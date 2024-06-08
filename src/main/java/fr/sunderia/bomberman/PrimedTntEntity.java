package fr.sunderia.bomberman;

import fr.sunderia.bomberman.utils.CustomDamage;
import kotlin.random.Random;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.*;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.scoreboard.Team;
import net.minestom.server.utils.PacketUtils;

/**
 * @author <a href=
 *         "https://github.com/Minestom/VanillaReimplementation/blob/e0c3e8a8c5a100522bef07f224e8c6e0671d155e/entities/src/main/java/net/minestom/vanilla/entities/item/PrimedTNTEntity.java">Minestom/VanillaReimplementation</a>
 */
public class PrimedTntEntity extends Entity {

    private int fuseTime = 80;
    // Bomb has been planted
    private final Player player;
    private final Sound tntHiss = Sound.sound(Key.key("entity.tnt.primed"), Sound.Source.BLOCK, 1f, 1f);
    private final Sound explosionSound = Sound.sound(Key.key("entity.generic.explode"), Sound.Source.BLOCK, 1f, 1f);
    private boolean pierce;
    public static Team pierceTeam;

    public Player getPlayer() {
        return player;
    }

    public PrimedTntEntity(Player player, Instance instance, Point pos) {
        super(EntityType.TNT);
        this.player = player;
        this.setInstance(instance, pos);
        setBoundingBox(1, 1, 1);
    }

    private void breakBlocks(int power, boolean isX, boolean negative) {
        Pos pos = getPosition();
        for (int x = 0; (negative ? x >= -power : x <= power); x += negative ? -1 : 1) {
            Pos newPos = pos.add(isX ? x : 0, 0, isX ? 0 : x);
            getInstance().getPlayers().stream().filter(
                    p -> p.getPosition().sameBlock(newPos) && !p.isDead() && p.getGameMode() == GameMode.ADVENTURE)
                    .forEach(player -> {
                        player.damage(new CustomDamage(this));
                        player.kill();
                    });
            getInstance().getEntities().stream()
                    .filter(e -> e.getPosition().sameBlock(newPos))
                    .filter(e -> e.getEntityType().id() == EntityType.ITEM.id() || e instanceof PrimedTntEntity)
                    .forEach(entity -> {
                        if(!(entity instanceof PrimedTntEntity tnt)) {
                            entity.remove();
                            return;
                        }
                        tnt.setFuseTime(1);
                    });
            ParticlePacket packet = new ParticlePacket(Particle.SMOKE, newPos.x() + .5,
                    newPos.y() + .5, newPos.z() + .5, 0, 0, 0, 0, 10);
            PacketUtils.sendPacket(getViewersAsAudience(), packet);
            PacketUtils.sendPacket(getViewersAsAudience(), new ParticlePacket(Particle.LAVA,
                    newPos.x(), newPos.y() + .5, newPos.z(), 0, 0, 0, 0, 10));
            int id = getInstance().getBlock(newPos).id();
            if (id != Block.AIR.id() && id != Block.BARRIER.id()) {
                if (id == Block.BRICKS.id()) {
                    getInstance().setBlock(newPos, Block.AIR);
                    dropPowerup(newPos);
                    if (getInstance().getBlock(newPos.add(0, 1, 0)).id() == Block.BARRIER.id())
                        getInstance().setBlock(newPos.add(0, 1, 0), Block.AIR);
                }
                if(!isAPierceBomb()) break;
                if(id == Block.STONE.id()) break;
            }
        }
    }

    private void dropPowerup(Pos pos) {
        if (Random.Default.nextInt(4) != 0)
            return;
        int index = Random.Default.nextInt(Powerup.values().length);
        Powerup powerup = Powerup.values()[index];
        ItemStack is = ItemStack.of(Material.NAUTILUS_SHELL).with(meta -> meta.set(ItemComponent.CUSTOM_MODEL_DATA, index + 1)
                .set(ItemComponent.ITEM_NAME, Component.text(powerup.name().replace("_", " ").toLowerCase())));
        ItemEntity item = new ItemEntity(is);
        item.setInstance(getInstance(), pos);
    }

    private void explode() {
        int power = Bomberman.Companion.getPowerMap().getOrDefault(player.getUuid(), 2);
        super.getViewersAsAudience().playSound(explosionSound);
        breakBlocks(power, true, false);
        breakBlocks(power, true, true);
        breakBlocks(power, false, false);
        breakBlocks(power, false, true);
    }

    public void setFuseTime(int fuseTime) {
        this.fuseTime = fuseTime;
    }

    @Override
    public void spawn() {
        super.getViewersAsAudience().playSound(tntHiss);
    }

    @Override
    public void update(long time) {
        super.update(time);
        if (--fuseTime != 0)
            return;
        explode();
        getInstance().setBlock(this.position, Block.AIR);
        remove();
        if(pierceTeam.getMembers().contains(this.getUuid().toString())) pierceTeam.removeMember(this.getUuid().toString());
    }

    public boolean isAPierceBomb() {
        return pierce;
    }

    public void setPierce(boolean pierce) {
        this.setGlowing(true);
        pierceTeam.addMember(this.getUuid().toString());
        this.pierce = pierce;
    }
}
