package com.mrsuffix.fishmating.utils;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Checks whether a real player is near an entity, used to gate breeding activity on
 * player presence so fish don't seek seeds or breed in unattended (but loaded) chunks.
 */
public final class PlayerProximityUtil {

    private PlayerProximityUtil() {
    }

    /**
     * @param entity the entity to measure from
     * @param radius required player radius in blocks; {@code <= 0} disables the check
     * @return {@code true} if the check is disabled ({@code radius <= 0}) or a non-spectator
     *         player is within {@code radius} blocks of the entity in the same world
     */
    public static boolean playerWithin(Entity entity, double radius) {
        if (radius <= 0) {
            return true; // check disabled
        }
        double radiusSq = radius * radius;
        // Read raw coordinates rather than getLocation() to avoid allocating a Location
        // per entity/player on this per-fish, per-tick path. getWorld().getPlayers() is
        // same-world only, so squared distance is safe and skips the per-player sqrt.
        double ex = entity.getX();
        double ey = entity.getY();
        double ez = entity.getZ();
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double dx = player.getX() - ex;
            double dy = player.getY() - ey;
            double dz = player.getZ() - ez;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                return true;
            }
        }
        return false;
    }
}
