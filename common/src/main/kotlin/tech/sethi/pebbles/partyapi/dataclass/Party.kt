package tech.sethi.pebbles.partyapi.dataclass

data class Party(
    val name: String, var owner: String, val members: MutableList<String>, val invites: MutableList<String>
)
