package com.grondona.utils

import java.security.MessageDigest

private const val MD5 = "MD5"
private const val SHA256 = "SHA-256"

private fun hash(input: String, algorithm: String): String {
    val md = MessageDigest.getInstance(algorithm)
    val digest = md.digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

fun hashMD5(input: String): String {
    return hash(input, MD5)
}

fun hashSHA256(input: String): String {
    return hash(input, SHA256)
}
