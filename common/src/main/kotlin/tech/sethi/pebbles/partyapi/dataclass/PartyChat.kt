package tech.sethi.pebbles.partyapi.dataclass

import java.util.*

data class PartyChat(
    val partyName: String, val sender: String, val message: String, val date: Date = Date()
)
