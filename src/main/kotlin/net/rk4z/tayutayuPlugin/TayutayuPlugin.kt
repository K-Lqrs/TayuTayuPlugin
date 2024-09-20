package net.rk4z.tayutayuPlugin

import com.google.gson.JsonParser
import net.rk4z.tayutayuPlugin.utils.Util.copyResourceToFile
import net.rk4z.tayutayuPlugin.utils.Util.getNullableBoolean
import net.rk4z.tayutayuPlugin.utils.Util.getNullableString
import net.rk4z.tayutayuPlugin.discord.DiscordBotManager
import net.rk4z.tayutayuPlugin.listener.TTListener
import net.rk4z.tayutayuPlugin.utils.DataBase
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.io.path.notExists

class TayutayuPlugin : JavaPlugin() {
    companion object {
        lateinit var instance: TayutayuPlugin
            private set
    }

    val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    val configFile = Paths.get(dataFolder.absolutePath, "config.yml")
    val logger: Logger = LoggerFactory.getLogger(TayutayuPlugin::class.java.simpleName)
    var requiredFieldsNull: Boolean = false
    val advancementTranslations = mutableMapOf<String, String>()
    private val yaml = Yaml()
    val db = DataBase(this)

    var authChannelID: String? = null
    var authRoleID: String? = null
    var authedRoleID: String? = null
    var useLuna: Boolean? = false

    var botToken: String? = null
    var logChannelID: String? = null
    var enableConsoleLog: Boolean? = false
    var consoleLogChannelID: String? = null

    var serverStartMessage: String? = null
    var serverStopMessage: String? = null
    var playerJoinMessage: String? = null
    var playerLeaveMessage: String? = null

    var botOnlineStatus: String? = null
    var botActivityStatus: String? = null
    var botActivityMessage: String? = null

    var messageStyle: String? = null
    var webHookId: String? = null

    override fun onLoad() {
        instance = getPlugin(TayutayuPlugin::class.java)
        loadJapaneseTranslations()

        checkRequiredFilesAndDirectories()

        loadConfig()

        if (requiredNullCheck()) {
            logger.error("BotToken, もしくはLogChannelIDが設定されていません")
            logger.error("あなたはこのプラグインを始めて起動しましたか？")
            logger.error("$configFile の設定を確認してください。")
            requiredFieldsNull = true
            return
        }
        nullCheck()

        if (db.connectToDatabase()) {
            db.createRequiredTables()
        }
    }

    override fun onEnable() {
        if (requiredFieldsNull == false) {
            server.pluginManager.apply {
                registerEvents(TTListener(), this@TayutayuPlugin)
            }
            executorService.execute {
                try {
                    DiscordBotManager.init(server)
                    DiscordBotManager.start()
                } catch (e: Exception) {
                    logger.error("Discord Botの起動に失敗しました。", e)
                    server.shutdown()
                }
            }
        } else {
            logger.error("必要なフィールドが設定されていないため、起動できません")
        }
    }

    override fun onDisable() {
        DiscordBotManager.stop()
    }

    fun loadJapaneseTranslations() {
        val inputStream = this::class.java.classLoader.getResourceAsStream("advancements.json")
        if (inputStream != null) {
            val reader = InputStreamReader(inputStream)
            val json = JsonParser.parseReader(reader).asJsonObject
            json.entrySet().forEach { entry ->
                advancementTranslations[entry.key] = entry.value.asString
            }
        }
    }

    private fun checkRequiredFilesAndDirectories() {
        try {
            if (!Files.exists(dataFolder.toPath())) {
                logger.info("$dataFolder にデータフォルダを作成します。")
                Files.createDirectories(dataFolder.toPath())
            }
            if (configFile.notExists()) {
                copyResourceToFile("config.yml", configFile)
            }
        } catch (e: SecurityException) {
            logger.error("セキュリティエラーにより、ディレクトリ/ファイルの、チェック/作成に失敗しました。", e)
        } catch (e: IOException) {
            logger.error("I/O エラーにより、ディレクトリ/ファイルの、チェック/作成に失敗しました。", e)
        } catch (e: Exception) {
            logger.error("必要なファイルまたはディレクトリの作成/チェック中に予期しないエラーが発生しました。", e)
        }
    }

    private fun loadConfig() {
        try {
            logger.info("コンフィグファイルをロード中...")

            if (Files.notExists(configFile)) {
                logger.error("コンフィグファイルが $configFile に見つかりませんでした。")
                return
            }

            Files.newInputStream(configFile).use { inputStream ->
                val config: Map<String, Any> = yaml.load(inputStream)

                // TauTayu
                authChannelID = config.getNullableString("AuthChannelID")
                authRoleID = config.getNullableString("AuthRoleID")
                authedRoleID = config.getNullableString("AuthedRoleID")
                useLuna = config.getNullableBoolean("UseLuna")

                // Required
                botToken = config.getNullableString("BotToken")
                logChannelID = config.getNullableString("LogChannelID")

                // this feature is not supported in the current version
                enableConsoleLog = config.getNullableBoolean("EnableConsoleLog")
                consoleLogChannelID = config.getNullableString("ConsoleLogChannelID")

                // Optional
                serverStartMessage = config.getNullableString("ServerStartMessage")
                serverStopMessage = config.getNullableString("ServerStopMessage")
                playerJoinMessage = config.getNullableString("PlayerJoinMessage")
                playerLeaveMessage = config.getNullableString("PlayerLeaveMessage")

                botOnlineStatus = config.getNullableString("BotOnlineStatus")
                botActivityStatus = config.getNullableString("BotActivityStatus")
                botActivityMessage = config.getNullableString("BotActivityMessage")

                messageStyle = config.getNullableString("MessageStyle")
                webHookId = extractWebhookIdFromUrl(config.getNullableString("WebhookUrl"))
            }
        } catch (e: IOException) {
            logger.error("コンフィグファイルのロードに失敗しました", e)
        } catch (e: Exception) {
            logger.error("コンフィグのロード中に予期せぬエラーが発生しました：", e)
        }
    }

    private fun extractWebhookIdFromUrl(url: String?): String? {
        val regex = Regex("https://discord.com/api/webhooks/([0-9]+)/[a-zA-Z0-9_-]+")
        val matchResult = url?.let { regex.find(it) }
        return matchResult?.groupValues?.get(1)
    }

    private fun nullCheck() {
        if (botActivityMessage.isNullOrBlank()) {
            botActivityMessage = "Minecraft Server"
        }
        if (botActivityStatus.isNullOrBlank()) {
            botActivityStatus = "playing"
        }
        if (botOnlineStatus.isNullOrBlank()) {
            botOnlineStatus = "online"
        }
        if (messageStyle.isNullOrBlank()) {
            messageStyle = "classic"
        }
        if (serverStartMessage.isNullOrBlank()) {
            serverStartMessage = ":white_check_mark: **サーバーが起動しました！**"
        }
        if (serverStopMessage.isNullOrBlank()) {
            serverStopMessage = ":octagonal_sign: **サーバーが停止しました！**"
        }
    }

    private fun requiredNullCheck(): Boolean {
        return botToken.isNullOrBlank() || logChannelID.isNullOrBlank() || authChannelID.isNullOrBlank() || authRoleID.isNullOrBlank() || authedRoleID.isNullOrBlank()
    }
}
