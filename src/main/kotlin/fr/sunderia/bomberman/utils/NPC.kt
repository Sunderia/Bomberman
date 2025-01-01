package fr.sunderia.bomberman.utils

import fr.sunderia.bomberman.party.GameCommand
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.*
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import java.util.UUID

class NPC(uuid: UUID, private val name: String, private val skin: PlayerSkin = BOMBERMAN_SKIN, private val interactCallback: ((NPC, PlayerEntityInteractEvent) -> Unit)): EntityCreature(EntityType.PLAYER, uuid) {

    companion object {
        val BOMBERMAN_SKIN = PlayerSkin(
            "ewogICJ0aW1lc3RhbXAiIDogMTcxNzc4MzI0NTI4MywKICAicHJvZmlsZUlkIiA6ICI1OWZmOTU1YzMxYjk0MWI0YWQwNDg4NDk0ODkzNzUzOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJNZWdhMzQ5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzdlZjUwZjM5MGRjY2Y0NzBhNmM2MzNjM2UwM2NkNDMzNDE4NWVlZjc4YmE5NmM3ZmJjNmUwNmE4NjY2NmY3MmMiCiAgICB9CiAgfQp9",
            "tFQkm3OXAYXi6EIjcW1liqTUUImzv+0I+sIqJR5Wb36r0pOh/LJVYV1bg28p1veCNZS+QbyumUfY6wzxz+kTAOl2ohfMDtuBlaK0JI3+TgX2OwOUOJv1z+/u+hkmq9yyO1U/bpHxrxSE3M4+SBExhRw3crfSe8bnaHDTUiMWm6nQH4u81H4sDnq1l1eraBIUUIymIxK/WwBf6fF2ylut6IPBU/SoFqvQeJN6JYcamD9aOrP+WBbkPV4A+ROuAGKfamZeFvZOoEDVx2QInfz0E3zBqqnLcM5Zqde6ce3raKzFxR1eQ24NX9ixGNkXa5/2jPq8GGrp02Tz+2X2+oxnani+CD9t8/U9V2zgzUIoXT2xTl0XVbA860cbwuhe/h7yYLA/Wsem4Q7adQXmr4tYSfS1hckF0LjXLCq1YCeTxoaIXQPuBwgB6+wHZXXBgW5+l/g5HBtCtK+qxAQA1kb1ntJr3nXwWnMq6d4AxWykZAI5VMUX64TGMyKpcO15HG6yRgj2L+WAMqpVvM/qLBvcOmt1HcHfHTrP3fkjfdfSZdBLO/DO+R2dZzpuzaUbkZSk5SZ6ZTRtp4bZ1OvoV8kzPCyBSvnEllauqGjC2VPkPFNizKrkPVi21gUYTPdz6IcRFnUU3IC+aviLPevq53pXYNeRmy1MNN7UK21eQD7I8p8="
        )
        fun createNPC(lobby: Instance, pos: Pos = Pos(0.0, 45.0, 10.0)) {
            val npc = NPC(UUID.randomUUID(), "§aPlay Game", interactCallback = { _, event -> GameCommand.startGame(event.player) })
            npc.setInstance(lobby, pos)
            npc.setView(180.0F, .0F)
        }
    }

    override fun updateNewViewer(player: Player) {
        val properties = mutableListOf<PlayerInfoUpdatePacket.Property>()
        if(skin.textures() != null && skin.signature() != null) {
            properties.add(PlayerInfoUpdatePacket.Property("textures", skin.textures(), skin.signature()))
        }
        val entry = PlayerInfoUpdatePacket.Entry(uuid, name, properties, false, 0, GameMode.CREATIVE, null, null, 0)
        player.sendPacket(PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.ADD_PLAYER, entry))
        super.updateNewViewer(player)
        player.sendPackets(EntityMetaDataPacket(entityId,
            java.util.Map.of(17, Metadata.Byte((127).toByte())) as Map<Int, Metadata.Entry<*>>
        ))
    }

    override fun updateOldViewer(player: Player) {
        super.updateOldViewer(player)
        player.sendPackets(PlayerInfoRemovePacket(uuid))
    }

    fun onInteract(event: PlayerEntityInteractEvent) = this.interactCallback(this, event)
}