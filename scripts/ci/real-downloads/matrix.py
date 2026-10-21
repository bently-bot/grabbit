#!/usr/bin/env python3
"""Real-download test matrix for Grabbit.

  matrix.py host --ytdlp FILE --label NAME [--qjs FILE] --out results.json
      Download every URL in urls.tsv with a given yt-dlp build on this machine, using the
      same options the app uses. Separates "engine/site broken" from "app broken".

  matrix.py probe --platform P --file FILE [--mediastore-size N] ... (used by emulator.sh)

  matrix.py summary results*.json
      Markdown table for $GITHUB_STEP_SUMMARY; exit 1 when the app failed where the same
      engine worked on the host, or when the plain mp4 failed anywhere.

Outcomes:  PASS     file saved, non-empty, ffprobe finds a valid video with a duration
           BLOCKED  the site refused this network (bot check, IP block, login wall, 429).
                    Typical for datacenter runners; says nothing about the app.
           FAIL     anything else.
No outcome is ever upgraded: a BLOCKED is reported as BLOCKED, never as a pass.
"""
import argparse
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))

# Keep in sync with core/Failures.kt (FailureClassifier). Order matters.
RULES = [
    ("bot-check", r"(?i)confirm you.?re not a bot|sign in to confirm"),
    ("rate-limited", r"(?i)http error 429|too many requests|rate.?limit"),
    ("ip-blocked", r"(?i)ip address is blocked|blocked from accessing|your ip"),
    ("login-required", r"(?i)login required|requires authentication|account authentication is required|"
                       r"only works when logged.?in|log in to|use --cookies|age.?restricted"),
    ("http-403", r"(?i)http error 403|403: forbidden"),
    ("unavailable", r"(?i)private video|video unavailable|this video is unavailable|has been removed|"
                    r"no longer available|no video could be found|does not exist|not found"),
    ("unsupported", r"(?i)unsupported url"),
    ("network", r"(?i)unable to download webpage|failed to resolve|network is unreachable|timed out|connection reset"),
]
BLOCKED = {"bot-check", "rate-limited", "ip-blocked", "login-required"}

# Same as FormatSelector.args(P480) + FormatSelector.common(); 480p keeps CI quick.
APP_ARGS = [
    "--no-playlist", "--no-mtime", "--newline",
    "--retries", "5", "--fragment-retries", "5", "--extractor-retries", "3",
    "-f", "bv*[height<=480]+ba/b[height<=480]/bv*+ba/b",
    "-S", "res:480,vcodec:h264,ext:mp4:m4a",
    "--merge-output-format", "mp4", "--remux-video", "mp4",
    "--no-update",
]


def classify(err):
    if not err:
        return "other"
    for kind, rx in RULES:
        if re.search(rx, err):
            return kind
    return "other"


def last_error(text):
    lines = [l.strip() for l in (text or "").splitlines() if l.strip()]
    errs = [l for l in lines if l.startswith("ERROR:")]
    return (errs[-1] if errs else (lines[-1] if lines else ""))[:300]


def urls():
    out = []
    with open(os.path.join(HERE, "urls.tsv")) as f:
        for line in f:
            if not line.strip() or line.startswith("#"):
                continue
            parts = line.rstrip("\n").split("\t")
            out.append({"platform": parts[0], "url": parts[1], "notes": parts[2] if len(parts) > 2 else ""})
    return out


def ffprobe(path):
    """(ok, description). ok = parseable container, duration > 0, has a video or audio stream."""
    if not path or not os.path.exists(path) or os.path.getsize(path) == 0:
        return False, "no file / empty file"
    try:
        p = subprocess.run(["ffprobe", "-v", "error", "-print_format", "json", "-show_format", "-show_streams", path],
                           capture_output=True, text=True, timeout=60)
    except Exception as e:  # noqa: BLE001
        return False, f"ffprobe crashed: {e}"
    if p.returncode != 0:
        return False, "ffprobe: " + (p.stderr.strip().splitlines() or ["invalid"])[-1][:200]
    data = json.loads(p.stdout or "{}")
    fmt = data.get("format", {})
    streams = data.get("streams", [])
    v = [s for s in streams if s.get("codec_type") == "video" and s.get("disposition", {}).get("attached_pic", 0) == 0]
    a = [s for s in streams if s.get("codec_type") == "audio"]
    dur = float(fmt.get("duration") or 0)
    if dur <= 0 or not (v or a):
        return False, f"ffprobe: no playable streams (duration {dur})"
    desc = fmt.get("format_name", "?").split(",")[0]
    if v:
        desc += f" {v[0].get('codec_name')} {v[0].get('width')}x{v[0].get('height')}"
    if a:
        desc += f" + {a[0].get('codec_name')}"
    return True, f"{desc}, {dur:.1f}s"


def result(platform, url, outcome, detail, **extra):
    r = {"platform": platform, "url": url, "outcome": outcome, "detail": detail}
    r.update(extra)
    return r


