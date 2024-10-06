package net.forgecraft.serverpacklocator;

import com.google.common.collect.ForwardingDeque;
import net.forgecraft.serverpacklocator.client.ClientSidedPackHandler;
import net.forgecraft.serverpacklocator.server.ServerSidedPackHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.Environment;
import net.minecraftforge.forgespi.locating.IModDirectoryLocatorFactory;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PackLocator implements IModLocator {
    private static final Logger LOGGER = LogManager.getLogger();
    private ArrayList<IModLocator> locators = new ArrayList<>();

    @Override
    public List<ModFileOrException> scanMods() {
        ArrayList<ModFileOrException> finalModList = new ArrayList<>();

        for (IModLocator locator : locators) {
            finalModList.addAll(locator.scanMods());
        }

        return finalModList;
    }

    @Override
    public String name() {
        return "serverpacklocator";
    }

    @Override
    public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {
        for (IModLocator locator : locators) {
            if (!locator.isValid(modFile)) continue;
            locator.scanFile(modFile, pathConsumer);
        }
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
        Dist dist = Environment.get().getDist();
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path splDir = gameDir.resolve("spl");
        Path configFile = splDir.resolve("config.toml");

        LOGGER.info("Loading ServerPackLocator configuration from {}", configFile);

        try {
            SidedPackHandler<?> sidedLocator = getSidedLocator(dist, gameDir, splDir);

            String reason = sidedLocator.getUnavailabilityReason();
            if (reason != null) {
                LOGGER.error("ServerPackLocator is unavailable: {}", reason);
                return;
            }

            try (var in = getClass().getResourceAsStream("/serverpacklocator-utilmod.embedded")) {
                if (in == null) {
                    LOGGER.error("Failed to find the utility mod in the server pack locator jar. This is okay if you are running in dev!");
                } else {
                    var utilityModPath = splDir.resolve("serverpacklocator-utilmod.jar");
                    Files.copy(in, utilityModPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize util mod.", e);
            }

            try {
                final IModDirectoryLocatorFactory modFileLocator = LaunchEnvironmentHandler.INSTANCE.getModFolderFactory();
                List<File> folders = sidedLocator.getModFolders();
                for (File folder : folders) {
                    IModLocator locator = modFileLocator.build(folder.toPath(), "");
                    locators.add(locator);
                }
            } catch (InterruptedException | IOException exception) {
                LOGGER.error("SPL failed to download the server pack.", exception);
                ModAccessor.setStatusLine("FAILED to download server pack");
            }
        } catch (ConfigException e) {
            LOGGER.error("ServerPackLocator has configuration errors: {}. Please check configuration file.", e.getMessage(), e);
        }
    }

    public SidedPackHandler<?> getSidedLocator(Dist dist, Path gameDir, Path configFile) throws ConfigException {
        return switch (dist) {
            case CLIENT -> new ClientSidedPackHandler(gameDir, configFile);
            case DEDICATED_SERVER -> new ServerSidedPackHandler(gameDir, configFile);
        };
    }

    @Override
    public boolean isValid(IModFile modFile) {
        return locators.stream().anyMatch(locator -> locator.isValid(modFile));
    }
}
