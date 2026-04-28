package dev.kpp.secret

internal fun constantTimeEquals(a: String, b: String): Boolean {
    // Reduce both to UTF-8 bytes and compare those constant-time.
    // Hash-derived comparison would be safer still, but bytewise xor over
    // the encoded form is sufficient for credential equality.
    val ab = a.encodeToByteArray()
    val bb = b.encodeToByteArray()
    return constantTimeEquals(ab, bb)
}

internal fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    // Iterate the longer of the two so we don't reveal the shorter length
    // via early exit; mismatched length still fails (the OR keeps result != 0).
    val len = maxOf(a.size, b.size)
    var result = a.size xor b.size
    var i = 0
    while (i < len) {
        val ai = if (i < a.size) a[i].toInt() and 0xff else 0
        val bi = if (i < b.size) b[i].toInt() and 0xff else 0
        result = result or (ai xor bi)
        i++
    }
    return result == 0
}
