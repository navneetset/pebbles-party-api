package tech.sethi.pebbles.partyapi.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import tech.sethi.pebbles.partyapi.PartyAPI
import tech.sethi.pebbles.partyapi.dataclass.Party
import tech.sethi.pebbles.partyapi.dataclass.PartyChat
import tech.sethi.pebbles.partyapi.datahandler.PartyHandler
import tech.sethi.pebbles.partyapi.datahandler.PartyResponse
import tech.sethi.pebbles.partyapi.eventlistener.JoinPartyEvent
import tech.sethi.pebbles.partyapi.eventlistener.LeavePartyEvent
import tech.sethi.pebbles.partyapi.screens.PartyScreenHandler
import tech.sethi.pebbles.partyapi.util.PM
import java.util.concurrent.CompletableFuture

object PartyCommand {

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        val partyCommand = literal("party")

        val menuCommand = literal("menu").executes { context ->
            val player = context.source.player ?: return@executes 1.also {
                context.source.sendFeedback(
                    { Text.of("You are not a player!") }, false
                )
            }

            PartyScreenHandler.open(player)

            1
        }

        val createCommand = literal("create").then(
            CommandManager.argument("partyName", StringArgumentType.string()).executes { context ->
                    val partyName = StringArgumentType.getString(context, "partyName")
                    val player = context.source.player?.name?.string ?: return@executes 1.also {
                        context.source.sendFeedback(
                            { Text.of("You are not a player!") }, false
                        )
                    }

                    // validate name, cannot contain spaces and special characters
                    if (partyName.contains(" ") || partyName.contains("[^a-zA-Z0-9]")) {
                        context.source.sendFeedback(
                            { PM.returnStyledText("<red>Party name can only contain letters and numbers!") }, false
                        )
                        return@executes 1
                    }

                    val newParty = Party(
                        partyName, player, mutableListOf(player), mutableListOf()
                    )

                    val response = PartyHandler.db.createParty(newParty)

                    when (response) {
                        PartyResponse.SUCCESS -> {
                            context.source.sendFeedback(
                                { PM.returnStyledText("<green>Party <blue>${partyName}</blue> created!") }, false
                            )
                        }

                        PartyResponse.ALREADY_PARTY -> {
                            context.source.sendFeedback(
                                { PM.returnStyledText("<red>You are in a party or party with this name already exists.") },
                                false
                            )
                        }

                        else -> {
                            context.source.sendFeedback(
                                { PM.returnStyledText("<red>Something went wrong!") }, false
                            )
                        }
                    }

                    1
                })

