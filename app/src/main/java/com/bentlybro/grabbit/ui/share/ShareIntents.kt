package com.bentlybro.grabbit.ui.share

import android.content.Intent
import com.bentlybro.grabbit.core.SharedText

/** Reads every place a link can hide in an incoming intent. */
fun Intent.toSharedText(): SharedText {
    val clips = buildList {
        clipData?.let { cd ->
            for (i in 0 until cd.itemCount) {
                val item = cd.getItemAt(i)
                item.text?.toString()?.let(::add)
                item.uri?.toString()?.let(::add)
            }
        }
    }
    return SharedText(
        text = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
        dataUri = dataString,
        clipTexts = clips,
        subject = getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
        title = getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString(),
    )
}
