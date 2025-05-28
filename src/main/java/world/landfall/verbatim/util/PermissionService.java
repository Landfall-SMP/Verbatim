package world.landfall.verbatim.util;

import net.minecraft.server.level.ServerPlayer;
import world.landfall.verbatim.Verbatim; // For LOGGER
import java.lang.reflect.Method;
import java.util.UUID;

public class PermissionService {
    private Object luckPermsApi;
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
                // Use reflection to safely check for LuckPerms
                Class<?> luckPermsProviderClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                Method getMethod = luckPermsProviderClass.getMethod("get");
                this.luckPermsApi = getMethod.invoke(null);
                this.luckPermsAvailable = true;
                Verbatim.LOGGER.info("[Verbatim PermissionService] LuckPerms API found and loaded. Permissions will be handled by LuckPerms.");
            } catch (ClassNotFoundException e) {
                this.luckPermsApi = null;
                this.luckPermsAvailable = false;
                Verbatim.LOGGER.warn("[Verbatim PermissionService] LuckPerms classes not found. Permissions will use vanilla OP levels as fallback.");
            } catch (Exception e) {
                this.luckPermsApi = null;
                this.luckPermsAvailable = false;
                Verbatim.LOGGER.warn("[Verbatim PermissionService] LuckPerms API not available: {}. Permissions will use vanilla OP levels as fallback.", e.getMessage());
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
            Verbatim.LOGGER.warn("[Verbatim PermissionService] Attempted to check a null or empty permissionNode for player {}. Denying.", player.getName().getString());
            return false; 
        }

        ensureLuckPermsChecked();

        if (this.luckPermsAvailable && this.luckPermsApi != null) {
            try {
                Method getUserManagerMethod = this.luckPermsApi.getClass().getMethod("getUserManager");
                Object userManager = getUserManagerMethod.invoke(this.luckPermsApi);
                
                Method getUserMethod = userManager.getClass().getMethod("getUser", UUID.class);
                Object user = getUserMethod.invoke(userManager, player.getUUID());
                
                if (user != null) {
                    Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
                    Object cachedData = getCachedDataMethod.invoke(user);
                    Method getPermissionDataMethod = cachedData.getClass().getMethod("getPermissionData");
                    Object permissionData = getPermissionDataMethod.invoke(cachedData);
                    Method checkPermissionMethod = permissionData.getClass().getMethod("checkPermission", String.class);
                    Object permissionResult = checkPermissionMethod.invoke(permissionData, permissionNode);
                    Method asBooleanMethod = permissionResult.getClass().getMethod("asBoolean");
                    boolean checkResult = (Boolean) asBooleanMethod.invoke(permissionResult);
                    
                    Verbatim.LOGGER.debug("[Verbatim PermissionService] LuckPerms check for player '{}', node '{}': {} (UUID: {})", 
                                       player.getName().getString(), permissionNode, checkResult, player.getUUID());
                    return checkResult;
                } else {
                    Verbatim.LOGGER.warn("[Verbatim PermissionService] LuckPerms available, but user '{}' (UUID: {}) not found by LuckPerms. Attempting to load user...",
                                       player.getName().getString(), player.getUUID());
                    try {
                        Method loadUserMethod = userManager.getClass().getMethod("loadUser", UUID.class);
                        Object completableFuture = loadUserMethod.invoke(userManager, player.getUUID());
                        Method getMethod = completableFuture.getClass().getMethod("get"); // This can throw, e.g., InterruptedException, ExecutionException
                        Object loadedUser = getMethod.invoke(completableFuture);
                        
                        if (loadedUser != null) {
                            Method getCachedDataMethod = loadedUser.getClass().getMethod("getCachedData");
                            Object cachedData = getCachedDataMethod.invoke(loadedUser);
                            Method getPermissionDataMethod = cachedData.getClass().getMethod("getPermissionData");
                            Object permissionData = getPermissionDataMethod.invoke(cachedData);
                            Method checkPermissionMethod = permissionData.getClass().getMethod("checkPermission", String.class);
                            Object permissionResult = checkPermissionMethod.invoke(permissionData, permissionNode);
                            Method asBooleanMethod = permissionResult.getClass().getMethod("asBoolean");
                            boolean checkResult = (Boolean) asBooleanMethod.invoke(permissionResult);
                            
                            Verbatim.LOGGER.info("[Verbatim PermissionService] After loading user '{}', permission check for node '{}': {}", 
                                               player.getName().getString(), permissionNode, checkResult);
                            return checkResult;
                        } else {
                            Verbatim.LOGGER.warn("[Verbatim PermissionService] Loaded user '{}' was null. Falling back to OP check for permission '{}'.",
                                               player.getName().getString(), permissionNode);
                        }
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        if (e.getCause() instanceof IllegalStateException) {
                            Verbatim.LOGGER.warn("[Verbatim PermissionService] LuckPerms capability not available for player '{}' during user load ({}). This can happen during respawn. Falling back to OP level check.", 
                                               player.getName().getString(), e.getCause().getMessage());
                        } else {
                            Verbatim.LOGGER.error("[Verbatim PermissionService] Failed to load user '{}' from LuckPerms via reflection (InvocationTargetException): {}. Cause: {}. Falling back to OP check.", 
                                                player.getName().getString(), e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "null");
                        }
                    } catch (Exception e) { // Catch other exceptions during loadUser, like NoSuchMethod, IllegalAccess
                        Verbatim.LOGGER.error("[Verbatim PermissionService] Failed to load user '{}' from LuckPerms via reflection (General Exception): {}. Falling back to OP check.", 
                                            player.getName().getString(), e.getMessage());
                    }
                    // If user was null and loadUser failed or returned null, we fall through to OP check here
                }
            } catch (java.lang.reflect.InvocationTargetException e) {
                // Check if the cause of the InvocationTargetException is an IllegalStateException from LuckPerms. Haven't had this issue with this branch yet, but it's here if it happens.
                if (e.getCause() instanceof IllegalStateException) {
                    Verbatim.LOGGER.warn("[Verbatim PermissionService] LuckPerms capability not available for player '{}' ({}). This can happen during respawn. Falling back to OP level check.", 
                                       player.getName().getString(), e.getCause().getMessage());
                } else {
                    Verbatim.LOGGER.error("[Verbatim PermissionService] Error accessing LuckPerms API via reflection (InvocationTargetException for {}): {}. Cause: {}. Falling back to OP check.",
                                        player.getName().getString(), e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "null");
                }
            } catch (Exception e) { // Catches other reflection exceptions like NoSuchMethodException, IllegalAccessException for the main block
                Verbatim.LOGGER.error("[Verbatim PermissionService] Error accessing LuckPerms API via reflection (General Exception for {}): {}. Falling back to OP check.", 
                                    player.getName().getString(), e.getMessage());
            }
        }
        
        // Fallback to vanilla OP check if LuckPerms not available, or if any reflection/LuckPerms error occurred above
        boolean opCheckResult = player.hasPermissions(opLevelIfLuckPermsAbsent);
        Verbatim.LOGGER.info("[Verbatim PermissionService] Vanilla OP check for player '{}', level {}: {} (for permission '{}')", 
                            player.getName().getString(), opLevelIfLuckPermsAbsent, opCheckResult, permissionNode);
        return opCheckResult;
    }
} 