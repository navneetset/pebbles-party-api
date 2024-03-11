package tech.sethi.pebbles.partyapi.screens

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import tech.sethi.pebbles.partyapi.dataclass.Party
import tech.sethi.pebbles.partyapi.datahandler.PartyHandler
import tech.sethi.pebbles.partyapi.util.PM

class PartyScreenHandler(
    syncId: Int, val player: ServerPlayerEntity
) : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X1, syncId, player.inventory, SimpleInventory(9 * 1), 1) {

    val memberSlots = listOf(0, 2, 4, 6, 8)

    init {
        setupPage()
    }

    companion object {
        fun open(player: ServerPlayerEntity) {
            player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, _ ->
                PartyScreenHandler(
                    syncId, player
                )
            }, PM.returnStyledText("Party")))
        }
    }


    fun setupPage() {
        val party = getParty()

        if (party == null) {
            val barrierItem = ItemStack(Items.BARRIER)
            barrierItem.setCustomName(PM.returnStyledText("<red>Not in a party</red>"))
            val lore = mutableListOf(
                "<yellow>/party create <name>",
                "<yellow>To create a party",
                "",
                "<yellow>/party join <name>",
                "<yellow>To join a party",
            )
            PM.setLore(barrierItem, lore)
            memberSlots.forEach { slot ->
                inventory.setStack(slot, barrierItem)
            }
        } else {
            val members = party.members
            val owner = party.owner
            members.remove(owner)
            members.add(0, owner)

            members.forEachIndexed { index, member ->
                val slot = memberSlots[index]
                val memberItem = ItemStack(Items.PLAYER_HEAD)
                memberItem.orCreateNbt.putString("SkullOwner", member)
                memberItem.setCustomName(PM.returnStyledText("<green>${member}</green>"))

                if (member == owner) {
                    PM.setLore(memberItem, mutableListOf("<yellow>Party Owner"))
                }
                inventory.setStack(slot, memberItem)
            }
        }
    }


    fun getParty(): Party? {
        return PartyHandler.db.getPlayerParty(player.name.string)
    }

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType?, player: PlayerEntity?) {
        return
    }
}
