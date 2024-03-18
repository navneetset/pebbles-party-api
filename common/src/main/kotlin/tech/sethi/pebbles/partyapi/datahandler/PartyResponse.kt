package tech.sethi.pebbles.partyapi.datahandler

data class PartyResponse(
    val success: Boolean,
    val message: String
) {
    companion object {
        fun success(message: String = ""): PartyResponse {
            return PartyResponse(true, message)
        }

        fun fail(message: String): PartyResponse {
            return PartyResponse(false, message)
        }
    }
}
