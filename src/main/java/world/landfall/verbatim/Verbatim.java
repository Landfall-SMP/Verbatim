package world.landfall.verbatim;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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

    @SuppressWarnings("removal")
    public Verbatim() {
        Configurator.setLevel("world.landfall.verbatim", Level.DEBUG);
        LOGGER.info("[Verbatim] Debug logging enabled");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, VerbatimConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        
        permissionService = new PermissionService();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Verbatim] Common setup complete.");
    }

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
