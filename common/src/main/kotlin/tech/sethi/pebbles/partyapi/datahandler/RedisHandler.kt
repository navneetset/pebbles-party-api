package tech.sethi.pebbles.partyapi.datahandler

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.exceptions.JedisConnectionException
import tech.sethi.pebbles.partyapi.PartyAPI
import tech.sethi.pebbles.partyapi.dataclass.PartyChat
import tech.sethi.pebbles.partyapi.util.ConfigHandler
import tech.sethi.pebbles.partyapi.util.PM

object RedisHandler {
    var jedisPool: JedisPool? = null
    var jedisSubscriber: Jedis? = null

    val gson = Gson()

    init {
        try {
            jedisPool = JedisPool(ConfigHandler.config.mongoDbConfig.redisUrl)
            jedisSubscriber = jedisPool?.resource

            CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    try {
                        subscribe()
                    } catch (e: JedisConnectionException) {
                        PartyAPI.LOGGER.error("Party-API Redis connection lost")
                        throw e
                    }
                    delay(1000)
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to connect to Redis", e)
        }
    }

    val jedisPubSub = object : JedisPubSub() {
        override fun onMessage(channel: String, message: String) {
            if (channel == "party-api") {
                val redisMessage = gson.fromJson(message, RedisMessage::class.java)
                when (redisMessage.type) {
                    RedisMessageType.PARTY_CHAT -> {
                        val partyChatMessage = gson.fromJson(redisMessage.json, PartyChatMessage::class.java)
                        MongoDatabaseHandler.parties[partyChatMessage.partyName]?.let { party ->
                            party.getPlayerEntities().forEach { player ->
                                PM.sendText(player, partyChatMessage.message)
                            }
                        }
                    }

                    RedisMessageType.PARTY_CREATE -> {
                        val partyCreateMessage = gson.fromJson(redisMessage.json, PartyCreateMessage::class.java)
                        MongoDatabaseHandler.getPartyFromDb(partyCreateMessage.partyName)?.let {
                            MongoDatabaseHandler.parties[partyCreateMessage.partyName] = it
                        }
                    }

                    RedisMessageType.PARTY_DELETE -> {
                        val partyCreateMessage = gson.fromJson(redisMessage.json, PartyCreateMessage::class.java)
                        MongoDatabaseHandler.parties.remove(partyCreateMessage.partyName)
                        MongoDatabaseHandler.deletePartyFromDb(partyCreateMessage.partyName)
                    }

                    RedisMessageType.PARTY_JOIN -> {
                        val partyJoinMessage = gson.fromJson(redisMessage.json, PartyJoinMessage::class.java)
                        MongoDatabaseHandler.parties[partyJoinMessage.partyName]?.let { party ->
                            party.getPlayerEntities().forEach { player ->
                                PM.sendText(player, "<aqua>${partyJoinMessage.player}</aqua> joined the party")
                            }
                        }
                    }

                    RedisMessageType.PARTY_LEAVE -> {
                        val partyLeaveMessage = gson.fromJson(redisMessage.json, PartyLeaveMessage::class.java)
                        MongoDatabaseHandler.parties[partyLeaveMessage.partyName]?.let { party ->
                            party.getPlayerEntities().forEach { player ->
                                PM.sendText(player, "<aqua>${partyLeaveMessage.player}</aqua> left the party")
                            }
                        }
                    }

                    RedisMessageType.PARTY_INVITE -> {
                        val partyInviteMessage = gson.fromJson(redisMessage.json, PartyInviteMessage::class.java)
                        MongoDatabaseHandler.getPartyFromDb(partyInviteMessage.partyName)?.let {
                            MongoDatabaseHandler.parties[partyInviteMessage.partyName] = it
                            val invitedPlayer = PM.getPlayer(partyInviteMessage.player) ?: return

                            invitedPlayer.sendMessage(
                                PM.returnStyledText("<green>You have been invited to <aqua>${it.name}</aqua> by ${it.owner.name}!"),
                                false
                            )
                            invitedPlayer.sendMessage(
                                PM.returnStyledText("<green>Use <aqua>/party join ${it.name}</aqua> to join or click <u><aqua>[<click:run_command:'/party join ${it.name}'>HERE</click>]</aqua></u>!"),
                                false
                            )
                        }
                    }
                }
            }
        }
    }

    fun subscribe() {
        try {
            jedisSubscriber?.subscribe(jedisPubSub, "party-api")
        } catch (e: JedisConnectionException) {
            PartyAPI.LOGGER.error("Redis connection lost")
        }
    }

    fun publish(message: RedisMessage) {
        jedisPool?.resource?.publish("party-api", gson.toJson(message))
    }

    fun close() {
        jedisSubscriber?.close()
        jedisPool?.close()
    }

    enum class RedisMessageType {
        PARTY_CHAT, PARTY_CREATE, PARTY_DELETE, PARTY_JOIN, PARTY_LEAVE, PARTY_INVITE
    }

    data class RedisMessage(
        val type: RedisMessageType, val json: String
    ) {
        companion object {
            fun partyChatMessage(chat: PartyChat): RedisMessage {
                return RedisMessage(
                    RedisMessageType.PARTY_CHAT,
                    gson.toJson(PartyChatMessage(chat.partyName, chat.sender, chat.message))
                )
            }

            fun partyCreateMessage(partyName: String): RedisMessage {
                return RedisMessage(RedisMessageType.PARTY_CREATE, gson.toJson(PartyCreateMessage(partyName)))
            }

            fun partyDeleteMessage(partyName: String): RedisMessage {
                return RedisMessage(RedisMessageType.PARTY_DELETE, gson.toJson(PartyCreateMessage(partyName)))
            }

            fun partyJoinMessage(partyName: String, player: String): RedisMessage {
                return RedisMessage(RedisMessageType.PARTY_JOIN, gson.toJson(PartyJoinMessage(partyName, player)))
            }

            fun partyLeaveMessage(partyName: String, player: String): RedisMessage {
                return RedisMessage(RedisMessageType.PARTY_LEAVE, gson.toJson(PartyLeaveMessage(partyName, player)))
            }

            fun partyInviteMessage(partyName: String, playerUuid: String): RedisMessage {
                return RedisMessage(RedisMessageType.PARTY_INVITE, gson.toJson(PartyInviteMessage(partyName, playerUuid)))
            }
        }
    }

    data class PartyChatMessage(
        val partyName: String, val player: String, val message: String
    )

    data class PartyCreateMessage(
        val partyName: String
    )

    data class PartyJoinMessage(
        val partyName: String, val player: String
    )

    data class PartyLeaveMessage(
        val partyName: String, val player: String
    )

    data class PartyInviteMessage(
        val partyName: String, val player: String
    )

}