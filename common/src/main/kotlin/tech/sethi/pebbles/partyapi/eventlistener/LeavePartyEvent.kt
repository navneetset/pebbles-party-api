package tech.sethi.pebbles.partyapi.eventlistener

import dev.architectury.event.Event
import dev.architectury.event.EventFactory

interface LeavePartyEvent {
    fun onLeaveParty(playerName: String)

    companion object {
        val EVENT: Event<LeavePartyEvent> = EventFactory.createLoop()
    }
}