package tech.sethi.pebbles.partyapi.dataclass

import java.util.*

data class PartyChat(
    val partyName: String, val sender: String, var message: String
)
