package tech.sethi.pebbles.partyapi.datahandler

import tech.sethi.pebbles.partyapi.dataclass.Party
import tech.sethi.pebbles.partyapi.dataclass.PartyChat
import tech.sethi.pebbles.partyapi.dataclass.PartyPlayer
import java.util.concurrent.ConcurrentHashMap

interface DBInterface {
    fun createParty(party: Party): PartyResponse
    fun getParty(name: String): Party?
    fun getPlayerParty(playerUuid: String): Party?
    fun invitePlayerToParty(partyPlayer: PartyPlayer, partyName: String): PartyResponse
    fun addPlayerToParty(partyPlayer: PartyPlayer, partyName: String): PartyResponse
    fun removePlayerFromParty(playerUuid: String, partyName: String): PartyResponse
    fun updateParty(party: Party): PartyResponse
    fun deleteParty(name: String): PartyResponse
    fun deleteAllParties(): PartyResponse

    fun sendChat(chat: PartyChat): PartyResponse

    var parties: ConcurrentHashMap<String, Party>
}