package net.rk4z.tayutayuPlugin.discord

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.rk4z.tayutayuPlugin.TayutayuPlugin
import net.rk4z.tayutayuPlugin.utils.Util.replaceUUIDsWithMCIDs
import org.bukkit.Server
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.awt.Color
import java.util.concurrent.CompletableFuture

object DiscordMessageHandler {
    private val plugin = TayutayuPlugin.instance

    fun handleDiscordMessage(event: MessageReceivedEvent, server: Server) {
        plugin.executorService.submit {
            val message: Component = createMessage(event, false, null) ?: return@submit
            sendToAllPlayers(server, message)
        }
    }

    fun handleMentionedDiscordMessage(event: MessageReceivedEvent, server: Server, mentionedPlayers: List<Player>, foundUUID: Boolean) {
        plugin.executorService.submit {
            val updatedMessageContent = replaceUUIDsWithMCIDs(event.message.contentRaw, server.onlinePlayers)

            mentionedPlayers.forEach { player ->
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f)
            }

            val mentionMessage: Component =
                createMessage(event, true, if (foundUUID) updatedMessageContent.first else event.message.contentRaw) ?: return@submit
            val generalMessage: Component =
                createMessage(event, false, if (foundUUID) updatedMessageContent.first else event.message.contentRaw) ?: return@submit

            mentionedPlayers.forEach { player ->
                player.sendMessage(mentionMessage)
            }

            val nonMentionedPlayers = if (foundUUID) updatedMessageContent.second else mentionedPlayers
            sendToAllPlayersExcept(server, generalMessage, nonMentionedPlayers)
        }
    }

    private fun sendToAllPlayers(server: Server, message: Component) {
        server.onlinePlayers.forEach { player ->
            player.sendMessage(message)
        }
    }

    private fun sendToAllPlayersExcept(server: Server, message: Component, excludePlayers: List<Player>) {
        server.onlinePlayers.forEach { player ->
            if (player !in excludePlayers) {
                player.sendMessage(message)
            }
        }
    }

    private fun createMessage(event: MessageReceivedEvent, isMention: Boolean, updatedContent: String?): Component? {
        val channelId: String = plugin.logChannelID!!
        if (event.channel.id != channelId || event.author.isBot) {
            return null
        }

        val member = event.member
        val memberName = member?.user?.name ?: "Unknown Name"
        val memberId = member?.user?.id ?: "00000000000000000000"
        val idSuggest = "<@$memberId>"
        val highestRole = member?.roles?.maxByOrNull { it.position }
        val roleName = highestRole?.name
        val rIdSuggest = highestRole?.id?.let { "<@&$it>" }
        val roleColor = highestRole?.color ?: Color.WHITE
        val kyoriRoleColor = TextColor.color(roleColor.red, roleColor.green, roleColor.blue)

        var componentMessage = Component.text("[", TextColor.color(0xFFFFFF))
            .append(Component.text("Discord", TextColor.color(0x55CDFC)))

        componentMessage = if (roleName != null) {
            componentMessage.append(Component.text(" | ", TextColor.color(0xFFFFFF)))
                .append(
                    Component.text(roleName, kyoriRoleColor)
                        .clickEvent(ClickEvent.callback {
                            rIdClick(rIdSuggest!!, roleName)
                        })
                )
                .append(Component.text("]", TextColor.color(0xFFFFFF)))
                .append(Component.text(" "))
        } else {
            componentMessage.append(Component.text("]", TextColor.color(0xFFFFFF)))
                .append(Component.text(" "))
        }

        componentMessage = componentMessage.append(
            Component.text(memberName)
                .clickEvent(ClickEvent.callback {
                    idClick(idSuggest, memberName)
                })
        )

        val messageContent = if (isMention) {
            Component.text(" » " + (updatedContent ?: event.message.contentDisplay)).decorate(TextDecoration.BOLD)
        } else {
            Component.text(" » " + (updatedContent ?: event.message.contentDisplay))
        }

        componentMessage = componentMessage.append(messageContent)

        val json = GsonComponentSerializer.gson().serialize(componentMessage)
        return GsonComponentSerializer.gson().deserialize(json)
    }

    private fun idClick(message: String, name: String) {
        val formattedMessage = "@$name"

        plugin.server.onlinePlayers.forEach { player ->
            player.sendMessage(formattedMessage)
        }

        DiscordBotManager.sendToDiscord(message)
    }

    private fun rIdClick(message: String, roleName: String) {
        val formattedMessage = "@$roleName"

        plugin.server.onlinePlayers.forEach { player ->
            player.sendMessage(formattedMessage)
        }

        DiscordBotManager.sendToDiscord(message)
    }

}