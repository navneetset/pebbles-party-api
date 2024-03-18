package tech.sethi.pebbles.partyapi.dataclass

import net.minecraft.server.network.ServerPlayerEntity
import tech.sethi.pebbles.partyapi.PartyAPI
import tech.sethi.pebbles.partyapi.util.ConfigHandler

data class Party(
    val name: String,
    var owner: PartyPlayer,
    val members: MutableList<PartyPlayer>,
    val invites: MutableList<String> = mutableListOf()
) {

    fun addMember(partyPlayer: PartyPlayer): Boolean {
        return members.add(partyPlayer)
    }

    fun removeMember(playerUuid: String): Boolean {
        return members.removeIf { it.uuid == playerUuid }
    }

    fun addInvite(playerUuid: String): Boolean {
        return invites.add(playerUuid)
    }

    fun removeInvite(playerUuid: String) {
        invites.remove(playerUuid)
    }

    fun isMember(playerUuid: String): Boolean {
        return members.any { it.uuid == playerUuid }
    }

    fun isOwner(playerUuid: String): Boolean {
        return owner.uuid == playerUuid
    }

    fun isInvited(playerUuid: String): Boolean {
        return invites.contains(playerUuid)
    }

    fun getMemberByName(name: String): PartyPlayer? {
        return members.find { it.name == name }
    }

    fun getMemberByUUID(uuid: String): PartyPlayer? {
        return members.find { it.uuid == uuid }
    }

    fun isFull(): Boolean {
        return ConfigHandler.config.partySize <= members.size
    }

    fun getPlayerEntities(): List<ServerPlayerEntity> {
        val players = mutableListOf<ServerPlayerEntity>()
        members.forEach { player ->
            val serverPlayer = PartyAPI.server!!.playerManager.getPlayer(player.name)
            if (serverPlayer != null) {
                players.add(serverPlayer)
            }
        }
        return players
    }
}

data class PartyPlayer(val uuid: String, val name: String)
