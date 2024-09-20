package net.rk4z.tayutayuPlugin.listener

import com.github.ucchyocean.lc3.LunaChat
import com.github.ucchyocean.lc3.japanize.JapanizeType
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.rk4z.tayutayuPlugin.TayutayuPlugin
import net.rk4z.tayutayuPlugin.discord.DiscordEmbed
import net.rk4z.tayutayuPlugin.discord.DiscordPlayerEventHandler
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class TTListener() : Listener {
    private val plugin = TayutayuPlugin.instance

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        if (plugin.db.isAuthorised(player.uniqueId) || player.isOp) {
            DiscordEmbed.sendPlayerJoinEmbed(player)
        } else {
            player.kickPlayer("あなたは認証されていません。\n Discordで認証を行うか、管理者に問い合わせてください")

            if (!plugin.db.hasPlayerConnected(player.uniqueId)) {
                plugin.db.insertConnectedPlayer(player.uniqueId.toString())
            }
        }
    }

    @EventHandler
    fun onPlayerLeft(event: PlayerQuitEvent) {
        if (plugin.db.isAuthorised(event.player.uniqueId) || event.player.isOp) {
            DiscordEmbed.sendPlayerLeftEmbed(event.player)
        }
    }

    @EventHandler
    fun onPlayerChat(event: AsyncChatEvent) {
        val player = event.player
        val plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message())
        var japanizedMessage: String? = null

        val containsHiraganaKatakanaOrKanji = plainMessage.any {
            it.toString().matches(Regex("[\\p{InHiragana}\\p{InKatakana}\\p{InCJKUnifiedIdeographs}]"))
        }

        val containsUpperCase = plainMessage.contains(Regex("[A-Z]"))
        val containsMentionFormat = plainMessage.contains(Regex("<@!|<@"))
        val containsRoleMentionFormat = plainMessage.contains(Regex("<@&"))

        if (!containsHiraganaKatakanaOrKanji && !containsUpperCase && containsMentionFormat && containsRoleMentionFormat) {
            val config = LunaChat.getConfig()
            val api = LunaChat.getAPI()

            if (api.isPlayerJapanize(player.name) && config.japanizeType != JapanizeType.NONE) {
                japanizedMessage = api.japanize(plainMessage, config.japanizeType)
            }
        }

        val format = if (japanizedMessage != null) {
            "$plainMessage ($japanizedMessage)"
        } else {
            plainMessage
        }

        DiscordPlayerEventHandler.handleMCMessage(player, format)
    }

    @EventHandler
    fun onPlayerGrantCriterion(event: PlayerAdvancementDoneEvent) {
        val advancement = event.advancement
        val display = advancement.display

        val shouldAnnounceToChat = display?.doesAnnounceToChat() ?: false

        val advancementName = display?.title()?.let { PlainTextComponentSerializer.plainText().serialize(it) }
            ?: advancement.key.toString()

        if (shouldAnnounceToChat) {
            DiscordEmbed.sendPlayerGrantCriterionEmbed(event.player, advancementName)
        }
    }

    @EventHandler
    fun onPlayerDied(event: PlayerDeathEvent) {
        val deathMessage = event.deathMessage() ?: return
        DiscordEmbed.sendPlayerDeathEmbed(event.entity, deathMessage)
    }
}