package tech.sethi.pebbles.partyapi.datahandler

import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.sethi.pebbles.partyapi.PartyAPI
import tech.sethi.pebbles.partyapi.dataclass.Party
import tech.sethi.pebbles.partyapi.dataclass.PartyChat
import tech.sethi.pebbles.partyapi.dataclass.PartyPlayer
import tech.sethi.pebbles.partyapi.eventlistener.JoinPartyEvent
import tech.sethi.pebbles.partyapi.eventlistener.LeavePartyEvent
import tech.sethi.pebbles.partyapi.util.ConfigHandler
import tech.sethi.pebbles.partyapi.util.PM
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object JsonDatabaseHandler : DBInterface {
    val partyFolder = File("config/pebbles-partyapi/parties")
    val partyFiles = partyFolder.listFiles()

    val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    override var parties: ConcurrentHashMap<String, Party> = ConcurrentHashMap()

    init {
        // create folder if it doesn't exist
        if (!partyFolder.exists()) {
            partyFolder.mkdirs()
        }

        partyFiles?.forEach {
            val party = gson.fromJson(it.readText(), Party::class.java)
            parties[party.name] = party
        }

        deleteAllParties()
    }

    override fun createParty(party: Party): PartyResponse {
        // check if player is already in a party
        if (getPlayerParty(party.owner.uuid) != null) {
            return PartyResponse.fail("Player <aqua>${party.owner.name}</aqua> is already in a party")
        }

        parties.forEach {
            if (it.key == party.name) {
                return PartyResponse.fail("Party with name <aqua>${party.name}</aqua> already exists")
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            val partyFile = File(partyFolder, "${party.name}.json")
            partyFile.writeText(gson.toJson(party))
        }

        parties[party.name] = party

        JoinPartyEvent.EVENT.invoker().onJoinParty(party.owner.uuid)

        return PartyResponse.success("<green>Party <aqua>${party.name}</aqua> created")
    }

    override fun getParty(name: String): Party? = parties[name]

    override fun getPlayerParty(playerUuid: String): Party? = parties.values.find { it.isMember(playerUuid) }

    override fun invitePlayerToParty(partyPlayer: PartyPlayer, partyName: String): PartyResponse {
        val party = getParty(partyName) ?: return PartyResponse.fail("Party <aqua>$partyName</aqua> does not exist")
        if (party.isFull()) {
            return PartyResponse.fail("Party <aqua>$partyName</aqua> is full")
        }
        if (party.isMember(partyPlayer.uuid)) {
            return PartyResponse.fail("Player <aqua>${partyPlayer.name}</aqua> is already in party <aqua>$partyName</aqua>")
        }
        if (party.isInvited(partyPlayer.uuid)) {
            return PartyResponse.fail("Player <aqua>${partyPlayer.name}</aqua> is already invited to party <aqua>$partyName</aqua>")
        }

        party.addInvite(partyPlayer.uuid)

        updateParty(party)

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
        val partyFile = File(partyFolder, "${party.name}.json")
        CoroutineScope(Dispatchers.IO).launch {
            partyFile.writeText(gson.toJson(party))
        }

        return PartyResponse.success("<green>Party <aqua>${party.name}</aqua> updated")
    }

    override fun deleteParty(name: String): PartyResponse {
        val party = getParty(name) ?: return PartyResponse.fail("Party <aqua>$name</aqua> does not exist")

        CoroutineScope(Dispatchers.IO).launch {
            val partyFile = File(partyFolder, "$name.json")
            partyFile.delete()
        }

        parties.remove(name)

        return PartyResponse.success("<green>Party <aqua>$name</aqua> deleted")
    }

    override fun deleteAllParties(): PartyResponse {
        partyFiles?.forEach {
            it.delete()
        }

        parties.clear()

        return PartyResponse.success("<green>All parties deleted")
    }

    override fun sendChat(chat: PartyChat): PartyResponse {
        val party =
            getParty(chat.partyName) ?: return PartyResponse.fail("Party <aqua>${chat.partyName}</aqua> does not exist")

        val chatFormat = ConfigHandler.config.partyChatFormat.replace("{player_name}", chat.sender)
            .replace("{message}", chat.message)

        party.members.forEach {
            PartyAPI.server!!.playerManager.getPlayer(it.name)?.sendMessage(PM.returnStyledText(chatFormat), false)
        }

        return PartyResponse.success("<green>Chat sent")
    }
}