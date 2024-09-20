package net.rk4z.tayutayuPlugin.utils

import net.rk4z.tayutayuPlugin.TayutayuPlugin
import org.bukkit.entity.Player
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

object Util {
    private val plugin = TayutayuPlugin.Companion.instance

    fun Map<String, Any>.getNullableString(key: String): String? =
        this[key]?.toString()?.takeIf { it.isNotBlank() }

    fun Map<String, Any>.getNullableBoolean(key: String): Boolean? =
        this[key]?.toString()?.takeIf { it.isNotBlank() }?.toBooleanStrictOrNull()

    fun copyResourceToFile(resourcePath: String, outputPath: Path) {
        val fullPath = "/$resourcePath"
        val inputStream: InputStream? = javaClass.getResourceAsStream(fullPath)
        if (inputStream == null) {
            plugin.logger.error("Resource $fullPath not found in Jar")
            return
        }
        Files.copy(inputStream, outputPath)
        plugin.logger.info("Copied resource $fullPath to $outputPath")
    }


    fun replaceUUIDsWithMCIDs(message: String, players: Collection<Player>): Pair<String, List<Player>> {
        var updatedMessage = message
        val mentionedPlayers = mutableListOf<Player>()
        val uuidPattern = Regex("@\\{([0-9a-fA-F-]+)}")
        val matches = uuidPattern.findAll(message)

        matches.forEach { match ->
            val uuidStr = match.groupValues[1]
            val player = players.find { it.uniqueId.toString() == uuidStr }
            player?.let {
                updatedMessage = updatedMessage.replace(match.value, "@${it.name}")
                mentionedPlayers.add(it)
            }
        }
        return Pair(updatedMessage, mentionedPlayers)
    }

}