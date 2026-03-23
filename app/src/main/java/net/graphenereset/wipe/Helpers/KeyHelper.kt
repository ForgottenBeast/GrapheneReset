package net.graphenereset.wipe.Helpers

class KeyHelper
{
    fun generateKey(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
        val random = java.util.Random()
        val parts = mutableListOf<String>()

        repeat(6) {
            val part = (1..6)
                .map { chars[random.nextInt(chars.length)] }
                .joinToString("")
            parts.add(part)
        }

        return parts.joinToString("-")
    }
}