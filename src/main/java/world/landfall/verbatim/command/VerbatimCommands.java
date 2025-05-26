package world.landfall.verbatim.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import world.landfall.verbatim.ChatChannelManager;
import world.landfall.verbatim.ChatFormattingUtils;
import world.landfall.verbatim.Verbatim;
import net.minecraft.network.chat.MutableComponent;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.Optional;

public class VerbatimCommands {

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

        // Override vanilla DM Commands - register these AFTER vanilla commands are registered
        // This will override the vanilla /msg and /tell commands by using the SAME argument tree structure ("targets" & "message") that vanilla uses.
        LiteralArgumentBuilder<CommandSourceStack> msgCommand = Commands.literal("msg")
            .then(Commands.argument("targets", EntityArgument.players())
                // Focus-only variant (no message supplied)
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer sender)) {
                        context.getSource().sendFailure(Component.literal("Players only."));
                        return 0;
                    }
                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                    if (targets.isEmpty()) {
                        sender.sendSystemMessage(Component.literal("No valid player targets.").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    // Focus on the first target only (consistent with /r behaviour)
                    ChatChannelManager.focusDm(sender, targets.iterator().next().getUUID());
                    return 1;
                })
                // Variant with message text supplied
                .then(Commands.argument("message", MessageArgument.message())
                    .executes(context -> {
                        if (!(context.getSource().getEntity() instanceof ServerPlayer sender)) {
                            context.getSource().sendFailure(Component.literal("Players only."));
                            return 0;
                        }
                        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                        String message = MessageArgument.getMessage(context, "message").getString();
                        int successes = 0;
                        for (ServerPlayer target : targets) {
                            successes += sendDirectMessage(sender, target, message);
                        }
                        return successes;
                    }))
            );

        LiteralArgumentBuilder<CommandSourceStack> tellCommand = Commands.literal("tell")
            .then(Commands.argument("targets", EntityArgument.players())
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer sender)) {
                        context.getSource().sendFailure(Component.literal("Players only."));
                        return 0;
                    }
                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                    if (targets.isEmpty()) {
                        sender.sendSystemMessage(Component.literal("No valid player targets.").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    ChatChannelManager.focusDm(sender, targets.iterator().next().getUUID());
                    return 1;
                })
                .then(Commands.argument("message", MessageArgument.message())
                    .executes(context -> {
                        if (!(context.getSource().getEntity() instanceof ServerPlayer sender)) {
                            context.getSource().sendFailure(Component.literal("Players only."));
                            return 0;
                        }
                        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                        String message = MessageArgument.getMessage(context, "message").getString();
                        int successes = 0;
                        for (ServerPlayer target : targets) {
                            successes += sendDirectMessage(sender, target, message);
                        }
                        return successes;
                    }))
            );

        LiteralArgumentBuilder<CommandSourceStack> replyCommand = Commands.literal("r")
            // Variant with message -> reply immediately
            .then(Commands.argument("message", MessageArgument.message())
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer sender)) {
                        context.getSource().sendFailure(Component.literal("Players only."));
                        return 0;
                    }
                    String message = MessageArgument.getMessage(context, "message").getString();
                    return replyToLastDm(sender, message);
                }))
            // No message -> just focus last DM sender (equivalent to d: prefix)
            .executes(context -> {
                if (!(context.getSource().getEntity() instanceof ServerPlayer sender)) {
                    context.getSource().sendFailure(Component.literal("Players only."));
                    return 0;
                }
                ChatChannelManager.handleDPrefix(sender);
                return 1;
            });

        // Register our commands - these will override vanilla commands if they exist
        dispatcher.register(msgCommand);
        dispatcher.register(tellCommand);
        dispatcher.register(replyCommand);
        
        // Also register alternative names to ensure we catch all variants
        dispatcher.register(Commands.literal("w").redirect(msgCommand.build())); // /w is another common alias for whisper/msg

        // Override /list command
        dispatcher.register(Commands.literal("list")
            .executes(context -> executeCustomListCommand(context.getSource())));

        // New /vlist command
        LiteralArgumentBuilder<CommandSourceStack> verbatimListCommand =
            Commands.literal("vlist")
                .executes(context -> listOnlinePlayers(context.getSource()));
        dispatcher.register(verbatimListCommand);
    }

    private static int executeCustomListCommand(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        PlayerList mcPlayerList = server.getPlayerList();
        List<ServerPlayer> onlinePlayers = mcPlayerList.getPlayers();

        if (onlinePlayers.isEmpty()) {
            source.sendSuccess(() -> Component.literal("There are no players currently online.").withStyle(ChatFormatting.YELLOW), true);
            return 1;
        }

        MutableComponent message = Component.literal("Online Players (" + onlinePlayers.size() + "):").withStyle(ChatFormatting.GOLD);

        boolean anyPlayerHasCustomDisplayName = false;
        for (ServerPlayer player : onlinePlayers) {
            String username = player.getName().getString();
            String strippedDisplayName = ChatFormattingUtils.stripFormattingCodes(player.getDisplayName().getString());
            if (!username.equals(strippedDisplayName)) {
                anyPlayerHasCustomDisplayName = true;
                break;
            }
        }

        for (ServerPlayer player : onlinePlayers) {
            String username = player.getName().getString();
            String strippedDisplayName = ChatFormattingUtils.stripFormattingCodes(player.getDisplayName().getString());
            boolean currentPlayerHasCustomDisplayName = !username.equals(strippedDisplayName);

            message.append(Component.literal("\n - ")); // Newline and bullet point

            ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + username + " ");

            if (anyPlayerHasCustomDisplayName) {
                if (currentPlayerHasCustomDisplayName) {
                    message.append(Component.literal(strippedDisplayName)
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withClickEvent(clickEvent)));
                    message.append(Component.literal(" (" + username + ")").withStyle(ChatFormatting.GRAY)); // Username part not clickable if display name is primary
                } else {
                    message.append(Component.literal(username)
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withClickEvent(clickEvent)));
                }
            } else { // No one has a custom display name, show all as yellow usernames
                message.append(Component.literal(username)
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withClickEvent(clickEvent)));
            }
        }

        source.sendSuccess(() -> message, true); // Ephemeral message
        return onlinePlayers.size();
    }

    private static int listOnlinePlayers(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        PlayerList playerList = server.getPlayerList();
        List<ServerPlayer> onlinePlayers = playerList.getPlayers();

        if (onlinePlayers.isEmpty()) {
            source.sendSuccess(() -> Component.literal("There are no players currently online. (vlist)").withStyle(ChatFormatting.YELLOW), true); 
            return 1;
        }

        MutableComponent message = Component.literal("Online Players (" + onlinePlayers.size() + ") [vlist]:\n").withStyle(ChatFormatting.GOLD);

        for (ServerPlayer player : onlinePlayers) {
            String username = player.getName().getString();
            String strippedDisplayName = ChatFormattingUtils.stripFormattingCodes(player.getDisplayName().getString());
            String formattedName = username;
            if (!username.equals(strippedDisplayName)) {
                formattedName = strippedDisplayName + " (" + username + ")";
            }
            message.append(Component.literal(" - " + formattedName + "\n").withStyle(ChatFormatting.GREEN));
        }
        source.sendSuccess(() -> message, true);
        return onlinePlayers.size();
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
        helpMessage.append("/channel help - Shows this help message.\n\n");
        
        helpMessage.append(Component.literal("Direct Message Commands:\n").withStyle(ChatFormatting.AQUA));
        helpMessage.append("/msg <player> [message] - Focus DM with player (and send message if provided).\n");
        helpMessage.append("/tell <player> [message] - Same as /msg.\n");
        helpMessage.append("/r [message] - Reply to last DM sender (and send message if provided).\n\n");
        
        helpMessage.append(Component.literal("Chat Prefixes:\n").withStyle(ChatFormatting.GREEN));
        helpMessage.append("d: - Focus on last DM sender (same as /r without message).\n");
        helpMessage.append("g: - Switch to global chat.\n");
        helpMessage.append(Component.literal("Use shortcuts like ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("g: your message").withStyle(ChatFormatting.ITALIC))
            .append(Component.literal(" to send to global (if shortcut is 'g') and focus it.").withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> helpMessage, false);
        return 1;
    }

    private static int sendDirectMessage(ServerPlayer sender, ServerPlayer target, String message) {
        // Focus sender on target
        ChatChannelManager.focusDm(sender, target.getUUID());
        
        // Update recipient's last incoming DM sender
        ChatChannelManager.setLastIncomingDmSender(target, sender.getUUID());
        
        // Format and send DM messages
        MutableComponent senderMessage = Component.literal("[You -> ")
            .withStyle(ChatFormatting.LIGHT_PURPLE)
            .append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("]: ").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
            
        MutableComponent recipientMessage = Component.literal("[")
            .withStyle(ChatFormatting.LIGHT_PURPLE)
            .append(Component.literal(sender.getName().getString()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" -> You]: ").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        
        sender.sendSystemMessage(senderMessage);
        target.sendSystemMessage(recipientMessage);
        
        Verbatim.LOGGER.debug("[Verbatim DM Command] DM sent from {} to {}: {}", sender.getName().getString(), target.getName().getString(), message);
        return 1;
    }

    private static int replyToLastDm(ServerPlayer sender, String message) {
        Optional<java.util.UUID> lastSenderOpt = ChatChannelManager.getLastIncomingDmSender(sender);
        if (lastSenderOpt.isEmpty()) {
            sender.sendSystemMessage(Component.literal("No recent DMs to reply to.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        ServerPlayer target = ChatChannelManager.getPlayerByUUID(lastSenderOpt.get());
        if (target == null) {
            sender.sendSystemMessage(Component.literal("Cannot reply: Target player is not online.").withStyle(ChatFormatting.RED));
            return 0;
        }

        return sendDirectMessage(sender, target, message);
    }
} 