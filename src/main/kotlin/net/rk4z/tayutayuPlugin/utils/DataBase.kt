package net.rk4z.tayutayuPlugin.utils

import net.rk4z.tayutayuPlugin.TayutayuPlugin
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

@Suppress("SqlNoDataSourceInspection")
class DataBase(val plugin: TayutayuPlugin) {
    private var connection: Connection? = null

    fun connectToDatabase(): Boolean {
        val url = "jdbc:sqlite:${plugin.dataFolder.absolutePath}/database.db"

        try {
            connection = DriverManager.getConnection(url)
            plugin.logger.info("Successfully connected to the SQLite database!")
            return true
        } catch (e: SQLException) {
            plugin.logger.error("Could not connect to the SQLite database!")
            e.printStackTrace()
            return false
        }
    }

    fun createRequiredTables() {
        val authorisedPlayersTable = """
        CREATE TABLE IF NOT EXISTS authorisedPlayers (
            uuid TEXT PRIMARY KEY NOT NULL,
            mcid TEXT NOT NULL,
            authorised_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );
        """.trimIndent()

        val connectedPlayersTable = """
        CREATE TABLE IF NOT EXISTS connectedPlayers (
            uuid TEXT PRIMARY KEY NOT NULL,
            first_connected_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );
        """.trimIndent()

        try {
            connection?.createStatement()?.use { statement ->
                statement.execute(authorisedPlayersTable)
                statement.execute(connectedPlayersTable)
            }
        } catch (e: SQLException) {
            plugin.logger.error("Error creating authorisedPlayers table!")
            e.printStackTrace()
        }
    }

    fun insertAuthorisedPlayer(uuid: String, mcid: String) {
        val insertAuthorisedPlayer = """
        INSERT INTO authorisedPlayers (uuid, mcid)
        VALUES (?, ?);
        """.trimIndent()

        try {
            connection?.prepareStatement(insertAuthorisedPlayer)?.use { statement ->
                statement.setString(1, uuid)
                statement.setString(2, mcid)
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.error("Error inserting authorised player!")
            e.printStackTrace()
        }
    }

    fun insertConnectedPlayer(uuid: String) {
        val insertConnectedPlayer = """
        INSERT INTO connectedPlayers (uuid)
        VALUES (?);
    """.trimIndent()

        try {
            connection?.prepareStatement(insertConnectedPlayer)?.use { statement ->
                statement.setString(1, uuid)
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.error("Error inserting connected player!")
            e.printStackTrace()
        }
    }


    fun isAuthorised(uuid: UUID): Boolean {
        val isAuthorised = """
        SELECT COUNT(*) FROM authorisedPlayers
        WHERE uuid = ?;
        """.trimIndent()

        try {
            connection?.prepareStatement(isAuthorised)?.use { statement ->
                statement.setString(1, uuid.toString())
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getInt(1) == 1
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.error("Error checking if player is authorised!")
            e.printStackTrace()
        }

        return false
    }

    fun hasPlayerConnected(uuid: UUID): Boolean {
        val query = """
        SELECT COUNT(*) FROM connectedPlayers WHERE uuid = ?;
        """.trimIndent()

        try {
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, uuid.toString())
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getInt(1) == 1
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.error("Error checking if player has connected!")
            e.printStackTrace()
        }

        return false
    }
}
