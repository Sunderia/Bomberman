package fr.sunderia.bomberman;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.jglrxavpok.hephaistos.nbt.NBTException;
import org.jglrxavpok.hephaistos.nbt.NBTReader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fr.sunderia.bomberman.Structure.BlockPos;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PickupItemEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.extensions.Extension;
import net.minestom.server.extras.lan.OpenToLAN;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.batch.AbsoluteBlockBatch;
import net.minestom.server.instance.batch.ChunkBatch;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.network.packet.server.play.SetCooldownPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.particle.ParticleCreator;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.utils.PacketUtils;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.world.DimensionType;

public class Bomberman extends Extension {

    private static final Map<UUID, Integer> powerMap = new HashMap<>();
    private final Gson gson = new GsonBuilder().create();
    private final Random random = new Random();

    private enum Powerup {
        //TODO: Add min and max
        FIRE_UP(p -> powerMap.put(p.getUuid(), powerMap.get(p.getUuid()) + 1)),
        FULL_FIRE(p -> powerMap.put(p.getUuid(), 8)),
        FIRE_DOWN(p -> powerMap.put(p.getUuid(), powerMap.get(p.getUuid()) - 1)),
        SPEED_UP(p -> p.addEffect(new Potion(PotionEffect.SPEED, (byte) (p.getActiveEffects().stream().filter(e -> e.getPotion().effect().id() == PotionEffect.SPEED.id()).findFirst().map(e -> e.getPotion().amplifier()).orElse((byte) 0) + 1), Integer.MAX_VALUE))),
        SPEED_DOWN(p -> p.removeEffect(PotionEffect.SPEED)),
        ;

        private final Consumer<Player> effect;

        Powerup(Consumer<Player> effect) {
            this.effect = effect;
        }

        public Consumer<Player> getEffect() {
            return effect;
        }
    }

    /**
    * @author <a href="https://github.com/Minestom/VanillaReimplementation/blob/e0c3e8a8c5a100522bef07f224e8c6e0671d155e/entities/src/main/java/net/minestom/vanilla/entities/item/PrimedTNTEntity.java">Minestom/VanillaReimplementation</a>
    */
    public class PrimedTntEntity extends Entity {

        private int fuseTime = 80;
        //Bomb has been planted
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
            for(int x = 0; (negative ? x >= -power : x <= power); x+= negative ? -1 : 1) {
                Pos newPos = pos.add(isX ? x : 0, 0, isX ? 0 : x);
                getInstance().getPlayers().stream().filter(p -> p.getPosition().sameBlock(newPos) && !p.isDead() && p.getGameMode() == GameMode.ADVENTURE).forEach(player -> {
                    player.sendMessage("You were killed by " + this.player.getUsername());
                    player.damage(DamageType.fromPlayer(this.player), 1f);
                    player.kill();
                });
                ParticlePacket packet = ParticleCreator.createParticlePacket(Particle.SMOKE, newPos.x() + .5, newPos.y() + .5, newPos.z() + .5, 0, 0, 0, 10);
                PacketUtils.sendPacket(getViewersAsAudience(), packet);
                PacketUtils.sendPacket(getViewersAsAudience(), ParticleCreator.createParticlePacket(Particle.LAVA, newPos.x() + .5, newPos.y() + .5, newPos.z() + .5, 0, 0, 0, 10));
                int id = getInstance().getBlock(newPos).id();
                if(id != Block.AIR.id() && id != Block.BARRIER.id()) {
                    if(id == Block.BRICKS.id()) {
                        getInstance().setBlock(newPos, Block.AIR);
                        dropPowerup(newPos);
                        if(getInstance().getBlock(newPos.add(0, 1, 0)).id() == Block.BARRIER.id()) getInstance().setBlock(newPos.add(0, 1, 0), Block.AIR);
                    }
                    break;
                }
            }
        }

        private void dropPowerup(Pos pos) {
            if(random.nextInt(4) != 0) return;
            int index = random.nextInt(Powerup.values().length);
            Powerup powerup = Powerup.values()[index];
            ItemStack is = ItemStack.of(Material.NAUTILUS_SHELL).withMeta(meta ->
                meta.customModelData(index + 1).displayName(Component.text(powerup.name().replace("_", " ").toLowerCase())));
            ItemEntity item = new ItemEntity(is);
            item.setInstance(getInstance(), pos);
        }