def cmd_host(a):
    version = subprocess.run([sys.executable, a.ytdlp, "--version"], capture_output=True, text=True).stdout.strip()
    rows = []
    for u in urls():
        tmp = tempfile.mkdtemp(prefix="grabbit-")
        args = [sys.executable, a.ytdlp, *APP_ARGS, "--cache-dir", os.path.join(tmp, "cache"),
                "-o", os.path.join(tmp, "%(title).80B [%(id)s].%(ext)s")]
        if a.qjs:
            args += ["--js-runtimes", f"quickjs:{a.qjs}"]
        t0 = time.time()
        try:
            p = subprocess.run(args + [u["url"]], capture_output=True, text=True, timeout=a.timeout)
            out, code = p.stdout + p.stderr, p.returncode
        except subprocess.TimeoutExpired:
            out, code = "ERROR: timed out after %ss" % a.timeout, -1
        secs = round(time.time() - t0)
        files = [f for f in glob.glob(os.path.join(tmp, "*")) if os.path.isfile(f)
                 and not re.search(r"\.(part|ytdl|temp|tmp)$", f)]
        f = max(files, key=os.path.getsize) if files else None
        if code == 0 and f:
            ok, desc = ffprobe(f)
            rows.append(result(u["platform"], u["url"], "PASS" if ok else "FAIL", desc,
                               bytes=os.path.getsize(f), seconds=secs))
        else:
            err = last_error(out)
            kind = classify(err)
            rows.append(result(u["platform"], u["url"], "BLOCKED" if kind in BLOCKED else "FAIL",
                               err, kind=kind, seconds=secs))
        print(f"[{a.label}] {u['platform']}: {rows[-1]['outcome']} - {rows[-1]['detail']}", flush=True)
        shutil.rmtree(tmp, ignore_errors=True)
    json.dump({"source": f"host-{a.label}", "engine": version, "results": rows}, open(a.out, "w"), indent=2)


def cmd_probe(a):
    ok, desc = ffprobe(a.file)
    print(json.dumps({"ok": ok, "desc": desc}))


def cmd_summary(a):
    docs = [json.load(open(f)) for f in a.files if os.path.exists(f)]
    order = {"emulator": 0, "host-latest": 1, "host-bundled": 2}
    docs.sort(key=lambda d: order.get(d["source"], 9))
    by = {d["source"]: {r["platform"]: r for r in d["results"]} for d in docs}
    titles = {"emulator": "App on emulator", "host-latest": "Host, same yt-dlp", "host-bundled": "Host, yt-dlp bundled in APK"}
    icon = {"PASS": "✅ pass", "BLOCKED": "🚫 blocked", "FAIL": "❌ fail", None: "—"}

    lines = ["## Real downloads", ""]
    for d in docs:
        lines.append(f"- **{titles.get(d['source'], d['source'])}**: yt-dlp `{d.get('engine') or '?'}`")
    lines += ["", "| Platform | " + " | ".join(titles.get(d["source"], d["source"]) for d in docs) + " | Detail (app) |",
              "|---|" + "---|" * len(docs) + "---|"]
    platforms = [u["platform"] for u in urls()]
    problems = []
    for p in platforms:
        cells = []
        for d in docs:
            r = by[d["source"]].get(p)
            o = r["outcome"] if r else None
            cell = icon[o]
            if r and r.get("kind") and o != "PASS":
                cell += f" ({r['kind']})"
            cells.append(cell)
        main = by.get("emulator", {}).get(p) or by.get("host-latest", {}).get(p) or {}
        detail = str(main.get("detail", "")).replace("|", "\\|")[:140]
        lines.append(f"| {p} | " + " | ".join(cells) + f" | {detail} |")

        app = by.get("emulator", {}).get(p)
        host = by.get("host-latest", {}).get(p)
        if p.startswith("direct"):
            for src in ("emulator", "host-latest"):
                r = by.get(src, {}).get(p)
                if r and r["outcome"] != "PASS":
                    problems.append(f"{p}: {src} could not save a plain mp4 ({r['detail']})")
        elif app and host and host["outcome"] == "PASS" and app["outcome"] == "FAIL":
            problems.append(f"{p}: app failed where the same engine works on the host -> app bug ({app['detail']})")
    if "emulator" not in by:
        problems.append("no emulator results (job crashed?)")
    lines += ["", "✅ pass = file saved, non-empty, valid per ffprobe. 🚫 blocked = the site refused this "
              "datacenter IP (bot check / IP block / login wall / rate limit); not an app result. "
              "❌ fail = anything else."]
    if problems:
        lines += ["", "### Problems", *[f"- {x}" for x in problems]]
    text = "\n".join(lines) + "\n"
    print(text)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write(text)
    if a.out:
        open(a.out, "w").write(text)
    sys.exit(1 if problems else 0)


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    h = sub.add_parser("host")
    h.add_argument("--ytdlp", required=True)
    h.add_argument("--qjs")
    h.add_argument("--label", required=True)
    h.add_argument("--out", required=True)
    h.add_argument("--timeout", type=int, default=240)
    p = sub.add_parser("probe")
    p.add_argument("--file", required=True)
    s = sub.add_parser("summary")
    s.add_argument("files", nargs="+")
    s.add_argument("--out")
    a = ap.parse_args()
    {"host": cmd_host, "probe": cmd_probe, "summary": cmd_summary}[a.cmd](a)


if __name__ == "__main__":
    main()
