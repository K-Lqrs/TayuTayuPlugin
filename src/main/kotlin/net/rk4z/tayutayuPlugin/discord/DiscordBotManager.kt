package net.rk4z.tayutayuPlugin.discord

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import net.rk4z.tayutayuPlugin.TayutayuPlugin
import org.bukkit.Server
import org.bukkit.entity.Player
import java.awt.Color
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.security.auth.login.LoginException

object DiscordBotManager : ListenerAdapter() {
    var jda: JDA? = null
    val plugin = TayutayuPlugin.instance
    private val intents = GatewayIntent.MESSAGE_CONTENT
    var botIsInitialized: Boolean = false
    var webHook: Webhook? = null
    private var server: Server? = null
    var guildId: String? = null

    fun init(s: Server) {
        server = s
    }

    fun start() {
        val onlineStatus = getOnlineStatus()
        val activity = getBotActivity()

        try {
            jda = JDABuilder.createDefault(plugin.botToken)
                .setAutoReconnect(true)
                .setStatus(onlineStatus)
                .setActivity(activity)
                .enableIntents(intents)
                .addEventListeners(discordListener, this)
                .setAutoReconnect(true)
                .build()
                .awaitReady()

            guildId = jda?.guilds?.firstOrNull()?.id

            jda?.updateCommands()?.addCommands(
                Commands.slash("playerlist", "Get a list of online players")
            )?.queue()

            jda?.updateCommands()

            botIsInitialized = true
            plugin.logger.info("Discord bot is now online")
            plugin.serverStartMessage?.let { sendToDiscord(it) }

            if (plugin.messageStyle == "modern") {
                if (!plugin.webHookId.isNullOrBlank()) {
                    webHook = jda?.retrieveWebhookById(plugin.webHookId!!)?.complete()
                } else {
                    plugin.logger.error("The message style is set to 'modern' but the webhook URL is not configured.")
                }
            }
        } catch (e: LoginException) {
            plugin.logger.error("Failed to login to Discord with the provided token", e)
        } catch (e: Exception) {
            plugin.logger.error("An unexpected error occurred during Discord bot startup", e)
        }
    }

    fun stop() {
        if (botIsInitialized) {
            jda?.shutdown()
            plugin.serverStopMessage?.let { sendToDiscord(it) }
            plugin.logger.info("Discord bot is now offline")
            botIsInitialized = false
        } else {
            plugin.logger.error("Discord bot is not initialized. Cannot stop the bot.")
        }
    }

    private fun getOnlineStatus(): OnlineStatus {
        return when (plugin.botOnlineStatus?.uppercase(Locale.getDefault())) {
            "ONLINE" -> OnlineStatus.ONLINE
            "IDLE" -> OnlineStatus.IDLE
            "DO_NOT_DISTURB" -> OnlineStatus.DO_NOT_DISTURB
            "INVISIBLE" -> OnlineStatus.INVISIBLE
            else -> OnlineStatus.ONLINE
        }
    }

