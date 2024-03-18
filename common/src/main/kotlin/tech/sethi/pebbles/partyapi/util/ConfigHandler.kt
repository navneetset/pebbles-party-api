package tech.sethi.pebbles.partyapi.util

import com.google.gson.GsonBuilder
import java.io.File

object ConfigHandler {
    val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    val configFile = File("config/pebbles-partyapi/config.json")

    private val configHandler = ConfigFileHandler(Config::class.java, configFile, gson)

    var config = Config()

    init {
        reload()
    }

    fun reload() {
        configHandler.reload()
        config = configHandler.config
    }


    enum class DatastoreType {
        JSON, MONGODB
    }

    data class Config(
        val partySize: Int = 4,
        val partyChatFormat: String = "<gold>[Party]</gold> <light_purple>{player_name}:</light_purple> <green>{message}",
        val datastore: DatastoreType = DatastoreType.JSON,
        val mongoDbConfig: MongoDbConfig = MongoDbConfig(),
    )

    data class MongoDbConfig(
        val uri: String = "mongodb://localhost:27017",
        val database: String = "pebbles_partyapi",
        val partyCollection: String = "Parties",
        val redisUrl: String = "redis://:password@localhost:6379",
    )
}