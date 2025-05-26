package world.landfall.verbatim;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import world.landfall.verbatim.command.VerbatimCommands;
import world.landfall.verbatim.discord.DiscordBot;
import world.landfall.verbatim.util.PermissionService;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.Level;

@Mod(Verbatim.MODID)
public class Verbatim {

    public static final String MODID = "verbatim";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static PermissionService permissionService;

    public Verbatim(IEventBus modEventBus, ModContainer modContainer) {
        Configurator.setLevel("world.landfall.verbatim", Level.DEBUG);
        LOGGER.info("[Verbatim] Debug logging enabled");

        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, VerbatimConfig.SPEC);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(ChatEvents.class);
        
        permissionService = new PermissionService();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Verbatim] Common setup complete.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        Verbatim.LOGGER.info("Registering Verbatim commands");
        VerbatimCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server is starting!");
        LOGGER.info("Loading chat channel configurations...");
        ChatChannelManager.loadConfiguredChannels();
        
        LOGGER.info("Initializing Discord Bot...");
        DiscordBot.init();

        LOGGER.info("[Verbatim] Permission nodes will be handled by {} (if available) or vanilla OP levels.", 
                    permissionService.isLuckPermsAvailable() ? "LuckPerms" : "vanilla OP checks");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server is stopping! Shutting down Discord Bot...");
        DiscordBot.shutdown();
    }
}
