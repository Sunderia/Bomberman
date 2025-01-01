package fr.sunderia.bomberman.utils

import fr.sunderia.bomberman.Bomberman
import fr.sunderia.bomberman.PrimedTntEntity
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.HeadProfile
import net.minestom.server.network.packet.server.play.SetCooldownPacket

data class FakeNPC(val head: Entity, val torso: Entity, val rightArm: Entity, val leftArm: Entity, val rightLeg: Entity, val leftLeg: Entity) {

    fun remove() {
        head.remove()
        torso.remove()
        rightArm.remove()
        leftArm.remove()
        rightLeg.remove()
        leftLeg.remove()
    }

    private fun moveFW(mult: Double) {
        head.velocity = head.position.direction().withY(.0).mul(mult)
        torso.velocity = torso.position.direction().withY(.0).mul(mult)
        rightArm.velocity = rightArm.position.direction().withY(.0).mul(mult)
        leftArm.velocity = leftArm.position.direction().withY(.0).mul(mult)
        rightLeg.velocity = rightLeg.position.direction().withY(.0).mul(mult)
        leftLeg.velocity = leftLeg.position.direction().withY(.0).mul(mult)
    }

    private fun rotate(relativeYaw: Float) {
        head.teleport(head.position.withYaw(head.position.yaw + relativeYaw))
        torso.teleport(torso.position.withYaw(torso.position.yaw + relativeYaw))
        rightArm.teleport(rightArm.position.withYaw(rightArm.position.yaw + relativeYaw))
        leftArm.teleport(leftArm.position.withYaw(leftArm.position.yaw + relativeYaw))
        rightLeg.teleport(rightLeg.position.withYaw(rightLeg.position.yaw + relativeYaw))
        leftLeg.teleport(leftLeg.position.withYaw(leftLeg.position.yaw + relativeYaw))
    }

    fun moveForward() = moveFW(-3.0)
    fun moveBackward() = moveFW(3.0)
    fun rotateRight() = rotate(90f)
    fun rotateLeft() = rotate(-90f)
    fun placeTNT(player: Player) {
        // Check if position is valid
        val pos = head.position.direction().mul(-1.0).add(head.position.withY(40.0))
        val instance = player.instance
        val block = instance.getBlock(pos)
        if (!block.isAir) {
            Bomberman.logger.info("Cannot place TNT on ${block.name()} at $pos")
            return
        }
        // if(Game.getGame(player.instance)!!.gameStatus != GameStatus.RUNNING) return
        if (Cooldown.isInCooldown(player.uuid, "tnt")) return
        player.playerConnection.sendPacket(SetCooldownPacket(Material.TNT.name(), 0))
        instance.setBlock(pos, Block.BARRIER)
        instance.setBlock(pos.add(.0, 1.0, .0), Block.BARRIER)
        val timeInSeconds = 2
        Cooldown(player.uuid, "tnt", timeInSeconds).start()
        val packet = SetCooldownPacket(Material.TNT.name(), timeInSeconds * 20)
        player.playerConnection.sendPacket(packet)
        val tnt = PrimedTntEntity(player, instance, pos.asPosition().toBlockPos().add(0.5, 0.0, 0.5))
        if(player.hasTag(PowerupTags.PIERCE.getBool())) {
            tnt.setPierce(true)
        }
    }

    companion object {
        private fun createPlayerPart(model: String, profile: HeadProfile, pos: Pos, translation: Point, lobby: Instance): Entity {
            val entity = Entity(EntityType.ITEM_DISPLAY)
            entity.setNoGravity(true)
            entity.aerodynamics = entity.aerodynamics.withVerticalAirResistance(0.7).withHorizontalAirResistance(0.7)
            entity.isGlowing = true //TODO: Fix entity not glowing everywhere except its head
            val meta = entity.entityMeta as ItemDisplayMeta
            meta.glowColorOverride = NamedTextColor.LIGHT_PURPLE.value()
            meta.displayContext = ItemDisplayMeta.DisplayContext.THIRD_PERSON_RIGHT_HAND
            meta.viewRange = .6f
            meta.leftRotation = floatArrayOf(.0f, .0f, .0f, 1f)
            meta.rightRotation = floatArrayOf(.0f, .0f, .0f, 1f)
            meta.scale = Vec(1.0, 1.0, 1.0)
            meta.translation = translation
            val headItem = ItemStack.of(Material.PLAYER_HEAD).withItemModel("player_display:player/${model}").with(
                ItemComponent.PROFILE, profile
            )
            meta.itemStack = headItem
            entity.setInstance(lobby, pos)
            return entity
        }

        fun createFakeNPC(lobby: Instance, basePos: Pos): FakeNPC {
            val map = mapOf(
                "head" to (Pos(.0, 1.4, .0) to Pos.ZERO),
                "torso" to (Pos(.0, 1.4, .0) to Pos(.0, -3072.0, .0)),
                "right_arm" to (Pos(.0, 1.4, .0) to Pos(.0, -1024.0, .0)),
                "left_arm" to (Pos(.0, 1.4, .0) to Pos(.0, -2048.0, .0)),
                "right_leg" to (Pos(.0, .7, .0) to Pos(.0, -4096.0, .0)),
                "left_leg" to (Pos(.0, .7, .0) to Pos(.0, -5120.0, .0))
            )
            val entities = map.map {
                it.key to createPlayerPart(
                    it.key,
                    HeadProfile(NPC.BOMBERMAN_SKIN),
                    basePos.add(it.value.first),
                    it.value.second,
                    lobby
                )
            }.toMap()
            return FakeNPC(
                entities["head"]!!,
                entities["torso"]!!,
                entities["right_arm"]!!,
                entities["left_arm"]!!,
                entities["right_leg"]!!,
                entities["left_leg"]!!
            )
        }
    }
}

private fun Pos.toBlockPos(): Pos = Pos(blockX() + .0, blockY() + .0, blockZ() + .0)