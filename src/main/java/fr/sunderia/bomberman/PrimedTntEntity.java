package fr.sunderia.bomberman;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.particle.ParticleCreator;
import net.minestom.server.utils.PacketUtils;

import static fr.sunderia.bomberman.Bomberman.random;

/**
 * @author <a href=
 *         "https://github.com/Minestom/VanillaReimplementation/blob/e0c3e8a8c5a100522bef07f224e8c6e0671d155e/entities/src/main/java/net/minestom/vanilla/entities/item/PrimedTNTEntity.java">Minestom/VanillaReimplementation</a>
 */
public class PrimedTntEntity extends Entity {

    private int fuseTime = 80;
    // Bomb has been planted
    private Player player;
    private final Sound tntHiss = Sound.sound(Key.key("entity.tnt.primed"), Sound.Source.BLOCK, 1f, 1f);
    private final Sound explosionSound = Sound.sound(Key.key("entity.generic.explode"), Sound.Source.BLOCK, 1f, 1f);
    private Pos spawnPos;

    public PrimedTntEntity(Player player) {
        super(EntityType.TNT);
        this.player = player;
        setGravity(.3f, getGravityAcceleration());
        setBoundingBox(1, 1, 1);
    }

    private void breakBlocks(int power, boolean isX, boolean negative) {
        Pos pos = getPosition();
        for (int x = 0; (negative ? x >= -power : x <= power); x += negative ? -1 : 1) {
            Pos newPos = pos.add(isX ? x : 0, 0, isX ? 0 : x);
            getInstance().getPlayers().stream().filter(
                    p -> p.getPosition().sameBlock(newPos) && !p.isDead() && p.getGameMode() == GameMode.ADVENTURE)
                    .forEach(player -> {
                        player.sendMessage("You were killed by " + this.player.getUsername());
                        player.damage(DamageType.fromPlayer(this.player), 1f);
                        player.kill();
                    });
            ParticlePacket packet = ParticleCreator.createParticlePacket(Particle.SMOKE, newPos.x() + .5,
                    newPos.y() + .5, newPos.z() + .5, 0, 0, 0, 10);
            PacketUtils.sendPacket(getViewersAsAudience(), packet);
            PacketUtils.sendPacket(getViewersAsAudience(), ParticleCreator.createParticlePacket(Particle.LAVA,
                    newPos.x() + .5, newPos.y() + .5, newPos.z() + .5, 0, 0, 0, 10));
            int id = getInstance().getBlock(newPos).id();
            if (id != Block.AIR.id() && id != Block.BARRIER.id()) {
                if (id == Block.BRICKS.id()) {
                    getInstance().setBlock(newPos, Block.AIR);
                    dropPowerup(newPos);
                    if (getInstance().getBlock(newPos.add(0, 1, 0)).id() == Block.BARRIER.id())
                        getInstance().setBlock(newPos.add(0, 1, 0), Block.AIR);
                }
                break;
            }
        }
    }

    private void dropPowerup(Pos pos) {
        if (random.nextInt(4) != 0)
            return;
        int index = random.nextInt(Powerup.values().length);
        Powerup powerup = Powerup.values()[index];
        ItemStack is = ItemStack.of(Material.NAUTILUS_SHELL).withMeta(meta -> meta.customModelData(index + 1)
                .displayName(Component.text(powerup.name().replace("_", " ").toLowerCase())));
        ItemEntity item = new ItemEntity(is);
        item.setInstance(getInstance(), pos);
    }

    private void explode() {
        int power = Bomberman.powerMap.getOrDefault(player.getUuid(), 2);
        super.getViewersAsAudience().playSound(explosionSound);
        breakBlocks(power, true, false);
        breakBlocks(power, true, true);
        breakBlocks(power, false, false);
        breakBlocks(power, false, true);
    }

    @Override
    public void spawn() {
        super.getViewersAsAudience().playSound(tntHiss);
        this.spawnPos = getPosition();
    }

    @Override
    public void update(long time) {
        super.update(time);
        if (--fuseTime != 0)
            return;
        explode();
        getInstance().setBlock(this.spawnPos, Block.AIR);
        remove();
    }
}
