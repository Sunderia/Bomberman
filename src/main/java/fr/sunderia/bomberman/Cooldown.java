package fr.sunderia.bomberman;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Cooldown {
    
    private static final Map<String, Cooldown> cooldowns = new HashMap<>();
    private long start;
    private final int timeInSeconds;
    private final UUID id;
    private final String cooldownName;

    /**
     *
     * @param id Player's UUID {@link org.bukkit.entity.Player#getUniqueId()}
     * @param cooldownName Name of the cooldown
     * @param timeInSeconds Time in seconds
     */
    public Cooldown(UUID id, String cooldownName, int timeInSeconds) {
        this.id = id;
        this.cooldownName = cooldownName;
        this.timeInSeconds = timeInSeconds;
    }

    /**
     * @param id Player's UUID {@link org.bukkit.entity.Player#getUniqueId()}
     * @param cooldownName Name of the cooldown
     * @return True if the cooldown is running
     */
    public static boolean isInCooldown(UUID id, String cooldownName) {
        if(getTimeLeft(id, cooldownName) >= 1) {
            return true;
        } else {
            stop(id, cooldownName);
            return false;
        }
    }

    /**
     * Stop the cooldown
     * @param id Player's UUID {@link org.bukkit.entity.Player#getUniqueId()}
     * @param cooldownName Name of the cooldown
     */
    private static void stop(UUID id, String cooldownName) {
        Cooldown.cooldowns.remove(id + cooldownName);
    }

    /**
     * @param id The player's UUID {@link org.bukkit.entity.Player#getUniqueId()}
     * @param cooldownName The name of the cooldown
     * @return An instance of {@link Cooldown} from {@link Cooldown#cooldowns}
     * @throws NullPointerException if the cooldown doesn't exist
     */
    private static Cooldown getCooldown(UUID id, String cooldownName) {
        return cooldowns.get(id + cooldownName);
    }

    /**
     * @param id Player's UUID {@link org.bukkit.entity.Player#getUniqueId()}
     * @param cooldownName Name of the cooldown
     * @return Time left in seconds
     */
    public static int getTimeLeft(UUID id, String cooldownName) {
        Cooldown cooldown = getCooldown(id, cooldownName);
        int f = -1;
        if(cooldown != null) {
            long now = System.currentTimeMillis();
            int r = (int) (now - cooldown.start) / 1000;
            f = (r - cooldown.timeInSeconds) * (-1);
        }
        return f;
    }

    /**
     * Start the cooldown
     */
    public void start() {
        this.start = System.currentTimeMillis();
        cooldowns.put(this.id.toString() + this.cooldownName, this);
    }
}