package tech.sethi.pebbles.partyapi.datahandler

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.client.model.changestream.ChangeStreamDocument
import com.mongodb.client.model.changestream.OperationType
import kotlinx.coroutines.*
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.kotlin.DataClassCodecProvider
import tech.sethi.pebbles.partyapi.PartyAPI
import tech.sethi.pebbles.partyapi.dataclass.Party
import tech.sethi.pebbles.partyapi.dataclass.PartyChat
import tech.sethi.pebbles.partyapi.util.ConfigHandler
import tech.sethi.pebbles.partyapi.util.PM

object MongoDatabaseHandler : DBInterface {

    val config = ConfigHandler.config.mongoDbConfig

    val mongoClientSettings = MongoClientSettings.builder().codecRegistry(getCodecRegistry())
        .applyConnectionString(ConnectionString(config.uri)).build()
    val mongoClient = MongoClients.create(mongoClientSettings)
    val database = mongoClient.getDatabase(config.database)

    val partyCollection = database.getCollection(config.partyCollection, Party::class.java)
    val chatCollection = database.getCollection(config.chatCollection, PartyChat::class.java)


    fun getCodecRegistry(): CodecRegistry {
        return CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(), fromProviders(DataClassCodecProvider())
        )
    }

    init {
        watchChatChanges()
    }

    override var parties: MutableList<Party> = mutableListOf()

    override fun createParty(party: Party): PartyResponse {
        TODO("Not yet implemented")
    }

    override fun getParty(name: String): Party? {
        TODO("Not yet implemented")
    }

    override fun getPlayerParty(player: String): Party? {
        TODO("Not yet implemented")
    }

    override fun updateParty(party: Party): PartyResponse {
        TODO("Not yet implemented")
    }

    override fun deleteParty(name: String): PartyResponse {
        TODO("Not yet implemented")
    }

    override fun sendChat(chat: PartyChat): PartyResponse {
        val ack = chatCollection.insertOne(chat).wasAcknowledged()
        return if (ack) PartyResponse.SUCCESS else PartyResponse.FAIL
    }

    fun watchChatChanges() {
        CoroutineScope(Dispatchers.IO).launch {
            val changeStream = chatCollection.watch().iterator()
            try {
                while (changeStream.hasNext()) {
                    val change: ChangeStreamDocument<PartyChat> = changeStream.next()
                    handleChatChange(change)
                }
            } catch (e: Exception) {
                // Handle exceptions, e.g., lost connection
            } finally {
                changeStream.close()
            }
        }
    }

    private fun handleChatChange(change: ChangeStreamDocument<PartyChat>) {
        // Check for the type of change
        if (change.operationType == OperationType.INSERT || change.operationType == OperationType.UPDATE) {

            val chat: PartyChat = change.fullDocument ?: return

            val party = getParty(chat.partyName) ?: return

            // Send the chat to all members
            val chatFormat = ConfigHandler.config.partyChatFormat.replace("{player_name}", chat.sender)
                .replace("{message}", chat.message)
            party.members.forEach { member ->
                PartyAPI.server!!.playerManager.getPlayer(member)?.sendMessage(PM.returnStyledText(chatFormat), false)
            }
        }
    }

    private fun handlePartyChange(change: ChangeStreamDocument<Party>) {
        // Check for the type of change
        if (change.operationType == OperationType.INSERT || change.operationType == OperationType.UPDATE) {
            val party: Party = change.fullDocument ?: return
            // Do something with the party
        }
    }

    override fun deleteAllParties(): PartyResponse {
        TODO("Not yet implemented")
    }

}