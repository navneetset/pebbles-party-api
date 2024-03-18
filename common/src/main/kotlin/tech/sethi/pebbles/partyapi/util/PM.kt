package tech.sethi.pebbles.partyapi.util

import com.mojang.brigadier.ParseResults
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import tech.sethi.pebbles.partyapi.PartyAPI
import tech.sethi.pebbles.partyapi.PartyAPI.server
import java.util.*

object PM {

    private fun parseMessageWithStyles(text: String, placeholder: String, style: Boolean = true): Component {
        val mm = if (style) {
            MiniMessage.miniMessage()
        } else {
            MiniMessage.builder().tags(TagResolver.empty()).build()
        }

        return mm.deserialize(text.replace("{placeholder}", placeholder)).decoration(TextDecoration.ITALIC, false)
    }

    fun returnStyledText(text: String, style: Boolean = true): MutableText {
        val component = parseMessageWithStyles(text, "placeholder", style)
        val gson = GsonComponentSerializer.gson()
        val json = gson.serialize(component)
        return Text.Serializer.fromJson(json) as MutableText
    }

    fun returnStyledJson(text: String): String {
        val component = parseMessageWithStyles(text, "placeholder")
        val gson = GsonComponentSerializer.gson()
        val json = gson.serialize(component)
        return json
    }

    fun setLore(itemStack: ItemStack, lore: List<String>) {
        val itemNbt = itemStack.getOrCreateSubNbt("display")
        val loreNbt = NbtList()

        for (line in lore) {
            loreNbt.add(NbtString.of(returnStyledJson(line)))
        }

        itemNbt.put("Lore", loreNbt)
    }

    fun sendText(player: PlayerEntity, text: String) {
        val component = returnStyledText(text)
        player.sendMessage(component, false)
    }

    fun parseCommand(
        command: String, context: String, server: MinecraftServer, player: PlayerEntity?
    ): ParseResults<ServerCommandSource>? {
        val cmdManager = server.commandManager

        when (context) {
            "console" -> {
                return cmdManager?.dispatcher?.parse(command, server.commandSource)
            }

            "player" -> {
                return cmdManager?.dispatcher?.parse(command, player?.commandSource)
            }
        }

        return null
    }

    fun createItemStack(
        item: Item, count: Int, name: String? = null, lore: List<String> = listOf(), nbtString: String? = null
    ): ItemStack {
        val itemStack = ItemStack(item, count)

        if (nbtString != null && nbtString != "" && nbtString != "{}") {
            itemStack.nbt = NbtHelper.fromNbtProviderString(nbtString)

            // if {palette:[]}, is empty, remove it
            if (itemStack.nbt?.get("palette")?.toString() == "[]") {
                itemStack.nbt?.remove("palette")
            }
        }

        if (name != null) {
            itemStack.setCustomName(returnStyledText(name))
        }

        if (lore.isEmpty().not()) {
            setLore(itemStack, lore)
        }

        return itemStack
    }

    fun getItem(itemId: String): Item {
        return Registries.ITEM.get(Identifier.tryParse(itemId))
    }

    fun getSounds(soundId: String): SoundEvent? {
        return Registries.SOUND_EVENT.get(Identifier.tryParse(soundId))
    }

    fun getStatusEffect(statusEffectId: String): StatusEffect {
        return Registries.STATUS_EFFECT.get(Identifier.tryParse(statusEffectId))
            ?: throw Exception("Status effect $statusEffectId not found")
    }

    fun runCommand(command: String) {
        try {
            val parseResults: ParseResults<ServerCommandSource> =
                server!!.commandManager.dispatcher.parse(command, server!!.commandSource)
            server!!.commandManager.dispatcher.execute(parseResults)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun runCommandAsPlayer(command: String, player: PlayerEntity) {
        try {
            val parseResults: ParseResults<ServerCommandSource> =
                server!!.commandManager.dispatcher.parse(command, player.commandSource)
            server!!.commandManager.dispatcher.execute(parseResults)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLocaleText(key: String): String {
        return Text.translatable(key).string
    }

    fun formatTime(time: Long): String {
        val seconds = time / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return "${hours}h ${minutes % 60}m ${seconds % 60}s"
    }

    fun getPlayer(uuidOrName: String): ServerPlayerEntity? {
        try {
            return PartyAPI.server!!.playerManager.getPlayer(UUID.fromString(uuidOrName))
        } catch (e: IllegalArgumentException) {
            return PartyAPI.server!!.playerManager.getPlayer(uuidOrName)
        }
    }
}