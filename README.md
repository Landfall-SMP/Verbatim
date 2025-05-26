# Verbatim - Advanced Chat Mod for Minecraft

Verbatim is a Minecraft server-side mod designed to enhance in-game communication by providing configurable chat channels, direct messaging, Discord integration, and more. It aims to offer a flexible and powerful chat system suitable for various server types, especially those with roleplaying or community focus.

## Features

*   **Customizable Chat Channels:**
    *   Define multiple chat channels with unique names, display prefixes (e.g., `&a[G]`), and shortcuts (e.g., `g`).
    *   **Permissions:** Secure channels with permission nodes (integrates with LuckPerms if available, otherwise uses vanilla OP levels).
    *   **Chat Range:** Set per-channel chat ranges (e.g., for local channels) or make them global.
    *   **Color Customization:** Configure colors for channel names, player names, separators, and messages.
    *   **Always-On Channels:** Designate channels that players are always in and cannot leave.
    *   **Mature Content Warnings:** Option to display a warning when players join channels marked as "mature."
    *   **Special Pre-Made Channel Types:**
        *   `local`: Implements distance-based message fading/obscuring and supports different suffixes (e.g., `!!` for shouting, `*` for whispering) that affect message range and style. (Big shout-out to [MassiveChat](https://www.massivecraft.com/documentation/massivechat-aliases-quick-commands/) for this idea!)
*   **Direct Messaging (DM) / Private Messaging (PM):**
    *   Send private messages to other online players.
    *   Quick reply functionality to the last incoming DM.
*   **Chat Prefixes & Shortcuts:**
    *   Use prefixes to quickly switch focus or send messages to specific channels/DMs without commands (e.g., `g: Hello everyone!` to send to and focus the 'global' channel).
    *   `d:` prefix to focus/reply to the last DM.
*   **Discord Integration:**
    *   Bridge in-game chat (specifically a designated "global" channel) to a Discord channel.
    *   Relay messages from Discord back into the game.
    *   Customizable message prefix for Discord messages shown in-game.
*   **Persistent Player Settings:**
    *   Remembers which channels a player has joined and their focused channel across sessions.
*   **Configuration:**
    *   Extensive configuration via a TOML file (`verbatim-server.toml` in the server's `config` folder).
    *   Define all channels, default channel, Discord bot token, channel ID, and other settings.
*   **Admin Commands & Utilities:**
    *   Reload configuration on the fly (typically requires OP/permission).

## Commands

Most commands are accessible via `/channel` or `/verbatim:channel`.

*   `/channels` or `/channel list`
    *   Lists all available chat channels and your current status in them.
*   `/channel focus <channelName>`
    *   Sets your active typing channel. This will also automatically join the channel if you aren't already in it (and have permission).
*   `/channel join <channelName>`
    *   Joins a specific channel to receive its messages.
*   `/channel leave <channelName>`
    *   Leaves a specific joined channel.
*   `/channel leave`
    *   Leaves your currently focused channel (if it's not an "always-on" channel).
*   `/channel help`
    *   Displays a help message with available Verbatim commands and chat prefixes.

**Direct Message Commands:**

*   `/msg <player> [message]` or `/tell <player> [message]`
    *   Sends a direct message to the specified player. If no message is provided, it focuses your chat on that player for subsequent messages.
*   `/r [message]`
    *   Replies to the last player who sent you a direct message. If no message is provided, it focuses your chat on that player.
*   `/w <player> [message]`
    *   An alias for `/msg`.

**Chat Prefixes (examples):**

*   `g: Your message here` - Sends "Your message here" to the channel with shortcut "g" and focuses it.
*   `d: Your reply here` - Sends "Your reply here" to the last person who DM'd you and focuses DM with them.
*   `d:` (with no message) - Focuses DM with the last person who DM'd you (same as `/r` with no message).

## Configuration

Verbatim is configured through the `verbatim-server.toml` file, typically located in the `config` directory of your Minecraft server after the mod is run for the first time.

Key areas to configure:
*   `defaultChannelName`: The channel players will default to.
*   `channels`: A list where each entry defines a chat channel's properties (name, prefix, shortcut, permission, range, colors, etc.).
*   `Discord Integration`: Settings for the Discord bot, including bot token, channel ID, message prefix, and enabling/disabling the feature.

## Permissions

Verbatim attempts to use LuckPerms for permission checking if it's available on the server. If LuckPerms is not found, it falls back to vanilla Minecraft's operator (OP) levels.

*   **Channel Access:** Channel permissions are defined in the `permission` field for each channel in the configuration file (e.g., `verbatim.channel.staff`). If a permission is set, players will need that node to join/speak in the channel (unless it's `alwaysOn`).