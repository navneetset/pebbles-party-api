package tech.sethi.pebbles.partyapi

import dev.architectury.event.events.common.CommandRegistrationEvent
import dev.architectury.event.events.common.LifecycleEvent
import net.minecraft.server.MinecraftServer
import org.apache.logging.log4j.LogManager
import tech.sethi.pebbles.partyapi.commands.PartyCommand
import tech.sethi.pebbles.partyapi.util.ConfigHandler

object PartyAPI {
    const val MOD_ID = "pebbles_partyapi"
    val LOGGER = LogManager.getLogger()
    var server: MinecraftServer? = null

    fun init() {
        LOGGER.info("Pebble's Party API Initialized!")

        LifecycleEvent.SERVER_STARTING.register { server = it }

        CommandRegistrationEvent.EVENT.register { dispatcher, _, _ ->
            PartyCommand.register(dispatcher)
        }

        ConfigHandler
    }
}