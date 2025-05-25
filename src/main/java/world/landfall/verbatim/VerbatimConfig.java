package world.landfall.verbatim;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.ArrayList;

public class VerbatimConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>> CHANNELS;
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_CHANNEL_NAME;

    static {
        BUILDER.push("Verbatim Mod Configuration");

        DEFAULT_CHANNEL_NAME = BUILDER.comment(
            "Default channel name for players when they first log in, or if their saved channel is invalid.",
            "This name MUST correspond to one of the defined channels below."
        ).define("defaultChannelName", "global");

        BUILDER.comment(
            "Channel definitions. Each channel is an object with the following properties:",
            "  name: String - The internal name of the channel (e.g., \"global\", \"local\"). Unique.",
            "  displayPrefix: String - The prefix shown in chat (e.g., \"&a[G]\"). Supports & color codes.",
            "  shortcut: String - The shortcut typed by users (e.g., \"g\", \"l\", \"s\"). Unique and case-sensitive.",
            "  permission: String (optional) - Permission node required. Empty or omit for no permission.",
            "  range: Integer (optional) - Chat range in blocks for local channels. Use -1 or omit for global/non-ranged channels.",
            "  nameColor: String (optional) - Color for player names (e.g., \"&e\"). Defaults to channel's messageColor if omitted, or white if that's also omitted.",
            "  separator: String (optional) - Separator between name and message (e.g., \" » \"). Defaults to \": \".",
            "  separatorColor: String (optional) - Color for the separator (e.g., \"&7\"). Defaults to channel's messageColor if omitted, or white.",
            "  messageColor: String (optional) - Color for the message content (e.g., \"&f\"). Defaults to white.",
            "  alwaysOn: Boolean (optional) - If true, players cannot '/channel leave' this channel. Defaults to false.",
            "  specialChannelType: String (optional) - Special behavior type (e.g., \"local\" for roleplay features). Defaults to none."
        );

        Supplier<List<? extends UnmodifiableConfig>> defaultChannelsSupplier = () -> {
            List<CommentedConfig> defaults = new ArrayList<>();

            CommentedConfig globalChannel = TomlFormat.newConfig();
            globalChannel.set("name", "global");
            globalChannel.set("displayPrefix", "&a[G]");
            globalChannel.set("shortcut", "g");
            globalChannel.set("permission", "");
            globalChannel.set("range", -1);
            globalChannel.set("nameColor", "&e");
            globalChannel.set("separator", " » ");
            globalChannel.set("separatorColor", "&7");
            globalChannel.set("messageColor", "&f");
            globalChannel.set("alwaysOn", true);
            defaults.add(globalChannel);

            CommentedConfig localChannel = TomlFormat.newConfig();
            localChannel.set("name", "local");
            localChannel.set("displayPrefix", "&b[L]");
            localChannel.set("shortcut", "l");
            localChannel.set("permission", "");
            localChannel.set("range", 100);
            localChannel.set("nameColor", "&e");
            localChannel.set("separator", " » ");
            localChannel.set("separatorColor", "&7");
            localChannel.set("messageColor", "&7");
            localChannel.set("alwaysOn", true);
            localChannel.set("specialChannelType", "local");
            defaults.add(localChannel);

            CommentedConfig staffChannel = TomlFormat.newConfig();
            staffChannel.set("name", "staff");
            staffChannel.set("displayPrefix", "&c[S]");
            staffChannel.set("shortcut", "s");
            staffChannel.set("permission", "verbatim.channel.staff");
            staffChannel.set("range", -1);
            staffChannel.set("nameColor", "&d");
            staffChannel.set("separator", " &m*&r ");
            staffChannel.set("separatorColor", "&5");
            staffChannel.set("messageColor", "&d");
            staffChannel.set("alwaysOn", false);
            defaults.add(staffChannel);

            return defaults;
        };

        Predicate<Object> channelEntryValidator = obj -> {
            if (!(obj instanceof UnmodifiableConfig)) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Entry is not an UnmodifiableConfig: {}", obj);
                return false;
            }
            UnmodifiableConfig config = (UnmodifiableConfig) obj;
            String entryName = config.getOptional("name").map(String::valueOf).orElse("UNKNOWN_ENTRY");

            if (!(config.get("name") instanceof String) || config.<String>get("name").isEmpty()) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Channel '{}': 'name' is missing, not a String, or empty.", entryName);
                return false;
            }
            if (!(config.get("displayPrefix") instanceof String)) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Channel '{}': 'displayPrefix' is missing or not a String.", entryName);
                return false;
            }
            if (!(config.get("shortcut") instanceof String) || config.<String>get("shortcut").isEmpty()) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Channel '{}': 'shortcut' is missing, not a String, or empty.", entryName);
                return false;
            }
            if (config.contains("permission") && !(config.get("permission") instanceof String)) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Channel '{}': 'permission' is not a String.", entryName);
                return false;
            }
            Object rangeObj = config.get("range"); 
            if (rangeObj == null || !(rangeObj instanceof Number)) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Channel '{}': 'range' is missing or not a Number.", entryName);
                return false;
            }
            if (((Number) rangeObj).intValue() < -1) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Channel '{}': 'range' ({}) is less than -1.", entryName, rangeObj);
                return false;
            }
            if (config.contains("nameColor") && !(config.get("nameColor") instanceof String)) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Channel '{}': 'nameColor' is not a String.", entryName);
                return false;
            }
            if (config.contains("separator") && !(config.get("separator") instanceof String)) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Channel '{}': 'separator' is not a String.", entryName);
                return false;
            }
            if (config.contains("separatorColor") && !(config.get("separatorColor") instanceof String)) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Channel '{}': 'separatorColor' is not a String.", entryName);
                return false;
            }
            if (config.contains("messageColor") && !(config.get("messageColor") instanceof String)) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Channel '{}': 'messageColor' is not a String.", entryName);
                return false;
            }
            if (config.contains("alwaysOn") && !(config.get("alwaysOn") instanceof Boolean)) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Channel '{}': 'alwaysOn' is not a Boolean.", entryName);
                return false;
            }
            if (config.contains("specialChannelType") && !(config.get("specialChannelType") instanceof String)) {
                Verbatim.LOGGER.warn("[VerbatimConfigValidator] Channel '{}': 'specialChannelType' is not a String.", entryName);
                return false;
            }
            // Verbatim.LOGGER.debug("[VerbatimConfigValidator] Channel '{}' passed validation.", entryName); // Remove or comment out debug log
            return true;
        };

        CHANNELS = BUILDER.defineList("channels", defaultChannelsSupplier, channelEntryValidator);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
