package net.rk4z.tayutayuPlugin.utils

enum class MessageKey {
    ;
    companion object {
        fun fromString(name: String): MessageKey? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}