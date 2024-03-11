package tech.sethi.pebbles.partyapi.datahandler

import tech.sethi.pebbles.partyapi.dataclass.Party
import tech.sethi.pebbles.partyapi.dataclass.PartyChat

interface DBInterface {
    fun createParty(party: Party): PartyResponse
    fun getParty(name: String): Party?
    fun getPlayerParty(player: String): Party?
    fun updateParty(party: Party): PartyResponse
    fun deleteParty(name: String): PartyResponse
    fun deleteAllParties(): PartyResponse

    fun sendChat(chat: PartyChat): PartyResponse

    var parties: MutableList<Party>
}