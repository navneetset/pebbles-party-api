package tech.sethi.pebbles.partyapi.forge

import dev.architectury.platform.forge.EventBuses
import tech.sethi.pebbles.partyapi.PartyAPI
import net.minecraftforge.fml.common.Mod
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(PartyAPI.MOD_ID)
object PartyAPIForge {
    init {
        // Submit our event bus to let architectury register our content on the right time
        EventBuses.registerModEventBus(PartyAPI.MOD_ID, MOD_BUS)
        PartyAPI.init()
    }
}