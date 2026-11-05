package com.riptide.util

fun sanitizeForDisplay(input: String): String {
    return input.replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "")
        .replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "")
}

fun basenameOnly(name: String): String {
    val clean = name.replace('\\', '/')
    return clean.substringAfterLast('/').ifBlank { name }
}
