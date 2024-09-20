package net.rk4z.tayutayuPlugin.discord

import net.dv8tion.jda.api.EmbedBuilder
import net.kyori.adventure.text.Component
import net.minecraft.server.level.ServerPlayer
import net.rk4z.tayutayuPlugin.TayutayuPlugin
import org.bukkit.entity.Player
import java.awt.Color

object DiscordEmbed {
    private val plugin = TayutayuPlugin.Companion.instance

    private fun sendEmbedToDiscord(color: Color, author: String? = null, imageUrl: String, channelId: String = plugin.logChannelID!!) {
        if (channelId.isBlank()) {
            plugin.logger.error("Channel ID is blank.")
            return
        }

        val embed = EmbedBuilder().apply {
            setColor(color)
            setAuthor(author, null, imageUrl)
        }.build()

        DiscordBotManager.jda?.getTextChannelById(channelId)?.sendMessageEmbeds(embed)?.queue()
    }

    fun sendPlayerJoinEmbed(player: Player) {
        val name = player.name
        val uuid = player.uniqueId.toString()
        val imageUrl = "https://visage.surgeplay.com/face/256/$uuid"
        sendEmbedToDiscord(Color.GREEN, "$name joined the server", imageUrl)
    }

    fun sendPlayerLeftEmbed(player: Player) {
        val name = player.name
        val uuid = player.uniqueId.toString()
        val imageUrl = "https://visage.surgeplay.com/face/256/$uuid"
        sendEmbedToDiscord(Color.RED, "$name left the server", imageUrl)
    }

    fun sendPlayerDeathEmbed(player: Player, deathMessage: Component) {
        val uuid = player.uniqueId.toString()
        val imageUrl = "https://visage.surgeplay.com/face/256/$uuid"
        sendEmbedToDiscord(Color.BLACK, deathMessage.toString(), imageUrl)
    }

    fun sendPlayerGrantCriterionEmbed(player: Player, criterion: String) {
        val name = player.name
        val uuid = player.uniqueId.toString()
        val imageUrl = "https://visage.surgeplay.com/face/256/$uuid"
        sendEmbedToDiscord(Color.YELLOW, "$name has made the advancement $criterion", imageUrl)
    }
}