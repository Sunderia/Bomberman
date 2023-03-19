package fr.sunderia.bomberman;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import org.apache.commons.codec.digest.DigestUtils;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.TitlePart;
import net.minestom.server.MinecraftServer;
import net.minestom.server.attribute.Attribute;
import net.minestom.server.attribute.AttributeModifier;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
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
import net.minestom.server.network.packet.server.play.SetCooldownPacket;
import net.minestom.server.resourcepack.ResourcePack;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.world.DimensionType;

public class Bomberman extends Extension {

    static final Map<UUID, Integer> powerMap = new HashMap<>();
    private final Gson gson = new GsonBuilder().create();
    public static final Random random = new Random();
    private String resourcePackSha1;
    private final String resourcePackURL = "https://raw.githubusercontent.com/Sunderia/Bomberman/main/bomberman.zip";

    @Override
    public void initialize() {
        InstanceManager manager = MinecraftServer.getInstanceManager();
        InstanceContainer container = createInstanceContainer(manager);
        var extensionNode = getEventNode();
        OpenToLAN.open();
        registerListeners(extensionNode, container);
        String sha1 = null;
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            URI.create(resourcePackURL).toURL().openStream().transferTo(os);
            sha1 = new String(DigestUtils.getSha1Digest().digest(os.toByteArray()));
        } catch(IOException e) {
            getLogger().error(Component.text("Couldn't get the SHA1 of the Resource Pack"), e);
        }
        this.resourcePackSha1 = sha1;
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
            player.scheduler()
                .scheduleTask(() -> player.sendActionBar(Component.join(JoinConfiguration.separator(Component.text(" ")),
                    Component.text("\uE000").style(b -> b.font(Key.key("bomberman", "font"))),
                    Component.text(": " + powerMap.get(player.getUuid())))
                        .style(b -> b.font(Key.key("default")))),
                TaskSchedule.immediate(), TaskSchedule.tick(10));
            if(resourcePackSha1 != null) player.setResourcePack(ResourcePack.forced("https://raw.githubusercontent.com/Sunderia/Bomberman/main/bomberman.zip", null));
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
            winner.sendTitlePart(TitlePart.TITLE, Component.text("You won", NamedTextColor.GREEN));
            winner.teleport(new Pos(0, 45, 0));
            generateStructure(winner.getInstance());
            resetGame(winner.getInstance());
        });

        extensionNode.addListener(PlayerBlockPlaceEvent.class, e -> {
            if(e.getBlock().id() != Block.TNT.id()) return;
            e.setCancelled(true);
            Player player = e.getPlayer();
            if(player.getGameMode() != GameMode.ADVENTURE && player.getGameMode() != GameMode.CREATIVE) return;
            Block blockBelow = player.getInstance().getBlock(e.getBlockPosition().sub(0, 1, 0));
            if(blockBelow.id() != Block.STONE.id() || player.getInstance().getBlock(e.getBlockPosition().add(0, 1, 0)).isSolid()) return;
            if(Cooldown.isInCooldown(e.getPlayer().getUuid(), "tnt")) return;
            e.setCancelled(false);
            e.getPlayer().sendPacket(new SetCooldownPacket(Material.TNT.id(), 0));
            e.consumeBlock(false);
            e.setBlock(Block.BARRIER);
            if(player.getGameMode() == GameMode.ADVENTURE) {
                final int timeInSeconds = 1;
                Cooldown c = new Cooldown(e.getPlayer().getUuid(), "tnt", timeInSeconds);
                c.start();
                SetCooldownPacket packet = new SetCooldownPacket(Material.TNT.id(), timeInSeconds * 20);
                e.getPlayer().sendPacket(packet);
            }
            PrimedTntEntity tnt = new PrimedTntEntity(e.getPlayer());
            tnt.setInstance(e.getInstance(), e.getBlockPosition().add(0.5d, 0, 0.5d));
        });

        extensionNode.addListener(PickupItemEvent.class, event -> {
            if(!(event.getLivingEntity() instanceof Player player) || event.getItemStack().material().id() != Material.NAUTILUS_SHELL.id()) return;
            if(player.getGameMode() != GameMode.ADVENTURE) {
                event.setCancelled(true);
                return;
            }
            int customModelData = event.getItemStack().meta().getCustomModelData();
            Powerup powerup = Powerup.values()[customModelData - 1];
            powerup.getEffect().accept(player);
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
        instance.getPlayers().forEach(p -> {
            p.clearEffects();
            UUID[] uuids = p.getAttribute(Attribute.MOVEMENT_SPEED).getModifiers().stream().map(AttributeModifier::getId).toArray(UUID[]::new);
            for(UUID uuid : uuids) {
                p.getAttribute(Attribute.MOVEMENT_SPEED).removeModifier(uuid);
            }
        });
        instance.getEntities().stream()
            .filter(e -> e.getEntityType().id() == EntityType.TNT.id() || e.getEntityType().id() == EntityType.ITEM.id())
            .forEach(Entity::remove);
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
    public void terminate() {}    
}
