package net.rk4z.tayutayuPlugin.discord

import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.kyori.adventure.text.Component
import net.minecraft.server.level.ServerPlayer
import net.rk4z.tayutayuPlugin.TayutayuPlugin
import org.bukkit.entity.Player

object DiscordPlayerEventHandler {
    val plugin = TayutayuPlugin.instance

    fun handleMCMessage(player: Player, message: String) {
        plugin.executorService.submit {
            when (plugin.messageStyle) {
                "modern" -> modernStyle(player, message)
                "classic" -> classicStyle(player, message)
                else -> classicStyle(player, message)
            }
        }
    }

    private fun classicStyle(player: Player, message: String) {
        val mcId = player.name
        val formattedMessage = "$mcId » $message"
        DiscordBotManager.sendToDiscord(formattedMessage)
    }

    private fun modernStyle(player: Player, message: String) {
        if (plugin.webHookId.isNullOrBlank()) {
            plugin.logger.error("Webhook URL is not configured or blank.")
            return
        }

        try {
            val webHookClient = DiscordBotManager.webHook

            val data = MessageCreateBuilder()
                .setContent(message.toString())
                .build()

            webHookClient!!.sendMessage(data)
                .setUsername(player.name)
                .setAvatarUrl("https://visage.surgeplay.com/face/256/${player.uniqueId}")
                .queue()

        } catch (e: Exception) {
            plugin.logger.error("An unexpected error occurred while sending message to Discord webhook: ${e.localizedMessage}", e)
        }
    }
}