        private void explode() {
            int power = powerMap.getOrDefault(player.getUuid(), 2);
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
            if(--fuseTime != 0) return;
            explode();
            getInstance().setBlock(this.spawnPos, Block.AIR);
            remove();
        }
    }

    @Override
    public void initialize() {
        InstanceManager manager = MinecraftServer.getInstanceManager();
        InstanceContainer container = createInstanceContainer(manager);
        var extensionNode = getEventNode();
        OpenToLAN.open();
        registerListeners(extensionNode, container);
    }

    private void registerListeners(EventNode<Event> extensionNode, InstanceContainer container) {     
        extensionNode.addListener(PlayerLoginEvent.class, event -> event.setSpawningInstance(container));
        extensionNode.addListener(PlayerSpawnEvent.class, e -> {
            Player player = e.getPlayer();
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(player.getPosition().withY(45));
            player.setRespawnPoint(player.getPosition().withY(45));
            player.getInventory().addItemStack(ItemStack.of(Material.TNT).withMeta(builder -> builder.canPlaceOn(Block.STONE, Block.BRICKS).build()));
            powerMap.put(player.getUuid(), 2);
        });

        extensionNode.addListener(PlayerChatEvent.class, e -> {
            Optional<GameMode> g = Arrays.stream(GameMode.values()).filter(gm -> e.getMessage().toUpperCase().contains(gm.name())).findFirst();
            g.ifPresent(gamemode -> {
                e.setCancelled(true);
                e.getPlayer().setGameMode(gamemode);
            });
        });

        extensionNode.addListener(PlayerDeathEvent.class, event -> {
            Player player = event.getPlayer();
            player.setGameMode(GameMode.SPECTATOR);
            List<Player> playerAlives = player.getInstance().getPlayers().stream().filter(p -> p.getGameMode() == GameMode.ADVENTURE).toList();
            if(playerAlives.size() != 1) return;
            Player winner = playerAlives.get(0);
            player.getInstance().getPlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.ADVENTURE).forEach(p -> {
                    if(p.isDead()) p.respawn();
                    p.sendMessage(Component.text(winner.getUsername() + " Won"));
                    p.teleport(new Pos(0, 45, 0));
                    p.setGameMode(GameMode.ADVENTURE);
                });
            winner.sendMessage("You won");
            winner.teleport(new Pos(0, 45, 0));
            generateStructure(winner.getInstance());
            resetGame(winner.getInstance());
        });

        extensionNode.addListener(PlayerBlockPlaceEvent.class, e -> {
            if(e.getBlock().id() != Block.TNT.id()) return;
            e.setCancelled(true);
            Player player = e.getPlayer();
            if(player.getGameMode() != GameMode.ADVENTURE) return;
            Block blockBelow = player.getInstance().getBlock(e.getBlockPosition().sub(0, 1, 0));
            if(blockBelow.id() != Block.STONE.id() || player.getInstance().getBlock(e.getBlockPosition().add(0, 1, 0)).isSolid()) return;
            if(Cooldown.isInCooldown(e.getPlayer().getUuid(), "tnt")) return;
            e.setCancelled(false);
            e.getPlayer().sendPacket(new SetCooldownPacket(Material.TNT.id(), 0));
            e.consumeBlock(false);
            e.setBlock(Block.BARRIER);
            final int timeInSeconds = 1;
            Cooldown c = new Cooldown(e.getPlayer().getUuid(), "tnt", timeInSeconds);
            c.start();
            SetCooldownPacket packet = new SetCooldownPacket(Material.TNT.id(), timeInSeconds * 20);
            PrimedTntEntity tnt = new PrimedTntEntity(e.getPlayer());
            tnt.setInstance(e.getInstance(), e.getBlockPosition().add(0.5d, 0, 0.5d));
            e.getPlayer().sendPacket(packet);
        });

        extensionNode.addListener(PickupItemEvent.class, event -> {
            if(!(event.getLivingEntity() instanceof Player player) || event.getItemStack().material().id() != Material.NAUTILUS_SHELL.id()) return;
            int customModelData = event.getItemStack().meta().getCustomModelData();
            Powerup powerup = Powerup.values()[customModelData - 1];
            powerup.getEffect().accept(player);
            player.sendMessage("You just got a " + powerup.name() + ". Your current explosion power is " + powerMap.get(player.getUuid()) + " blocks");
        });
    }
    
    private InstanceContainer createInstanceContainer(InstanceManager manager) {
        DimensionType fullBright = DimensionType.builder(NamespaceID.from("sunderia:full_bright")).ambientLight(2.0f).build();
        MinecraftServer.getDimensionTypeManager().addDimension(fullBright);
        InstanceContainer container = manager.createInstanceContainer(fullBright);
        container.setGenerator(unit -> {
            unit.modifier().fillHeight(0, 40, Block.STONE);
        });
        generateStructure(container);
        return container;
    }

    private void resetGame(Instance instance) {
        powerMap.replaceAll((k,v) -> 2);
        instance.getPlayers().forEach(Player::clearEffects);
    }

    private void generateStructure(Instance container) {
        Structure blocks = parseNBT();
        if(blocks == null) return;
        Pos startPos = new Pos(0, 0, 0).sub(blocks.size().div(2)).withY(40);
        AbsoluteBlockBatch batch = new AbsoluteBlockBatch();
        for(BlockPos blockPos : blocks.blocks()) {
            if(blockPos.block().isAir()) continue;
            if(blockPos.block().id() == Block.BRICKS.id() && random.nextInt(3) == 2) continue;
            batch.setBlock(startPos.add(blockPos.vec()), blockPos.block());
            if(blockPos.block().id() == Block.BRICKS.id()) {
                batch.setBlock(startPos.add(blockPos.vec().add(0, 1, 0)), Block.BARRIER);
            }
        }
        ChunkUtils.optionalLoadAll(container, getAffectedChunks(batch), null)
            .thenRun(() -> batch.apply(container, () -> batch.clear()));
    }

    private Structure parseNBT() {
        InputStream stream = Bomberman.class.getResourceAsStream("/bomberman.nbt");
        try(NBTReader reader = new NBTReader(stream)) {
        List<BlockPos> structure = new LinkedList<>();
            JsonObject nbt = gson.fromJson(reader.read().toSNBT(), JsonObject.class);
            JsonArray palettes = nbt.getAsJsonArray("palette");
            Block[] palette = IntStream.range(0, palettes.size())
                .mapToObj(palettes::get).map(JsonElement::getAsJsonObject)
                .map(obj -> obj.getAsJsonPrimitive("Name").getAsString())
                .map(Block::fromNamespaceId).toArray(Block[]::new);
            JsonArray blockArray = nbt.getAsJsonArray("blocks");
            blockArray.forEach(el -> {
                JsonObject blockObj = el.getAsJsonObject();
                JsonArray jsonPos = blockObj.getAsJsonArray("pos");
                structure.add(new BlockPos(
                    new Vec(jsonPos.get(0).getAsInt(), jsonPos.get(1).getAsInt(), jsonPos.get(2).getAsInt()),
                    palette[blockObj.get("state").getAsInt()])
                );
            });
            int[] size = new int[3];
            JsonArray jsonSize = nbt.getAsJsonArray("size");
            for(int i = 0; i < 3; i++) size[i] = jsonSize.get(i).getAsInt();
            return new Structure(new Vec(jsonSize.get(0).getAsInt(), jsonSize.get(1).getAsInt(), jsonSize.get(2).getAsInt()), structure);
        } catch(IOException | NBTException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static long[] getAffectedChunks(AbsoluteBlockBatch batch) {
        try {
            Field field = batch.getClass().getDeclaredField("chunkBatchesMap");
            field.setAccessible(true);

            Long2ObjectMap<ChunkBatch> chunkBatchesMap = (Long2ObjectMap<ChunkBatch>) field.get(batch);

            return chunkBatchesMap.keySet().toLongArray();

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void terminate() {
        
    }
    
}
