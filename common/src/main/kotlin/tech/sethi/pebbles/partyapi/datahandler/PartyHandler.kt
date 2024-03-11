package tech.sethi.pebbles.partyapi.datahandler

import tech.sethi.pebbles.partyapi.util.ConfigHandler

object PartyHandler {
    val db = when(ConfigHandler.config.datastore) {
        ConfigHandler.DatastoreType.JSON -> JsonDatabaseHandler
        ConfigHandler.DatastoreType.MONGODB -> MongoDatabaseHandler
    }
}