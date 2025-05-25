package world.landfall.verbatim.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.server.level.ServerPlayer;
import world.landfall.verbatim.Verbatim; // For LOGGER

public class PermissionService {
    private LuckPerms luckPermsApi;
    private Boolean luckPermsAvailable; // Use Boolean (nullable) to track if we've checked yet

    public PermissionService() {
        // Don't check for LuckPerms here - it might not be loaded yet
        this.luckPermsApi = null;
        this.luckPermsAvailable = null; // null = not checked yet
        Verbatim.LOGGER.info("[Verbatim PermissionService] PermissionService initialized. LuckPerms availability will be checked when first needed.");
    }

    /**
     * Lazy-load LuckPerms API. This is called on first permission check.
     */
    private void ensureLuckPermsChecked() {
        if (this.luckPermsAvailable == null) { // Haven't checked yet
            try {
                this.luckPermsApi = LuckPermsProvider.get();
                this.luckPermsAvailable = true;
                Verbatim.LOGGER.info("[Verbatim PermissionService] LuckPerms API found and loaded. Permissions will be handled by LuckPerms.");
            } catch (IllegalStateException e) {
                this.luckPermsApi = null;
                this.luckPermsAvailable = false;
                Verbatim.LOGGER.info("[Verbatim PermissionService] LuckPerms API not found. Permissions will use vanilla OP levels as fallback.");
            }
        }
    }

    public boolean isLuckPermsAvailable() {
        ensureLuckPermsChecked();
        return this.luckPermsAvailable;
    }

    /**
     * Checks if a player has a specific permission node.
     *
     * @param player The player to check. Can be null, in which case permission is denied.
     * @param permissionNode The permission node string (e.g., "verbatim.channel.staff"). Can be null or empty, in which case permission is effectively denied unless handled by caller.
     * @param opLevelIfLuckPermsAbsent The vanilla OP level (0-4) to check if LuckPerms is not loaded.
     * @return True if the player has the permission, false otherwise.
     */
    public boolean hasPermission(ServerPlayer player, String permissionNode, int opLevelIfLuckPermsAbsent) {
        if (player == null) {
            Verbatim.LOGGER.warn("[Verbatim PermissionService] Attempted to check permission for a null player. Denying.");
            return false;
        }
        if (permissionNode == null || permissionNode.isEmpty()) {
            Verbatim.LOGGER.warn("[Verbatim PermissionService] Attempted to check a null or empty permission node for player {}. Denying.", player.getName().getString());
            return false; 
        }

        // Lazy-check for LuckPerms availability
        ensureLuckPermsChecked();

        if (this.luckPermsAvailable && this.luckPermsApi != null) {
            User user = this.luckPermsApi.getUserManager().getUser(player.getUUID());
            if (user != null) {
                boolean checkResult = user.getCachedData().getPermissionData().checkPermission(permissionNode).asBoolean();
                Verbatim.LOGGER.info("[Verbatim PermissionService] LuckPerms check for player '{}', node '{}': {} (UUID: {})", 
                                   player.getName().getString(), permissionNode, checkResult, player.getUUID());
                
                // Additional debugging: Check if user has ANY permissions
                if (!checkResult) {
                    Verbatim.LOGGER.info("[Verbatim PermissionService] Permission denied. User's effective permissions: {}", 
                                       user.getCachedData().getPermissionData().getPermissionMap().keySet());
                }
                
                return checkResult;
            } else {
                Verbatim.LOGGER.warn("[Verbatim PermissionService] LuckPerms available, but user '{}' (UUID: {}) not found by LuckPerms. " +
                                   "This might happen if the user just joined. Attempting to load user...",
                                   player.getName().getString(), player.getUUID());
                
                // Try to load the user synchronously (this might block briefly)
                try {
                    User loadedUser = this.luckPermsApi.getUserManager().loadUser(player.getUUID()).get();
                    if (loadedUser != null) {
                        boolean checkResult = loadedUser.getCachedData().getPermissionData().checkPermission(permissionNode).asBoolean();
                        Verbatim.LOGGER.info("[Verbatim PermissionService] After loading user '{}', permission check for node '{}': {}", 
                                           player.getName().getString(), permissionNode, checkResult);
                        return checkResult;
                    }
                } catch (Exception e) {
                    Verbatim.LOGGER.error("[Verbatim PermissionService] Failed to load user '{}' from LuckPerms: {}", 
                                        player.getName().getString(), e.getMessage());
                }
                
                // If we still can't load the user, deny permission
                Verbatim.LOGGER.warn("[Verbatim PermissionService] Could not load user '{}' from LuckPerms. Denying permission '{}'.", 
                                   player.getName().getString(), permissionNode);
                return false; 
            }
        } else {
            // LuckPerms is not available, fallback to vanilla OP check.
            boolean opCheckResult = player.hasPermissions(opLevelIfLuckPermsAbsent);
            Verbatim.LOGGER.info("[Verbatim PermissionService] LuckPerms not available. Vanilla OP check for player '{}', level {}: {} (for permission '{}')", 
                                player.getName().getString(), opLevelIfLuckPermsAbsent, opCheckResult, permissionNode);
            return opCheckResult;
        }
    }
} 