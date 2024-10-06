//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package net.forgecraft.serverpackutility;

import com.mojang.authlib.GameProfile;
import net.forgecraft.serverpacklocator.ModAccessor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("serverpacklocatorutility")
public class UtilityMod {
    private static final Logger LOG = LoggerFactory.getLogger(UtilityMod.class);

    public UtilityMod() {
        MinecraftForge.EVENT_BUS.addListener(this::onServerStart);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStop);
    }

    private void onServerStart(ServerStartedEvent startedEvent) {
        LOG.info("Initializing ServerPackLocator utility mod...");

        var server = startedEvent.getServer();
        ModAccessor.setAllowListStrategy(uuid -> server.submit(() -> {
            if (server.getPlayerList().isUsingWhitelist()) {
                return server.getPlayerList().getWhiteList().isWhiteListed(new GameProfile(uuid, "")); //Name does not matter
            } else {
                return true;
            }
        }).join());
//        ModAccessor.setLogIps(server.logIPs());
    }

    private void onServerStop(ServerStoppedEvent stoppedEvent) {
        ModAccessor.setAllowListStrategy(null);
        ModAccessor.setLogIps(true);
        LOG.info("Uninitialized ServerPackLocator utility mod.");
    }
}