        val inviteCommand =
            literal("invite").then(CommandManager.argument("player", EntityArgumentType.player()).executes { context ->
                val player = context.source.player?.name?.string ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("You are not a player!") }, false
                    )
                }

                val party = PartyHandler.db.getPlayerParty(player)

                if (party != null && party.owner == player) {
                    val invitedPlayer = EntityArgumentType.getPlayer(context, "player")
                    if (party.members.contains(invitedPlayer.name.string)) {
                        context.source.sendFeedback(
                            { PM.returnStyledText("<red>Player <blue>${invitedPlayer.name.string}</blue> is already in your party!") },
                            false
                        )
                    } else {
                        party.invites.add(invitedPlayer.name.string)
                        val response = PartyHandler.db.updateParty(party)
                        when (response) {
                            PartyResponse.SUCCESS -> {
                                context.source.sendFeedback(
                                    { PM.returnStyledText("<green>Player <blue>${invitedPlayer.name.string}</blue> invited!") },
                                    false
                                )

                                invitedPlayer.sendMessage(
                                    PM.returnStyledText("<green>You have been invited to <blue>${party.name} by ${context.source.player?.name?.string}</blue>!"),
                                    false
                                )
                                invitedPlayer.sendMessage(
                                    PM.returnStyledText("<green>Use <blue>/party join ${party.name}</blue> to join or click <u><aqua>[<click:run_command:'/party join ${party.name}'>HERE</click>]</aqua></u>!"),
                                    false
                                )
                            }

                            else -> {
                                context.source.sendFeedback(
                                    { PM.returnStyledText("<red>Something went wrong!") }, false
                                )
                            }
                        }
                    }
                } else {
                    context.source.sendFeedback(
                        { PM.returnStyledText("<red>You are not the owner of a party!") }, false
                    )
                }

                1
            })

        val joinCommand =
            literal("join").then(CommandManager.argument("partyName", StringArgumentType.string()).executes { context ->
                val partyName = StringArgumentType.getString(context, "partyName")
                val player = context.source.player?.name?.string ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("You are not a player!") }, false
                    )
                }

                val party = PartyHandler.db.getParty(partyName)

                if (party != null) {
                    if (party.invites.contains(player)) {
                        party.members.add(player)
                        party.invites.remove(player)
                        val response = PartyHandler.db.updateParty(party)
                        when (response) {
                            PartyResponse.SUCCESS -> {
                                context.source.sendFeedback(
                                    { PM.returnStyledText("<green>You have joined <blue>${party.name}</blue>!") }, false
                                )
                                JoinPartyEvent.EVENT.invoker().onJoinParty(player)
                            }

                            else -> {
                                context.source.sendFeedback(
                                    { PM.returnStyledText("<red>Something went wrong!") }, false
                                )
                            }
                        }
                    } else {
                        context.source.sendFeedback(
                            { PM.returnStyledText("<red>You are not invited to <blue>${party.name}</blue>!") }, false
                        )
                    }
                } else {
                    context.source.sendFeedback(
                        { PM.returnStyledText("<red>Party <blue>${partyName}</blue> does not exist!") }, false
                    )
                }

                1
            })

        val leaveCommand = literal("leave").executes { context ->
            val player = context.source.player?.name?.string ?: return@executes 1.also {
                context.source.sendFeedback(
                    { Text.of("You are not a player!") }, false
                )
            }

            val party = PartyHandler.db.getPlayerParty(player)

            if (party != null) {
                if (party.owner == player) {
                    context.source.sendFeedback(
                        { PM.returnStyledText("<red>You are the owner of the party! Use <blue>/party disband</blue> to disband the party!") },
                        false
                    )
                } else {
                    party.members.remove(player)
                    val response = PartyHandler.db.updateParty(party)
                    when (response) {
                        PartyResponse.SUCCESS -> {
                            context.source.sendFeedback(
                                { PM.returnStyledText("<green>You have left <blue>${party.name}</blue>!") }, false
                            )
                            LeavePartyEvent.EVENT.invoker().onLeaveParty(player)
                        }

                        else -> {
                            context.source.sendFeedback(
                                { PM.returnStyledText("<red>Something went wrong!") }, false
                            )
                        }
                    }
                }
            } else {
                context.source.sendFeedback(
                    { PM.returnStyledText("<red>You are not in a party!") }, false
                )
            }

            1
        }

        val kickCommand = literal("kick").then(CommandManager.argument("player", StringArgumentType.string())
            .suggests { context, builder ->
                getPartyMemberSuggestion(context, builder)
            }.executes { context ->
                val player = context.source.player?.name?.string ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("You are not a player!") }, false
                    )
                }

                val party = PartyHandler.db.getPlayerParty(player)

                if (party != null && party.owner == player) {
                    val playerToKick = StringArgumentType.getString(context, "player")
                    if (playerToKick == party.owner) {
                        context.source.sendFeedback(
                            { PM.returnStyledText("<red>You cannot kick the owner of the party!") }, false
                        )
                        return@executes 1
                    }
                    if (party.members.contains(playerToKick)) {
                        party.members.remove(playerToKick)
                        val response = PartyHandler.db.updateParty(party)
                        when (response) {
                            PartyResponse.SUCCESS -> {
                                context.source.sendFeedback(
                                    { PM.returnStyledText("<green>Player <blue>${playerToKick}</blue> kicked from <blue>${party.name}</blue>!") },
                                    false
                                )

                                val kickedPlayer = PartyAPI.server!!.playerManager.getPlayer(playerToKick)

                                kickedPlayer?.sendMessage(
                                    PM.returnStyledText("<red>You have been kicked from <blue>${party.name}</blue> by ${player}!"),
                                    false
                                )

                                if (kickedPlayer != null) {
                                    LeavePartyEvent.EVENT.invoker().onLeaveParty(playerToKick)
                                }
                            }

                            else -> {
                                context.source.sendFeedback(
                                    { PM.returnStyledText("<red>Something went wrong!") }, false
                                )
                            }
                        }
                    } else {
                        context.source.sendFeedback(
                            { PM.returnStyledText("<red>Player <blue>${playerToKick}</blue> is not in your party!") },
                            false
                        )
                    }
                } else {
                    context.source.sendFeedback(
                        { PM.returnStyledText("<red>You are not the owner of a party!") }, false
                    )
                }

                1
            })

        val disbandCommand = literal("disband").executes { context ->
            val player = context.source.player?.name?.string ?: return@executes 1.also {
                context.source.sendFeedback(
                    { Text.of("You are not a player!") }, false
                )
            }

            val party = PartyHandler.db.getPlayerParty(player)

            if (party != null && party.owner == player) {
                val members = party.members
                val response = PartyHandler.db.deleteParty(party.name)
                when (response) {
                    PartyResponse.SUCCESS -> {
                        context.source.sendFeedback(
                            { PM.returnStyledText("<green>Party <blue>${party.name}</blue> disbanded!") }, false
                        )
                        members.forEach { member ->
                            if (member != player) PartyAPI.server!!.playerManager.getPlayer(member)?.sendMessage(
                                PM.returnStyledText("<red>Your party <blue>${party.name}</blue> has been disbanded by ${player}!"),
                                false
                            )
                            LeavePartyEvent.EVENT.invoker().onLeaveParty(member)
                        }
                    }

                    else -> {
                        context.source.sendFeedback(
                            { PM.returnStyledText("<red>Something went wrong!") }, false
                        )
                    }
                }
            } else {
                context.source.sendFeedback(
                    { PM.returnStyledText("<red>You are not the owner of a party!") }, false
                )
            }

            1
        }

        val transferOwnerCommand =
            literal("transferOwner").then(CommandManager.argument("newOwner", StringArgumentType.string())
                .suggests(PartyCommand::getPartyMemberSuggestion).executes { context ->
                    val newOwner = StringArgumentType.getString(context, "newOwner")
                    val player = context.source.player?.name?.string ?: return@executes 1.also {
                        context.source.sendFeedback(
                            { Text.of("You are not a player!") }, false
                        )
                    }

                    val party = PartyHandler.db.getPlayerParty(player)

                    if (party != null && party.owner == player) {
                        if (party.members.contains(newOwner)) {
                            party.owner = newOwner
                            val response = PartyHandler.db.updateParty(party)
                            when (response) {
                                PartyResponse.SUCCESS -> {
                                    context.source.sendFeedback(
                                        { PM.returnStyledText("<green>Ownership transferred to <blue>${newOwner}</blue>!") },
                                        false
                                    )
                                }

                                else -> {
                                    context.source.sendFeedback(
                                        { PM.returnStyledText("<red>Something went wrong!") }, false
                                    )
                                }
                            }
                        } else {
                            context.source.sendFeedback(
                                { PM.returnStyledText("<red>Player <blue>${newOwner}</blue> is not in your party!") },
                                false
                            )
                        }
                    } else {
                        context.source.sendFeedback(
                            { PM.returnStyledText("<red>You are not the owner of a party!") }, false
                        )
                    }

                    1
                })

        val chatCommand = literal("chat").then(
            CommandManager.argument("message", StringArgumentType.greedyString()).executes { context ->
                    val message = StringArgumentType.getString(context, "message")
                    val player = context.source.player?.name?.string ?: return@executes 1.also {
                        context.source.sendFeedback(
                            { Text.of("You are not a player!") }, false
                        )
                    }

                    val party = PartyHandler.db.getPlayerParty(player)
                    if (party != null) {
                        val partyChat = PartyChat(party.name, player, message)
                        PartyHandler.db.sendChat(partyChat)
                    } else {
                        context.source.sendFeedback(
                            { PM.returnStyledText("<red>You are not in a party!") }, false
                        )
                    }

                    1
                })


        partyCommand.then(menuCommand)
        partyCommand.then(createCommand)
        partyCommand.then(inviteCommand)
        partyCommand.then(joinCommand)
        partyCommand.then(leaveCommand)
        partyCommand.then(kickCommand)
        partyCommand.then(disbandCommand)
        partyCommand.then(chatCommand)
        partyCommand.then(transferOwnerCommand)

        dispatcher.register(partyCommand)
    }

    private fun getPartyMemberSuggestion(
        context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val party = PartyHandler.db.getPlayerParty(
            context.source.player?.name?.string ?: return CommandSource.suggestMatching(
                mutableListOf(), builder
            )
        )
        val members = party?.members ?: return CommandSource.suggestMatching(mutableListOf(), builder)
        members.forEach { builder.suggest(it) }
        return builder.buildFuture()
    }
}