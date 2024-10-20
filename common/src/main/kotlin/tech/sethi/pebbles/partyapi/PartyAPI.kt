package tech.sethi.pebbles.partyapi

import dev.architectury.event.EventResult
import dev.architectury.event.events.common.ChatEvent
import dev.architectury.event.events.common.CommandRegistrationEvent
import dev.architectury.event.events.common.LifecycleEvent
import net.minecraft.server.MinecraftServer
import org.apache.logging.log4j.LogManager
import tech.sethi.pebbles.partyapi.commands.PartyCommand
import tech.sethi.pebbles.partyapi.dataclass.PartyChat
import tech.sethi.pebbles.partyapi.datahandler.PartyHandler
import tech.sethi.pebbles.partyapi.eventlistener.JoinPartyEvent
import tech.sethi.pebbles.partyapi.util.ConfigHandler
import tech.sethi.pebbles.partyapi.util.PM

object PartyAPI {
    const val MOD_ID = "pebbles_partyapi"
    val LOGGER = LogManager.getLogger()
    var server: MinecraftServer? = null

    fun init() {
        LOGGER.info("Pebble's Party API Initialized!")

        LifecycleEvent.SERVER_STARTING.register { server = it }

        PartyHandler

        CommandRegistrationEvent.EVENT.register { dispatcher, _, _ ->
            PartyCommand.register(dispatcher)
        }

//        JoinPartyEvent.EVENT.register(object : JoinPartyEvent {
//            override fun onJoinParty(playerName: String) {
//                LOGGER.info("$playerName joined a party!")
//            }
//        })

        ChatEvent.RECEIVED.register { player, component ->
            if (player != null && player.uuidAsString in PartyCommand.playersInPartyChat) {
                val party = PartyHandler.db.getPlayerParty(player.uuidAsString)
                val message = component.string
                if (party != null) {
                    val chat = PartyChat(party.name, player.name.string, message)

                    val response = PartyHandler.db.sendChat(chat)
                    if (response.success.not()) PM.sendText(player, response.message)
                } else {
                    PM.sendText(player, "<red>You are not in a party! <aqua>/party chat</aqua> to disable party chat")
                }

                return@register EventResult.interruptFalse()
            }

            return@register EventResult.pass()
        }

        ConfigHandler
    }
}