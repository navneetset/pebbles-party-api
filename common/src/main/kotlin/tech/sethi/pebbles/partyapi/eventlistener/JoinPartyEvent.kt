package tech.sethi.pebbles.partyapi.eventlistener

import dev.architectury.event.Event
import dev.architectury.event.EventFactory

interface JoinPartyEvent {
    fun onJoinParty(playerUuid: String)

    companion object {
        val EVENT: Event<JoinPartyEvent> = EventFactory.createLoop()
    }
}