package net.sylphian.verify.velocity.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.sylphian.verify.common.MessageUtils;
import net.sylphian.verify.common.PlayerIdentity;
import net.sylphian.verify.common.VerificationResult;
import net.sylphian.verify.velocity.VerifyVelocity;

import java.util.Map;
import java.util.UUID;

public class PlayerListener {

    private final VerifyVelocity plugin;
    private final Map<UUID, PlayerIdentity> verifiedPlayers;

    public PlayerListener(VerifyVelocity plugin, Map<UUID, PlayerIdentity> verifiedPlayers) {
        this.plugin = plugin;
        this.verifiedPlayers = verifiedPlayers;
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        return EventTask.async(() -> {
            try {
                VerificationResult result = plugin.getVerifyManager().checkPlayer(uuid, ip).join();
                if (!result.isAllowed()) {
                    event.setResult(LoginEvent.ComponentResult.denied(result.getKickMessage()));
                } else {
                    verifiedPlayers.put(uuid, result.getIdentity());
                }
            } catch (Exception e) {
                plugin.getLogger().error("Error checking verification for {}", player.getUsername(), e);
                event.setResult(LoginEvent.ComponentResult.denied(MessageUtils.buildErrorMessage(plugin.getConfig())));
            }
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        verifiedPlayers.remove(uuid);
        plugin.getVerifyManager().resetStrikes(uuid);
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        PlayerIdentity identity = verifiedPlayers.get(player.getUniqueId());
        if (identity != null) {
            plugin.sendVerificationData(player, identity);
        }
    }
}
