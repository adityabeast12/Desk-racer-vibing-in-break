package javax.crypto.spec

class SecretKeySpec(key: ByteArray, val algorithm: String) {
    val encoded: ByteArray = key.copyOf()
}
