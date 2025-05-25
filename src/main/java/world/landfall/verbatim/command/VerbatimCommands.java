package world.landfall.verbatim.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import world.landfall.verbatim.ChatChannelManager;
import world.landfall.verbatim.ChatFormattingUtils;
import world.landfall.verbatim.Verbatim; // Import Verbatim for MODID and permissionService
import net.minecraft.network.chat.MutableComponent;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.Set;

public class VerbatimCommands {

    // Permission nodes are no longer used for gating the commands themselves,
    // but constants can be kept if you plan to reintroduce command-level perms later
    // or for other purposes. For now, they are effectively unused by .requires().
    // private static final String PERM_NODE_BASE_COMMAND = Verbatim.MODID + ".command.channel";
    // private static final String PERM_NODE_LIST = PERM_NODE_BASE_COMMAND + ".list";
    // private static final String PERM_NODE_HELP = PERM_NODE_BASE_COMMAND + ".help";
    // private static final String PERM_NODE_FOCUS = PERM_NODE_BASE_COMMAND + ".focus";
    // private static final String PERM_NODE_LEAVE = PERM_NODE_BASE_COMMAND + ".leave";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> listAllChannelsCommand =
            Commands.literal("channels")
                .executes(context -> listChannels(context.getSource()));
        dispatcher.register(listAllChannelsCommand);

        LiteralArgumentBuilder<CommandSourceStack> channelCommand = Commands.literal("channel")
            .then(Commands.literal("help")
                .executes(context -> showHelp(context.getSource())))
            .then(Commands.literal("list") // New alias for /channels, or list joined channels
                .executes(context -> listChannels(context.getSource())))
            .then(Commands.literal("focus")
                .then(Commands.argument("channelName", StringArgumentType.string())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        ChatChannelManager.getAllChannelConfigs().stream().map(c -> c.name).collect(Collectors.toList()), builder))
                    .executes(context -> {
                        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                            context.getSource().sendFailure(Component.literal("Players only.")); return 0;
                        }
                        ChatChannelManager.focusChannel(player, StringArgumentType.getString(context, "channelName"));
                        return 1;
                    })))
            .then(Commands.literal("join")
                .then(Commands.argument("channelName", StringArgumentType.string())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        ChatChannelManager.getAllChannelConfigs().stream().map(c -> c.name).collect(Collectors.toList()), builder))
                    .executes(context -> {
                        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                            context.getSource().sendFailure(Component.literal("Players only.")); return 0;
                        }
                        ChatChannelManager.joinChannel(player, StringArgumentType.getString(context, "channelName"));
                        return 1;
                    })))
            .then(Commands.literal("leave")
                .then(Commands.argument("channelName", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        if (context.getSource().getEntity() instanceof ServerPlayer player) {
                            return SharedSuggestionProvider.suggest(ChatChannelManager.getJoinedChannels(player), builder);
                        } return SharedSuggestionProvider.suggest(new String[]{}, builder);
                    })
                    .executes(context -> {
                        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                            context.getSource().sendFailure(Component.literal("Players only.")); return 0;
                        }
                        ChatChannelManager.leaveChannelCmd(player, StringArgumentType.getString(context, "channelName"));
                        return 1;
                    }))
                .executes(context -> { // /channel leave (no args) -> leave focused if not alwaysOn
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Component.literal("Players only.")); return 0;
                    }
                    ChatChannelManager.getFocusedChannelConfig(player).ifPresentOrElse(focused -> {
                        ChatChannelManager.leaveChannelCmd(player, focused.name);
                    }, () -> player.sendSystemMessage(Component.literal("You are not focused on any channel to leave.").withStyle(ChatFormatting.YELLOW)));
                    return 1;
                }))
            .executes(context -> showHelp(context.getSource()));

        dispatcher.register(channelCommand);
        dispatcher.register(Commands.literal(Verbatim.MODID + "channels").redirect(listAllChannelsCommand.build()));
        dispatcher.register(Commands.literal(Verbatim.MODID + "channel").redirect(channelCommand.build()));
    }

    private static int listChannels(CommandSourceStack source) {
        MutableComponent message = Component.literal("Available Channels (Focusable/Joinable):\n").withStyle(ChatFormatting.GOLD);
        Collection<ChatChannelManager.ChannelConfig> allChannels = ChatChannelManager.getAllChannelConfigs();
        if (allChannels.isEmpty()) {
            source.sendFailure(Component.literal("No chat channels are currently configured.").withStyle(ChatFormatting.RED));
            return 0;
        }
        for (ChatChannelManager.ChannelConfig channel : allChannels) {
            message.append(ChatFormattingUtils.parseColors(channel.displayPrefix))
                 .append(Component.literal(" " + channel.name).withStyle(ChatFormatting.YELLOW))
                 .append(Component.literal(" (Shortcut: " + channel.shortcut + ")").withStyle(ChatFormatting.GRAY));
            if (channel.range >= 0) {
                message.append(Component.literal(" - Range: ").withStyle(ChatFormatting.GRAY))
                     .append(Component.literal(String.valueOf(channel.range)).withStyle(ChatFormatting.AQUA));
            }
            if (channel.alwaysOn) {
                message.append(Component.literal(" (Always On, Public)").withStyle(ChatFormatting.DARK_GRAY));
            } else if (channel.permission.isEmpty()) {
                 message.append(Component.literal(" (Public)").withStyle(ChatFormatting.GREEN));
            } else {
                 message.append(Component.literal(" (Permission: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(channel.permission.get()).withStyle(ChatFormatting.ITALIC))
                    .append(Component.literal(")").withStyle(ChatFormatting.GRAY));
            }
            message.append("\n");
        }

        if (source.getEntity() instanceof ServerPlayer player) {
            Set<String> joined = ChatChannelManager.getJoinedChannels(player);
            ChatChannelManager.getFocusedChannelConfig(player).ifPresent(focused -> {
                 message.append(Component.literal("\nYour Focused Channel: ").withStyle(ChatFormatting.BLUE))
                    .append(ChatFormattingUtils.parseColors(focused.displayPrefix))
                    .append(Component.literal(" " + focused.name).withStyle(ChatFormatting.BOLD));
            });
            if (!joined.isEmpty()) {
                message.append(Component.literal("\nYour Joined Channels:\n").withStyle(ChatFormatting.BLUE));
                for (String joinedName : joined) {
                    ChatChannelManager.getChannelConfigByName(joinedName).ifPresent(jc -> {
                        message.append("  - ")
                            .append(ChatFormattingUtils.parseColors(jc.displayPrefix))
                            .append(Component.literal(" " + jc.name).withStyle(ChatFormatting.DARK_AQUA)).append("\n");
                    });
                }
            }
        }
        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int showHelp(CommandSourceStack source) {
        MutableComponent helpMessage = Component.literal("Verbatim Channel Commands:\n").withStyle(ChatFormatting.GOLD);
        helpMessage.append("/channels or /channel list - Lists all available channels & your status.\n");
        helpMessage.append("/channel focus <channelName> - Sets your active typing channel (also joins it).\n");
        helpMessage.append("/channel join <channelName> - Joins a channel to receive messages.\n");
        helpMessage.append("/channel leave <channelName> - Leaves a joined channel.\n");
        helpMessage.append("/channel leave - Leaves your currently focused channel (if not alwaysOn).\n");
        helpMessage.append("/channel help - Shows this help message.\n");
        helpMessage.append(Component.literal("Use shortcuts like ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("g: your message").withStyle(ChatFormatting.ITALIC))
            .append(Component.literal(" to send to global (if shortcut is 'g') and focus it.").withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> helpMessage, false);
        return 1;
    }
} 