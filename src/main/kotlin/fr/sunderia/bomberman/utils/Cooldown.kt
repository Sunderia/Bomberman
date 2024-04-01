package fr.sunderia.bomberman.utils

import java.util.*

/**
 *
 * @param id Player's UUID [net.minestom.server.entity.Entity.getUuid]
 * @param cooldownName Name of the cooldown
 * @param timeInSeconds Time in seconds
 */
class Cooldown(private val id: UUID, private val cooldownName: String, private val timeInSeconds: Int) {
    private var start: Long = 0

    /**
     * Start the cooldown
     */
    fun start() {
        start = System.currentTimeMillis()
        cooldowns[id.toString() + cooldownName] = this
    }

    companion object {
        private val cooldowns: MutableMap<String, Cooldown> = HashMap()

        /**
         * @param id Player's UUID [net.minestom.server.entity.Entity.getUuid]
         * @param cooldownName Name of the cooldown
         * @return True if the cooldown is running
         */
        fun isInCooldown(id: UUID, cooldownName: String): Boolean {
            return if (getTimeLeft(id, cooldownName) >= 1) {
                true
            } else {
                stop(id, cooldownName)
                false
            }
        }

        /**
         * Stop the cooldown
         * @param id Player's UUID [net.minestom.server.entity.Entity.getUuid]
         * @param cooldownName Name of the cooldown
         */
        private fun stop(id: UUID, cooldownName: String) {
            cooldowns.remove(id.toString() + cooldownName)
        }

        /**
         * @param id The player's UUID [net.minestom.server.entity.Entity.getUuid]
         * @param cooldownName The name of the cooldown
         * @return An instance of [Cooldown] from [Cooldown.cooldowns]
         * @throws NullPointerException if the cooldown doesn't exist
         */
        private fun getCooldown(id: UUID, cooldownName: String): Cooldown? {
            return cooldowns[id.toString() + cooldownName]
        }

        /**
         * @param id Player's UUID [net.minestom.server.entity.Entity.getUuid]
         * @param cooldownName Name of the cooldown
         * @return Time left in seconds
         */
        fun getTimeLeft(id: UUID, cooldownName: String): Int {
            val cooldown = getCooldown(id, cooldownName)
            var f = -1
            if (cooldown != null) {
                val now = System.currentTimeMillis()
                val r = (now - cooldown.start).toInt() / 1000
                f = (r - cooldown.timeInSeconds) * -1
            }
            return f
        }
    }
}