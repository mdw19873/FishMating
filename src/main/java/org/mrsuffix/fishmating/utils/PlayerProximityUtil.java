package com.mrsuffix.fishmating.utils;

import org.bukkit.GameMode;
import org.bukkit.Location;
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
        Location origin = entity.getLocation();
        // Same-world players only; distanceSquared avoids the per-player sqrt and never
        // throws (a cross-world comparison can't happen here).
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(origin) <= radiusSq) {
                return true;
            }
        }
        return false;
    }
}
