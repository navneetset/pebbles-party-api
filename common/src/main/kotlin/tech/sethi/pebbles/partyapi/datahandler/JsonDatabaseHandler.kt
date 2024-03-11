package tech.sethi.pebbles.partyapi.datahandler

import com.google.gson.GsonBuilder
import tech.sethi.pebbles.partyapi.PartyAPI
import tech.sethi.pebbles.partyapi.dataclass.Party
import tech.sethi.pebbles.partyapi.dataclass.PartyChat
import tech.sethi.pebbles.partyapi.util.ConfigHandler
import tech.sethi.pebbles.partyapi.util.PM
import java.io.File

object JsonDatabaseHandler : DBInterface {
    val partyFolder = File("config/pebbles-partyapi/parties")
    val partyFiles = partyFolder.listFiles()

    val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    override var parties: MutableList<Party> = mutableListOf()

    init {
        // create folder if it doesn't exist
        if (!partyFolder.exists()) {
            partyFolder.mkdirs()
        }

        partyFiles?.forEach {
            val party = gson.fromJson(it.readText(), Party::class.java)
            parties.add(party)
        }

        deleteAllParties()
    }

    override fun createParty(party: Party): PartyResponse {
        // check if party with same name exists
        if (parties.any { it.name == party.name }) {
            return PartyResponse.ALREADY_PARTY
        }
        val partyFile = File(partyFolder, "${party.name}.json")
        partyFile.writeText(gson.toJson(party))
        parties.add(party)


        return PartyResponse.SUCCESS
    }

    override fun getParty(name: String): Party? {
        return parties.find { it.name == name }
    }

    override fun getPlayerParty(player: String): Party? {
        return parties.find { it.members.contains(player) }
    }

    override fun updateParty(party: Party): PartyResponse {
        val partyFile = File(partyFolder, "${party.name}.json")
        partyFile.writeText(gson.toJson(party))

        return PartyResponse.SUCCESS
    }

    override fun deleteParty(name: String): PartyResponse {
        val party = parties.find { it.name == name } ?: return PartyResponse.NOT_PARTY
        val partyFile = File(partyFolder, "${party.name}.json")
        partyFile.delete()
        (parties).remove(party)

        return PartyResponse.SUCCESS
    }


    override fun sendChat(chat: PartyChat): PartyResponse {
        val party = parties.find { it.name == chat.partyName } ?: return PartyResponse.NOT_PARTY
        val chatFormat = ConfigHandler.config.partyChatFormat.replace("{player_name}", chat.sender)
            .replace("{message}", chat.message)
        party.members.forEach {
            PartyAPI.server!!.playerManager.getPlayer(it)?.sendMessage(PM.returnStyledText(chatFormat), false)
        }
        return PartyResponse.SUCCESS
    }

    override fun deleteAllParties(): PartyResponse {
        partyFiles?.forEach {
            it.delete()
        }
        parties.clear()
        return PartyResponse.SUCCESS
    }
}