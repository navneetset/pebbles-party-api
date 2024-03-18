package tech.sethi.pebbles.partyapi.datahandler

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.client.model.Filters
import kotlinx.coroutines.*
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.kotlin.DataClassCodecProvider
import tech.sethi.pebbles.partyapi.dataclass.Party
import tech.sethi.pebbles.partyapi.dataclass.PartyChat
import tech.sethi.pebbles.partyapi.dataclass.PartyPlayer
import tech.sethi.pebbles.partyapi.eventlistener.JoinPartyEvent
import tech.sethi.pebbles.partyapi.eventlistener.LeavePartyEvent
import tech.sethi.pebbles.partyapi.util.ConfigHandler
import java.util.concurrent.ConcurrentHashMap

object MongoDatabaseHandler : DBInterface {

    val config = ConfigHandler.config.mongoDbConfig

    val mongoClientSettings = MongoClientSettings.builder().codecRegistry(getCodecRegistry())
        .applyConnectionString(ConnectionString(config.uri)).build()
    val mongoClient = MongoClients.create(mongoClientSettings)
    val database = mongoClient.getDatabase(config.database)

    val partyCollection = database.getCollection(config.partyCollection, Party::class.java)

    override var parties: ConcurrentHashMap<String, Party> = ConcurrentHashMap()

    fun getCodecRegistry(): CodecRegistry {
        return CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(), fromProviders(DataClassCodecProvider())
        )
    }

    init {
        RedisHandler
    }

    override fun createParty(party: Party): PartyResponse {
        if (getPlayerParty(party.owner.uuid) != null) {
            return PartyResponse.fail("Player ${party.owner.name} is already in a party")
        }

        val partyExists = getParty(party.name) != null

        if (partyExists) {
            return PartyResponse.fail("Party with name ${party.name} already exists")
        }

        CoroutineScope(Dispatchers.IO).launch {
            val ack = partyCollection.insertOne(party).wasAcknowledged()
            if (ack) {
                RedisHandler.publish(RedisHandler.RedisMessage.partyCreateMessage(party.name))
            }
        }

        return PartyResponse.success("<green>Party <aqua>${party.name}</aqua> created")
    }

    override fun getParty(name: String): Party? = parties[name]

    fun getPartyFromDb(name: String): Party? {
        val party = partyCollection.find(Filters.eq("name", name)).first()
        return party
    }

    fun deletePartyFromDb(name: String) {
        partyCollection.deleteOne(Filters.eq("name", name))
    }

    override fun getPlayerParty(playerUuid: String): Party? = parties.values.find { it.isMember(playerUuid) }

    override fun invitePlayerToParty(partyPlayer: PartyPlayer, partyName: String): PartyResponse {
        val party = getParty(partyName) ?: return PartyResponse.fail("Party not found")
        if (party.isFull()) {
            return PartyResponse.fail("Party is full")
        }

        if (party.isInvited(partyPlayer.uuid)) {
            return PartyResponse.fail("Player is already invited")
        }

        party.addInvite(partyPlayer.uuid)

        CoroutineScope(Dispatchers.IO).launch {
            val updated = updateParty(party)
            if (updated.success) {
                RedisHandler.publish(RedisHandler.RedisMessage.partyInviteMessage(partyName, partyPlayer.uuid))
            }
        }

        return PartyResponse.success("<green>Player <aqua>${partyPlayer.name}</aqua> invited to party <aqua>$partyName</aqua>")
    }

    override fun addPlayerToParty(partyPlayer: PartyPlayer, partyName: String): PartyResponse {
        val party = getParty(partyName) ?: return PartyResponse.fail("Party <aqua>$partyName</aqua> does not exist")
        if (party.isFull()) {
            return PartyResponse.fail("Party <aqua>$partyName</aqua> is full")
        }
        if (party.isMember(partyPlayer.uuid)) {
            return PartyResponse.fail("Player <aqua>${partyPlayer.name}</aqua> is already in party <aqua>$partyName</aqua>")
        }

        // if not owner, check if player is invited
        if (!party.isOwner(partyPlayer.uuid)) {
            if (!party.isInvited(partyPlayer.uuid)) {
                return PartyResponse.fail("Player <aqua>${partyPlayer.name}</aqua> is not invited to party <aqua>$partyName</aqua>")
            }
        }

        party.addMember(partyPlayer)

        party.removeInvite(partyPlayer.uuid)

        updateParty(party)

        JoinPartyEvent.EVENT.invoker().onJoinParty(partyPlayer.uuid)

        return PartyResponse.success("<green>Player <aqua>${partyPlayer.name}</aqua> added to party <aqua>$partyName</aqua>")
    }


    override fun removePlayerFromParty(playerUuid: String, partyName: String): PartyResponse {
        val party = getParty(partyName) ?: return PartyResponse.fail("Party <aqua>$partyName</aqua> does not exist")
        if (!party.isMember(playerUuid)) {
            return PartyResponse.fail("Player <aqua>$playerUuid</aqua> is not in party <aqua>$partyName</aqua>")
        }
        if (party.isOwner(playerUuid)) {
            return PartyResponse.fail("<red>Cannot remove party owner from party. Use <yellow>/party disband</yellow> instead!</red>")
        }

        val partyPlayer = party.getMemberByUUID(playerUuid)
            ?: return PartyResponse.fail("Player <aqua>$playerUuid</aqua> not found in party <aqua>$partyName</aqua>")

        party.removeMember(playerUuid)

        updateParty(party)

        LeavePartyEvent.EVENT.invoker().onLeaveParty(playerUuid)

        return PartyResponse.success("<green>Player <aqua>${partyPlayer.name}</aqua> removed from party <aqua>$partyName</aqua>")
    }

    override fun updateParty(party: Party): PartyResponse {
        val filter = Filters.eq("name", party.name)
        val ack = partyCollection.replaceOne(filter, party).wasAcknowledged()

        return if (ack) PartyResponse.success("Party <aqua>${party.name}</aqua> updated") else PartyResponse.fail("Failed to update party <aqua>${party.name}</aqua>")
    }

    override fun deleteParty(name: String): PartyResponse {
        val party = getParty(name) ?: return PartyResponse.fail("Party <aqua>$name</aqua> does not exist")
        CoroutineScope(Dispatchers.IO).launch {
            val ack = partyCollection.deleteOne(Filters.eq("name", name)).wasAcknowledged()
            if (ack) {
                RedisHandler.publish(RedisHandler.RedisMessage.partyDeleteMessage(name))
            }
        }
        return PartyResponse.success("<green>Party <aqua>$name</aqua> deleted")
    }

    override fun sendChat(chat: PartyChat): PartyResponse {
        chat.message = ConfigHandler.config.partyChatFormat.replace("{player_name}", chat.sender)
            .replace("{message}", chat.message)
        RedisHandler.publish(RedisHandler.RedisMessage.partyChatMessage(chat))
        return PartyResponse.success("Chat sent")
    }

    override fun deleteAllParties(): PartyResponse {
        val ack = partyCollection.deleteMany(Filters.exists("name")).wasAcknowledged()
        return if (ack) PartyResponse.success("All parties deleted") else PartyResponse.fail("Failed to delete all parties")
    }

}