    private fun getBotActivity(): Activity {
        return when (plugin.botActivityStatus?.lowercase(Locale.getDefault())) {
            "playing" -> Activity.playing(plugin.botActivityMessage ?: "Minecraft Server")
            "watching" -> Activity.watching(plugin.botActivityMessage ?: "Minecraft Server")
            "listening" -> Activity.listening(plugin.botActivityMessage ?: "Minecraft Server")
            "competing" -> Activity.competing(plugin.botActivityMessage ?: "Minecraft Server")
            else -> Activity.playing("Minecraft Server")
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        when (event.name) {
            "playerlist" -> handlePlayerListCommand(event)
        }
    }

    private val discordListener = object : ListenerAdapter() {
        override fun onMessageReceived(event: MessageReceivedEvent) {
            if (event.author.isBot) return
            val server = server ?: return plugin.logger.error("Server is not initialized. Cannot process Discord message.")
            if (event.channel.id != plugin.logChannelID) {
                if (event.channel.id == plugin.authChannelID) {
                    handleMCIDMessage(event)
                }
                return
            }

            plugin.executorService.submit {
                val mentionedPlayers = findMentionedPlayers(event.message.contentRaw, server.onlinePlayers)
                if (mentionedPlayers.isNotEmpty()) {
                    DiscordMessageHandler.handleMentionedDiscordMessage(event, server, mentionedPlayers, false)
                } else {
                    DiscordMessageHandler.handleDiscordMessage(event, server)
                }
            }
        }

        fun handleMCIDMessage(event: MessageReceivedEvent) {
            val player = server?.onlinePlayers?.find { it.name == event.message.contentRaw }
                ?: server?.offlinePlayers?.find { it.name == event.message.contentRaw }
                ?: run {
                    event.message.reply("そのプレイヤーはオンラインまたはオフラインで見つかりませんでした。\n一度サーバーに接続してから、再度お試しください")
                        .queue {
                            plugin.executorService.schedule({
                                it.delete().queue()
                            }, 5, TimeUnit.SECONDS)
                        }
                    return
                }

            event.guild.retrieveMember(event.author).queue(
                { member ->
                    // 認証ロールのIDを確認
                    val authRoleID = plugin.authRoleID ?: run {
                        plugin.logger.error("Auth role ID is not set")
                        return@queue
                    }

                    // ユーザーが認証ロールを持っているかチェック
                    val hasRole = member.roles.any { role -> role.id == authRoleID }

                    if (plugin.db.isAuthorised(player.uniqueId)) {
                        // プレイヤーが既に認証されている場合
                        event.message.reply("そのプレイヤーは既に認証されています。").queue {
                            plugin.executorService.schedule({
                                it.delete().queue()
                            }, 5, TimeUnit.SECONDS)
                        }
                    } else if (hasRole) {
                        // 認証プロセス
                        plugin.db.insertAuthorisedPlayer(player.uniqueId.toString(), player.name.toString())
                        event.message.reply("プレイヤー ${player.name} が認証されました。").queue {
                            val authedRoleID = plugin.authedRoleID ?: run {
                                plugin.logger.error("Authed role ID is not set")
                                return@queue
                            }

                            // 認証済みのロールを付与
                            event.guild.addRoleToMember(member, event.guild.getRoleById(authedRoleID) ?: return@queue).queue()

                            // 参加申請のロールを削除
                            event.guild.removeRoleFromMember(member, event.guild.getRoleById(authRoleID) ?: return@queue).queue()

                            // メッセージを5秒後に削除
                            plugin.executorService.schedule({
                                it.delete().queue()
                            }, 5, TimeUnit.SECONDS)
                        }
                    } else {
                        // ロールがない場合のエラーメッセージ
                        event.message.reply("参加申請をしていることを確認できませんでした。\n参加申請をしてもう一度お試しください").queue {
                            plugin.executorService.schedule({
                                it.delete().queue()
                            }, 5, TimeUnit.SECONDS)
                        }
                    }
                },
                { error ->
                    // メンバーの取得に失敗した場合
                    plugin.logger.error("Failed to retrieve member: ${error.message}")
                    event.message.reply("メンバーが見つかりませんでした。").queue {
                        plugin.executorService.schedule({
                            it.delete().queue()
                        }, 5, TimeUnit.SECONDS)
                    }
                }
            )
        }
    }

    private fun handlePlayerListCommand(event: SlashCommandInteractionEvent) {
        val server = server ?: run {
            plugin.logger.error("MinecraftServer is not initialized. Cannot process /playerlist command.")
            event.reply("プレイヤーリストを取得できませんでした。")
                .setEphemeral(true)
                .queue {
                    plugin.executorService.schedule({
                        it.deleteOriginal().queue()
                    }, 5, TimeUnit.SECONDS)
                }
            return
        }

        val onlinePlayers = server.onlinePlayers
        val playerCount = onlinePlayers.size

        val embedBuilder = EmbedBuilder()
            .setTitle("Online Players")
            .setColor(Color.GREEN)
            .setDescription("There are currently $playerCount players online.\n")

        if (playerCount > 0) {
            val playerList = onlinePlayers.joinToString(separator = "\n") { player -> player.name }
            embedBuilder.setDescription(embedBuilder.descriptionBuilder.append(playerList).toString())
        } else {
            embedBuilder.setDescription("There are currently no players online.")
        }

        event.replyEmbeds(embedBuilder.build()).queue()
    }

    private fun findMentionedPlayers(messageContent: String, players: Collection<Player>): List<Player> {
        val mentionedPlayers = mutableListOf<Player>()
        val mcidPattern = Regex("@([a-zA-Z0-9_]+)")
        val uuidPattern = Regex("@\\{([0-9a-fA-F-]+)}")

        mcidPattern.findAll(messageContent).forEach { match ->
            val mcid = match.groupValues[1]
            players.find { it.name == mcid }?.let { mentionedPlayers.add(it) }
        }

        uuidPattern.findAll(messageContent).forEach { match ->
            val uuidStr = match.groupValues[1]
            players.find { it.uniqueId.toString() == uuidStr }?.let { mentionedPlayers.add(it) }
        }

        return mentionedPlayers
    }

    fun sendToDiscord(message: String) {
        plugin.executorService.submit {
            plugin.logChannelID?.let { jda?.getTextChannelById(it)?.sendMessage(message)?.queue() }
        }
    }
}