package world.landfall.verbatim.discord;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.server.ServerLifecycleHooks;
import world.landfall.verbatim.Verbatim;
import world.landfall.verbatim.ChatFormattingUtils;
import net.minecraft.ChatFormatting;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class DiscordListener extends ListenerAdapter {

    // Pattern to match Discord custom emojis: <:name:id> or <a:name:id>
    private static final Pattern CUSTOM_EMOJI_PATTERN = Pattern.compile("<a?:([^:]+):\\d+>");

    /**
     * Processes a Discord message to handle emojis and attachments for Minecraft chat.
     * - Custom emojis become :emoji_name:
     * - Unicode emojis become their shortcode equivalent where possible
     * - Images/attachments become <image> in dark gray
     */
    private MutableComponent processDiscordMessage(Message message) {
        String content = message.getContentDisplay();
        MutableComponent processedMessage = Component.empty();
        
        // Handle custom emojis first
        Matcher customEmojiMatcher = CUSTOM_EMOJI_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (customEmojiMatcher.find()) {
            String emojiName = customEmojiMatcher.group(1);
            customEmojiMatcher.appendReplacement(sb, ":" + emojiName + ":");
        }
        customEmojiMatcher.appendTail(sb);
        content = sb.toString();
        
        // Handle Unicode emojis - convert common ones to text representations
        content = content.replaceAll("😀", ":grinning:")
                        .replaceAll("😃", ":smiley:")
                        .replaceAll("😄", ":smile:")
                        .replaceAll("😁", ":grin:")
                        .replaceAll("😆", ":laughing:")
                        .replaceAll("😅", ":sweat_smile:")
                        .replaceAll("🤣", ":rofl:")
                        .replaceAll("😂", ":joy:")
                        .replaceAll("🙂", ":slightly_smiling_face:")
                        .replaceAll("🙃", ":upside_down_face:")
                        .replaceAll("😉", ":wink:")
                        .replaceAll("😊", ":blush:")
                        .replaceAll("😇", ":innocent:")
                        .replaceAll("🥰", ":smiling_face_with_hearts:")
                        .replaceAll("😍", ":heart_eyes:")
                        .replaceAll("🤩", ":star_struck:")
                        .replaceAll("😘", ":kissing_heart:")
                        .replaceAll("😗", ":kissing:")
                        .replaceAll("😚", ":kissing_closed_eyes:")
                        .replaceAll("😙", ":kissing_smiling_eyes:")
                        .replaceAll("🥲", ":smiling_face_with_tear:")
                        .replaceAll("😋", ":yum:")
                        .replaceAll("😛", ":stuck_out_tongue:")
                        .replaceAll("😜", ":stuck_out_tongue_winking_eye:")
                        .replaceAll("🤪", ":zany_face:")
                        .replaceAll("😝", ":stuck_out_tongue_closed_eyes:")
                        .replaceAll("🤑", ":money_mouth_face:")
                        .replaceAll("🤗", ":hugs:")
                        .replaceAll("🤭", ":hand_over_mouth:")
                        .replaceAll("🤫", ":shushing_face:")
                        .replaceAll("🤔", ":thinking:")
                        .replaceAll("🤐", ":zipper_mouth_face:")
                        .replaceAll("🤨", ":raised_eyebrow:")
                        .replaceAll("😐", ":neutral_face:")
                        .replaceAll("😑", ":expressionless:")
                        .replaceAll("😶", ":no_mouth:")
                        .replaceAll("😏", ":smirk:")
                        .replaceAll("😒", ":unamused:")
                        .replaceAll("🙄", ":roll_eyes:")
                        .replaceAll("😬", ":grimacing:")
                        .replaceAll("🤥", ":lying_face:")
                        .replaceAll("😔", ":pensive:")
                        .replaceAll("😪", ":sleepy:")
                        .replaceAll("🤤", ":drooling_face:")
                        .replaceAll("😴", ":sleeping:")
                        .replaceAll("😷", ":mask:")
                        .replaceAll("🤒", ":face_with_thermometer:")
                        .replaceAll("🤕", ":face_with_head_bandage:")
                        .replaceAll("🤢", ":nauseated_face:")
                        .replaceAll("🤮", ":vomiting_face:")
                        .replaceAll("🤧", ":sneezing_face:")
                        .replaceAll("🥵", ":hot_face:")
                        .replaceAll("🥶", ":cold_face:")
                        .replaceAll("🥴", ":woozy_face:")
                        .replaceAll("😵", ":dizzy_face:")
                        .replaceAll("🤯", ":exploding_head:")
                        .replaceAll("🤠", ":cowboy_hat_face:")
                        .replaceAll("🥳", ":partying_face:")
                        .replaceAll("🥸", ":disguised_face:")
                        .replaceAll("😎", ":sunglasses:")
                        .replaceAll("🤓", ":nerd_face:")
                        .replaceAll("🧐", ":monocle_face:")
                        .replaceAll("😕", ":confused:")
                        .replaceAll("😟", ":worried:")
                        .replaceAll("🙁", ":slightly_frowning_face:")
                        .replaceAll("☹️", ":frowning_face:")
                        .replaceAll("😮", ":open_mouth:")
                        .replaceAll("😯", ":hushed:")
                        .replaceAll("😲", ":astonished:")
                        .replaceAll("😳", ":flushed:")
                        .replaceAll("🥺", ":pleading_face:")
                        .replaceAll("😦", ":frowning:")
                        .replaceAll("😧", ":anguished:")
                        .replaceAll("😨", ":fearful:")
                        .replaceAll("😰", ":cold_sweat:")
                        .replaceAll("😥", ":disappointed_relieved:")
                        .replaceAll("😢", ":cry:")
                        .replaceAll("😭", ":sob:")
                        .replaceAll("😱", ":scream:")
                        .replaceAll("😖", ":confounded:")
                        .replaceAll("😣", ":persevere:")
                        .replaceAll("😞", ":disappointed:")
                        .replaceAll("😓", ":sweat:")
                        .replaceAll("😩", ":weary:")
                        .replaceAll("😫", ":tired_face:")
                        .replaceAll("🥱", ":yawning_face:")
                        .replaceAll("😤", ":triumph:")
                        .replaceAll("😡", ":rage:")
                        .replaceAll("😠", ":angry:")
                        .replaceAll("🤬", ":face_with_symbols_on_mouth:")
                        .replaceAll("😈", ":smiling_imp:")
                        .replaceAll("👿", ":imp:")
                        .replaceAll("💀", ":skull:")
                        .replaceAll("☠️", ":skull_and_crossbones:")
                        .replaceAll("💩", ":poop:")
                        .replaceAll("🤡", ":clown_face:")
                        .replaceAll("👹", ":ogre:")
                        .replaceAll("👺", ":goblin:")
                        .replaceAll("👻", ":ghost:")
                        .replaceAll("👽", ":alien:")
                        .replaceAll("👾", ":space_invader:")
                        .replaceAll("🤖", ":robot:")
                        .replaceAll("😺", ":smiley_cat:")
                        .replaceAll("😸", ":smile_cat:")
                        .replaceAll("😹", ":joy_cat:")
                        .replaceAll("😻", ":heart_eyes_cat:")
                        .replaceAll("😼", ":smirk_cat:")
                        .replaceAll("😽", ":kissing_cat:")
                        .replaceAll("🙀", ":scream_cat:")
                        .replaceAll("😿", ":crying_cat_face:")
                        .replaceAll("😾", ":pouting_cat:")
                        .replaceAll("👋", ":wave:")
                        .replaceAll("🤚", ":raised_back_of_hand:")
                        .replaceAll("🖐️", ":raised_hand_with_fingers_splayed:")
                        .replaceAll("✋", ":raised_hand:")
                        .replaceAll("🖖", ":vulcan_salute:")
                        .replaceAll("👌", ":ok_hand:")
                        .replaceAll("🤌", ":pinched_fingers:")
                        .replaceAll("🤏", ":pinching_hand:")
                        .replaceAll("✌️", ":v:")
                        .replaceAll("🤞", ":crossed_fingers:")
                        .replaceAll("🤟", ":love_you_gesture:")
                        .replaceAll("🤘", ":metal:")
                        .replaceAll("🤙", ":call_me_hand:")
                        .replaceAll("👈", ":point_left:")
                        .replaceAll("👉", ":point_right:")
                        .replaceAll("👆", ":point_up_2:")
                        .replaceAll("🖕", ":middle_finger:")
                        .replaceAll("👇", ":point_down:")
                        .replaceAll("☝️", ":point_up:")
                        .replaceAll("👍", ":thumbsup:")
                        .replaceAll("👎", ":thumbsdown:")
                        .replaceAll("✊", ":fist:")
                        .replaceAll("👊", ":fist_oncoming:")
                        .replaceAll("🤛", ":fist_left:")
                        .replaceAll("🤜", ":fist_right:")
                        .replaceAll("👏", ":clap:")
                        .replaceAll("🙌", ":raised_hands:")
                        .replaceAll("👐", ":open_hands:")
                        .replaceAll("🤲", ":palms_up_together:")
                        .replaceAll("🤝", ":handshake:")
                        .replaceAll("🙏", ":pray:")
                        .replaceAll("✍️", ":writing_hand:")
                        .replaceAll("💅", ":nail_care:")
                        .replaceAll("🤳", ":selfie:")
                        .replaceAll("💪", ":muscle:")
                        .replaceAll("🦾", ":mechanical_arm:")
                        .replaceAll("🦿", ":mechanical_leg:")
                        .replaceAll("🦵", ":leg:")
                        .replaceAll("🦶", ":foot:")
                        .replaceAll("👂", ":ear:")
                        .replaceAll("🦻", ":ear_with_hearing_aid:")
                        .replaceAll("👃", ":nose:")
                        .replaceAll("🧠", ":brain:")
                        .replaceAll("🫀", ":anatomical_heart:")
                        .replaceAll("🫁", ":lungs:")
                        .replaceAll("🦷", ":tooth:")
                        .replaceAll("🦴", ":bone:")
                        .replaceAll("👀", ":eyes:")
                        .replaceAll("👁️", ":eye:")
                        .replaceAll("👅", ":tongue:")
                        .replaceAll("👄", ":lips:")
                        .replaceAll("💋", ":kiss:")
                        .replaceAll("🩸", ":drop_of_blood:")
                        .replaceAll("❤️", ":heart:")
                        .replaceAll("🧡", ":orange_heart:")
                        .replaceAll("💛", ":yellow_heart:")
                        .replaceAll("💚", ":green_heart:")
                        .replaceAll("💙", ":blue_heart:")
                        .replaceAll("💜", ":purple_heart:")
                        .replaceAll("🤎", ":brown_heart:")
                        .replaceAll("🖤", ":black_heart:")
                        .replaceAll("🤍", ":white_heart:")
                        .replaceAll("💔", ":broken_heart:")
                        .replaceAll("❣️", ":heavy_heart_exclamation:")
                        .replaceAll("💕", ":two_hearts:")
                        .replaceAll("💞", ":revolving_hearts:")
                        .replaceAll("💓", ":heartbeat:")
                        .replaceAll("💗", ":heartpulse:")
                        .replaceAll("💖", ":sparkling_heart:")
                        .replaceAll("💘", ":cupid:")
                        .replaceAll("💝", ":gift_heart:")
                        .replaceAll("💟", ":heart_decoration:");
        
        // Add the processed text content
        if (!content.trim().isEmpty()) {
            processedMessage.append(Component.literal(content));
        }
        
        // Handle attachments (images, files, etc.)
        if (!message.getAttachments().isEmpty()) {
            for (Message.Attachment attachment : message.getAttachments()) {
                if (!content.trim().isEmpty()) {
                    processedMessage.append(Component.literal(" "));
                }
                
                if (attachment.isImage()) {
                    processedMessage.append(Component.literal("<image>").withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    processedMessage.append(Component.literal("<file>").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        
        return processedMessage;
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (!DiscordBot.isEnabled()) return;

        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        String configuredChannelId = world.landfall.verbatim.VerbatimConfig.DISCORD_CHANNEL_ID.get();
        if (!event.getChannel().getId().equals(configuredChannelId)) {
            return;
        }

        // Process the Discord message to handle emojis and attachments
        MutableComponent processedMessageContent = processDiscordMessage(event.getMessage());
        String processedText = processedMessageContent.getString();
        
        if (processedText.trim().isEmpty()) {
            return;
        }

        String authorName;
        Member member = event.getMember();
        String nickname = member != null ? member.getNickname() : null;
        if (nickname != null && !nickname.isEmpty()) {
            authorName = nickname;
        } else {
            authorName = author.getName();
        }

        String prefixStr = DiscordBot.getDiscordMessagePrefix(); 
        if (prefixStr == null) prefixStr = "";
        
        String separatorStr = DiscordBot.getDiscordMessageSeparator();
        if (separatorStr == null) separatorStr = ": "; 

        MutableComponent finalMessage = Component.empty();
        int currentLength = 0;
        final int MAX_LENGTH = 256;
        final String TRUNCATION_MARKER = "...";
        final int TRUNCATION_MARKER_LEN = TRUNCATION_MARKER.length();

        if (!prefixStr.isEmpty()) {
            Component prefixComponent = ChatFormattingUtils.parseColors(prefixStr + " ");
            finalMessage.append(prefixComponent);
            currentLength += ChatFormattingUtils.stripFormattingCodes(prefixComponent.getString()).length();
        }
        
        Component authorComponent = Component.literal(authorName);
        finalMessage.append(authorComponent);
        currentLength += authorName.length(); 
        
        Component separatorComponent = ChatFormattingUtils.parseColors(separatorStr);
        finalMessage.append(separatorComponent);
        currentLength += ChatFormattingUtils.stripFormattingCodes(separatorComponent.getString()).length();
        
        int maxContentLength = MAX_LENGTH - currentLength - TRUNCATION_MARKER_LEN;
        
        // Check if we need to truncate the processed message
        if (processedText.length() > maxContentLength) {
            if (maxContentLength > 0) {
                String truncatedText = processedText.substring(0, maxContentLength);
                finalMessage.append(Component.literal(truncatedText));
                finalMessage.append(Component.literal(TRUNCATION_MARKER).withStyle(ChatFormatting.DARK_GRAY));
            } else {
                if (MAX_LENGTH - currentLength >= TRUNCATION_MARKER_LEN) {
                    finalMessage.append(Component.literal(TRUNCATION_MARKER).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        } else {
            // Append the processed message content (which includes emoji conversions and <image> tags)
            finalMessage.append(processedMessageContent);
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(finalMessage, false);
            Verbatim.LOGGER.debug("[Discord -> Game] {} ({}) relayed to game chat.", authorName, author.getId());
        } else {
            Verbatim.LOGGER.warn("[Verbatim Discord] MinecraftServer instance is null, cannot send message to game.");
        }
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!DiscordBot.isEnabled()) {
            event.reply("The Discord bot integration is currently disabled.").setEphemeral(true).queue();
            return;
        }

        if (event.getName().equals("list")) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                event.reply("Could not connect to the Minecraft server to fetch the player list.").setEphemeral(true).queue();
                return;
            }

            PlayerList mcPlayerList = server.getPlayerList();
            List<ServerPlayer> onlinePlayers = mcPlayerList.getPlayers();

            if (onlinePlayers.isEmpty()) {
                event.reply("There are no players currently online on the Minecraft server.").setEphemeral(true).queue();
                return;
            }

            String playerListString = onlinePlayers.stream()
                .map(player -> {
                    String username = player.getName().getString();
                    String strippedDisplayName = ChatFormattingUtils.stripFormattingCodes(player.getDisplayName().getString());
                    if (!username.equals(strippedDisplayName)) {
                        return strippedDisplayName + " (" + username + ")";
                    }
                    return username;
                })
                .collect(Collectors.joining("\n- ", "**Online Players (" + onlinePlayers.size() + "):**\n- ", ""));
            
            if (playerListString.length() > 1990) {
                playerListString = playerListString.substring(0, 1990) + "... (list truncated)";
            }

            event.reply(playerListString).setEphemeral(true).queue();
        }
    }
}