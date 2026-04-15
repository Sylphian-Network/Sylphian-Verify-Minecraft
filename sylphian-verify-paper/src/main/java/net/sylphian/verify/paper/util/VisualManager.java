package net.sylphian.verify.paper.util;

import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.sylphian.verify.common.PlayerIdentity;
import net.sylphian.verify.paper.VerifyPaper;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Manages the visual identity of players on the server, including chat formatting,
 * tab list names, and nametags.
 * Centralizes logic for the [ForumName] MinecraftUsername identity format.
 */
public class VisualManager {

    private final VerifyPaper plugin;

    /**
     * Constructs a new VisualManager.
     *
     * @param plugin The VerifyPaper plugin instance.
     */
    public VisualManager(VerifyPaper plugin) {
        this.plugin = plugin;
    }

    /**
     * Formats the full display name for a player: [ForumName] MinecraftUsername.
     * Includes interactive forum profile link for chat-compatible components.
     *
     * @param player   The player.
     * @param identity The player's forum identity.
     * @return The formatted component.
     */
    public Component formatFullDisplayName(Player player, PlayerIdentity identity) {
        String forumBase = plugin.getPluginConfig().getForumBaseUrl();
        String forumName = identity.forumUsername();
        String profileUrl = forumBase + "/members/" + forumName + "." + identity.forumUserId();

        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(forumName, NamedTextColor.WHITE))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .hoverEvent(Component.text("View " + forumName + "'s forum profile"))
                .clickEvent(ClickEvent.openUrl(profileUrl));
    }

    /**
     * Updates all visual aspects for a player (tab list, and nametag).
     *
     * @param player   The player to update.
     * @param identity The player's forum identity.
     */
    public void updateVisuals(Player player, PlayerIdentity identity) {
        Component fullDisplayName = formatFullDisplayName(player, identity);
        player.playerListName(fullDisplayName);
        applyNametag(player, identity);
    }

    /**
     * Applies the nametag prefix to a player using scoreboard teams.
     * Sets the team prefix to [ForumName] so the resulting nametag is [ForumName] MinecraftUsername.
     *
     * @param player   The player to update.
     * @param identity The player's forum identity.
     */
    public void applyNametag(Player player, PlayerIdentity identity) {
        Scoreboard scoreboard = plugin.getPlayerNamesScoreboard();
        if (scoreboard == null) return;

        String teamName = "f_" + identity.forumUserId();
        Team team = scoreboard.getTeam(teamName);

        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        team.prefix(
                Component.text("[", NamedTextColor.DARK_GRAY)
                        .append(Component.text(identity.forumUsername(), NamedTextColor.WHITE))
                        .append(Component.text("] ", NamedTextColor.DARK_GRAY))
        );

        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    /**
     * Creates a chat renderer that formats messages with the [ForumName] MinecraftUsername prefix.
     *
     * @param player   The player sending the message.
     * @param identity The player's forum identity.
     * @return The chat renderer.
     */
    public ChatRenderer getChatRenderer(Player player, PlayerIdentity identity) {
        Component fullDisplayName = formatFullDisplayName(player, identity);
        Component separator = Component.text(" » ", NamedTextColor.DARK_GRAY);

        return (source, sourceDisplayName, message, viewer) ->
                Component.empty()
                        .append(fullDisplayName)
                        .append(separator)
                        .append(message.color(NamedTextColor.WHITE));
    }

    /**
     * Removes a player from their scoreboard team and cleans up the team if it's empty.
     *
     * @param player The player to clean up.
     */
    public void cleanUpPlayer(Player player) {
        Scoreboard scoreboard = plugin.getPlayerNamesScoreboard();
        if (scoreboard == null) return;

        Team team = scoreboard.getEntryTeam(player.getName());
        if (team != null) {
            team.removeEntry(player.getName());
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        }
    }
}
