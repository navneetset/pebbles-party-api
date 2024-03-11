package tech.sethi.pebbles.partyapi.datahandler

enum class PartyResponse {
    SUCCESS,
    FAIL,
    NAME_TAKEN,
    NOT_FOUND,
    INVALID,
    ALREADY_MEMBER,
    NOT_MEMBER,
    ALREADY_INVITED,
    NOT_INVITED,
    ALREADY_OWNER,
    NOT_OWNER,
    ALREADY_PARTY,
    NOT_PARTY,
    CONNECTION_ERROR,
}