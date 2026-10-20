package com.bentlybro.grabbit.core

/**
 * Everything a share/view intent might carry, flattened to plain strings so the "which link
 * did they mean" logic stays testable without Android. Priority: EXTRA_TEXT, then the
 * intent data URI (ACTION_VIEW), then ClipData items, then EXTRA_SUBJECT/title.
 */
data class SharedText(
    val text: String? = null,
    val dataUri: String? = null,
    val clipTexts: List<String> = emptyList(),
    val subject: String? = null,
    val title: String? = null,
) {
    fun url(): String? = UrlExtractor.extract(text, dataUri, *clipTexts.toTypedArray(), subject, title)
}
