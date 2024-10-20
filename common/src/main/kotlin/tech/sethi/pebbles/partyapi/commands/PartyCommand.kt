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
import tech.sethi.pebbles.partyapi.dataclass.PartyPlayer
import tech.sethi.pebbles.partyapi.datahandler.PartyHandler
import tech.sethi.pebbles.partyapi.datahandler.PartyResponse
import tech.sethi.pebbles.partyapi.eventlistener.JoinPartyEvent
import tech.sethi.pebbles.partyapi.eventlistener.LeavePartyEvent
import tech.sethi.pebbles.partyapi.screens.PartyScreenHandler
import tech.sethi.pebbles.partyapi.util.ConfigHandler
import tech.sethi.pebbles.partyapi.util.PM
import java.util.concurrent.CompletableFuture

object PartyCommand {

    val playersInPartyChat = mutableListOf<String>()

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        val partyCommand = literal("party").executes { context ->
            val player = context.source.player ?: return@executes 1.also {
                context.source.sendFeedback(
                    { Text.of("You are not a player!") }, false
                )
            }

            PartyScreenHandler.open(player)

            1
        }

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
            CommandManager.argument("partyname", StringArgumentType.string()).executes { context ->
                val player = context.source.player ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("You are not a player!") }, false
                    )
                }

                val partyName = StringArgumentType.getString(context, "partyname")
                val partyPlayer = PartyPlayer(player.uuidAsString, player.name.string)
                val party = Party(partyName, partyPlayer, mutableListOf(partyPlayer))
                val response = PartyHandler.db.createParty(party)

                PM.sendText(player, response.message)

                1
            })

        val inviteCommand =
            literal("invite").then(CommandManager.argument("player", EntityArgumentType.player()).executes { context ->
                val player = context.source.player ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("You are not a player!") }, false
                    )
                }

                val party = PartyHandler.db.getPlayerParty(player.uuidAsString) ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("You are not in a party!") }, false
                    )
                }

                val invitedPlayer = EntityArgumentType.getPlayer(context, "player")
                val partyPlayer = PartyPlayer(invitedPlayer.uuidAsString, invitedPlayer.name.string)
                val response = PartyHandler.db.invitePlayerToParty(partyPlayer, party.name)

                PM.sendText(player, response.message)

                if (ConfigHandler.config.datastore == ConfigHandler.DatastoreType.JSON) {
                    if (response.success) {
                        invitedPlayer.sendMessage(
                            PM.returnStyledText("<green>You have been invited to <blue>${party.name} by ${context.source.player?.name?.string}</blue>!"),
                            false
                        )
                        invitedPlayer.sendMessage(
                            PM.returnStyledText("<green>Use <aqua>/party join ${party.name}</aqua> to join or click <u><aqua>[<click:run_command:'/party join ${party.name}'>HERE</click>]</aqua></u>!"),
                            false
                        )
                    }
                }

                1
            })

        val joinCommand =
            literal("join").then(CommandManager.argument("partyname", StringArgumentType.string()).executes { context ->
                val player = context.source.player ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("You are not a player!") }, false
                    )
                }

                val partyName = StringArgumentType.getString(context, "partyname")

                val party = PartyHandler.db.getParty(partyName) ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("Party <aqua>$partyName</aqua> does not exist!") }, false
                    )
                }

                val partyPlayer = PartyPlayer(player.uuidAsString, player.name.string)

                val response = PartyHandler.db.addPlayerToParty(partyPlayer, partyName)

                PM.sendText(player, response.message)

                if (response.success) {
                    party.members.forEach {
                        val member = PartyAPI.server!!.getPlayerManager().getPlayer(it.name)
                        member?.sendMessage(
                            PM.returnStyledText("<green>Player <aqua>${player.name.string}</aqua> has joined the party!"),
                            false
                        )
                    }
                }


                1
            })

        val leaveCommand = literal("leave").executes { context ->
            val player = context.source.player ?: return@executes 1.also {
                context.source.sendFeedback(
                    { Text.of("You are not a player!") }, false
                )
            }

            val party = PartyHandler.db.getPlayerParty(player.uuidAsString) ?: return@executes 1.also {
                context.source.sendFeedback(
                    { Text.of("You are not in a party!") }, false
                )
            }

            val partyPlayer = party.members.find { it.uuid == player.uuidAsString } ?: return@executes 1.also {
                context.source.sendFeedback(
                    { Text.of("You are not in a party!") }, false
                )
            }

            val response = PartyHandler.db.removePlayerFromParty(partyPlayer.uuid, party.name)

            PM.sendText(player, response.message)

            if (response.success) {
                party.members.forEach {
                    val member = PartyAPI.server!!.getPlayerManager().getPlayer(it.name) ?: return@forEach
                    PM.sendText(member, response.message)
                }
            }

            1
        }

        val kickCommand = literal("kick").then(CommandManager.argument("player", StringArgumentType.string())
            .suggests { context, builder ->
                getPartyMemberSuggestion(context, builder)
            }.executes { context ->
                val player = context.source.player ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("You are not a player!") }, false
                    )
                }

                val party = PartyHandler.db.getPlayerParty(player.uuidAsString) ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("You are not in a party!") }, false
                    )
                }

                val partyPlayer = party.members.find { it.uuid == player.uuidAsString } ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("You are not in a party!") }, false
                    )
                }

                val playerToKick = StringArgumentType.getString(context, "player")

                // check if context is party owner
                if (partyPlayer.uuid != party.owner.uuid) return@executes 1.also {
                    context.source.sendFeedback(
                        { PM.returnStyledText("<red>Only party owners can kick members!") }, false
                    )
                }

                val ptkPartyPlayer = party.members.find { it.name == playerToKick } ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { PM.returnStyledText("Player <aqua>$playerToKick</aqua> is not in the party!") }, false
                    )
                }

                val response = PartyHandler.db.removePlayerFromParty(ptkPartyPlayer.uuid, party.name)

                if (response.success) {
                    party.members.forEach {
                        val member = PartyAPI.server!!.playerManager.getPlayer(it.name) ?: return@forEach
                        PM.sendText(member, response.message)
                    }

                    val member = PartyAPI.server!!.playerManager.getPlayer(playerToKick) ?: return@executes 1
                    PM.sendText(member, response.message)
                }

                1
            })

        val disbandCommand = literal("disband").executes { context ->
            val player = context.source.player ?: return@executes 1.also {
                context.source.sendFeedback(
                    { Text.of("You are not a player!") }, false
                )
            }

            val party = PartyHandler.db.getPlayerParty(player.uuidAsString) ?: return@executes 1.also {
                context.source.sendFeedback(
                    { Text.of("You are not in a party!") }, false
                )
            }

            val cachedMembers = party.members
            val response = PartyHandler.db.deleteParty(party.name)

            if (response.success) {
                cachedMembers.forEach {
                    val member = PartyAPI.server!!.playerManager.getPlayer(it.name) ?: return@forEach
                    PM.sendText(member, response.message)
                }
            }

            1
        }

        val transferOwnerCommand =
            literal("transferOwner").then(CommandManager.argument("newOwner", StringArgumentType.string())
                .suggests(PartyCommand::getPartyMemberSuggestion).executes { context ->


                    1
                })

        val chatCommand = literal("chat").executes { context ->
            val player = context.source.player ?: return@executes 1.also {
                context.source.sendFeedback(
                    { Text.of("You are not a player!") }, false
                )
            }

            val party = PartyHandler.db.getPlayerParty(player.uuidAsString) ?: return@executes 1.also {
                context.source.sendFeedback(
                    { Text.of("You are not in a party!") }, false
                )
            }

            if (party.hasChatToggled(player.uuidAsString)) {
                context.source.sendFeedback(
                    { Text.of("You have to toggle your party chat!") }, false
                )
                return@executes 1
            }

            if (playersInPartyChat.contains(player.uuidAsString)) {
                playersInPartyChat.remove(player.uuidAsString)
                PM.sendText(player, "Party chat disabled")
            } else {
                playersInPartyChat.add(player.uuidAsString)
                PM.sendText(player, "Party chat enabled")
            }

            1
        }

        val listCommand = literal("list")
            .executes { context ->
                val player = context.source.player ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("You are not a player!") }, false
                    )
                }

                val party = PartyHandler.db.getPlayerParty(player.uuidAsString)
                if (party != null) {
                    val partyMembers = party.members
                    if (partyMembers.isEmpty()) {
                        context.source.sendFeedback( { PM.returnStyledText("<red>The party has no members!") }, false)
                        return@executes 0
                    }

                    context.source.sendFeedback( { PM.returnStyledText("<gold>Party Members:") }, false)
                    partyMembers.forEach { member ->
                        val message = if (party.isOwner(member.uuid)) {
                            PM.returnStyledText("<blue> - ${party.owner.name}<gold> \uD83D\uDC51</blue>")
                        } else {
                            PM.returnStyledText("<green> - ${member.name}")
                        }
                        context.source.sendFeedback( { message }, false)
                    }

                } else {
                    context.source.sendFeedback(
                        { PM.returnStyledText("<red>You are not in a party!") }, false
                    )
                }

                1
            }

        val toggleChatCommand = literal("toggleChat")
            .executes { context ->
                val player = context.source.player ?: return@executes 1.also {
                    context.source.sendFeedback(
                        { Text.of("You are not a player!") }, false
                    )
                }

                val party = PartyHandler.db.getPlayerParty(player.uuidAsString)
                if (party != null) {
                    if (party.noChatList.contains(player.uuidAsString)) {
                        party.noChatList.remove(player.uuidAsString)
                        context.source.sendFeedback(
                            { PM.returnStyledText("<green>Enabled Party Chat!") }, false
                        )
                    } else {
                        playersInPartyChat.removeIf { it == player.uuidAsString }
                        party.noChatList.add(player.uuidAsString)
                        context.source.sendFeedback(
                            { PM.returnStyledText("<red>Disabled Party Chat!") }, false
                        )
                    }

                } else {
                    context.source.sendFeedback(
                        { PM.returnStyledText("<red>You are not in a party!") }, false
                    )
                }

            1
        }


        partyCommand.then(toggleChatCommand)
        partyCommand.then(listCommand)
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
            context.source.player?.uuidAsString ?: return CommandSource.suggestMatching(
                mutableListOf(), builder
            )
        )
        val members = party?.members ?: return CommandSource.suggestMatching(mutableListOf(), builder)
        members.forEach { builder.suggest(it.name) }
        return builder.buildFuture()
    }
}