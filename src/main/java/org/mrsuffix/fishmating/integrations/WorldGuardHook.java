package com.mrsuffix.fishmating.integrations;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;

import java.util.logging.Logger;

/**
 * The single class that touches the WorldGuard API. Every {@code com.sk89q.*} reference is
 * confined here, so the JVM only links these types when this class is actually used — which
 * the rest of the plugin only does behind a {@code getPlugin("WorldGuard") != null} guard.
 * That keeps FishMating loading cleanly when WorldGuard is absent.
 *
 * <p>The custom {@code allow-fish-breeding} {@link StateFlag} defaults to {@code ALLOW}, so
 * breeding works everywhere unless a region explicitly sets it to {@code DENY}.
 */
public final class WorldGuardHook {

    /** Region flag name; admins set it via {@code /rg flag <region> allow-fish-breeding deny}. */
    private static final String FLAG_NAME = "allow-fish-breeding";

    private static StateFlag breedingFlag;
    private static boolean registered;

    private WorldGuardHook() {
    }

    /**
     * Registers the {@code allow-fish-breeding} flag. MUST be called from the plugin's
     * {@code onLoad()} (before WorldGuard enables): the flag registry locks once WorldGuard
     * is enabled. Only call this when WorldGuard is present on the server.
     *
     * @param log the plugin logger, for reporting a conflict
     */
    public static void registerFlag(Logger log) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag(FLAG_NAME, true); // default = ALLOW
            registry.register(flag);
            breedingFlag = flag;
            registered = true;
        } catch (FlagConflictException e) {
            // Another plugin already registered this name (or we did, e.g. on /reload).
            // Reuse the existing flag if it's a StateFlag; otherwise leave the hook off.
            Flag<?> existing = registry.get(FLAG_NAME);
            if (existing instanceof StateFlag stateFlag) {
                breedingFlag = stateFlag;
                registered = true;
            } else {
                log.warning("WorldGuard flag '" + FLAG_NAME + "' is registered as a different "
                        + "type; FishMating's WorldGuard integration is disabled.");
            }
        }
    }

    /** @return whether the flag was registered/resolved and the integration can query it. */
    public static boolean isAvailable() {
        return registered && breedingFlag != null;
    }

    /**
     * @param location the location where a baby would spawn
     * @return {@code true} if breeding is allowed there. Fails open: returns {@code true}
     *         when the integration is unavailable, the location is unusable, or the query
     *         throws — so a WorldGuard hiccup never silently blocks breeding everywhere.
     */
    public static boolean isBreedingAllowed(Location location) {
        if (!isAvailable() || location == null || location.getWorld() == null) {
            return true; // not applicable
        }
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            // (RegionAssociable) null: a system action with no owner/member association.
            StateFlag.State state = query.queryState(
                    BukkitAdapter.adapt(location), (RegionAssociable) null, breedingFlag);
            boolean explicitlyDenied = state == StateFlag.State.DENY;
            // integrationEnabled/worldGuardPresent are already true here (caller-gated).
            return BreedingFlagDecision.allowed(true, true, explicitlyDenied);
        } catch (Exception e) {
            return true; // fail open
        }
    }
}
