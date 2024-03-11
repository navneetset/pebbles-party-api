package tech.sethi.pebbles.partyapi.fabric

import tech.sethi.pebbles.partyapi.fabriclike.PartyApiFabricLike
import net.fabricmc.api.ModInitializer


object PartyAPIFabric: ModInitializer {
    override fun onInitialize() {
        PartyApiFabricLike.init()
    }
}
