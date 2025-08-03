/*
 * In-Game Account Switcher is a mod for Minecraft that allows you to change your logged in account in-game, without restarting Minecraft.
 * Copyright (C) 2015-2022 The_Fireplace
 * Copyright (C) 2021-2025 VidTu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>
 */

package ru.vidtu.ias.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

/**
 * JSON file selection popup screen.
 *
 * @author VidTu
 */
public final class JsonLoadPopupScreen extends Screen {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/JsonLoadPopupScreen");

    /**
     * Parent screen.
     */
    private final Screen parent;

    /**
     * Callback to execute on successful load.
     */
    private final Runnable onLoad;

    /**
     * Creates a new JSON load popup screen.
     *
     * @param parent Parent screen
     * @param onLoad Callback to execute on successful load
     */
    public JsonLoadPopupScreen(Screen parent, Runnable onLoad) {
        super(Component.translatable("ias.json.loadFeatherAccounts"));
        this.parent = parent;
        this.onLoad = onLoad;
    }

    private void refreshTokensInJsonFile(Path jsonPath) {
        try {
            LOGGER.info("Refreshing tokens in JSON file: {}", jsonPath);

            String jsonContent = Files.readString(jsonPath);
            com.google.gson.JsonObject rootObject = com.google.gson.JsonParser.parseString(jsonContent).getAsJsonObject();
            com.google.gson.JsonArray msArray = rootObject.getAsJsonArray("ms");

            if (msArray == null) {
                LOGGER.error("Invalid JSON structure - missing 'ms' array in file: {}", jsonPath);
                return;
            }

            boolean anyUpdated = false;

            for (int i = 0; i < msArray.size(); i++) {
                com.google.gson.JsonObject accountObj = msArray.get(i).getAsJsonObject();
                String username = accountObj.get("minecraftUsername").getAsString();
                String refreshToken = accountObj.get("refreshToken").getAsString();

                try {
                    LOGGER.info("Refreshing tokens for: {}", username);

                    ru.vidtu.ias.json.TokenRefresher.RefreshResult result =
                            ru.vidtu.ias.json.TokenRefresher.performFullRefresh(refreshToken).get();

                    if (result != null) {
                        accountObj.addProperty("accessToken", result.minecraftAccessToken());
                        accountObj.addProperty("refreshToken", result.refreshToken());
                        accountObj.addProperty("tokenExpiresAt", result.expiresAt());

                        LOGGER.info("✅ Token refresh successful for: {}", username);
                        anyUpdated = true;
                    } else {
                        LOGGER.warn("❌ Token refresh failed for: {}", username);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to refresh tokens for {}: {}", username, e.getMessage());
                }
            }

            if (anyUpdated) {
                String updatedJson = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rootObject);
                Files.writeString(jsonPath, updatedJson);
                LOGGER.info("✅ Updated JSON file saved: {}", jsonPath);
            } else {
                LOGGER.warn("⚠️ No tokens were refreshed in file: {}", jsonPath);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to refresh tokens in JSON file: {}", e.getMessage(), e);
        }
    }

    /**
     * Loads JSON accounts from the feather-accounts folder.
     *
     * @param featherAccountsDir Path to feather-accounts directory
     */
    private void loadFromFeatherAccountsFolder(Path featherAccountsDir) {
        try {
            if (!Files.exists(featherAccountsDir)) {
                LOGGER.warn("Feather-accounts directory does not exist: {}", featherAccountsDir);
                return;
            }

            AccountList.JSON_ACCOUNTS.clear();

            // Find all JSON files in feather-accounts folder
            java.util.List<Path> jsonFiles = ru.vidtu.ias.json.JsonAccountLoader.findJsonFiles(featherAccountsDir);

            LOGGER.info("Found {} JSON files in feather-accounts folder", jsonFiles.size());

            for (Path jsonFile : jsonFiles) {
                try {
                    java.util.List<ru.vidtu.ias.account.Account> accounts =
                            ru.vidtu.ias.json.JsonAccountLoader.loadAccountsFromJson(jsonFile);
                    AccountList.JSON_ACCOUNTS.addAll(accounts);
                    LOGGER.info("Loaded {} accounts from: {}", accounts.size(), jsonFile.getFileName());
                } catch (Exception e) {
                    LOGGER.error("Failed to load accounts from {}: {}", jsonFile, e.getMessage());
                }
            }

            LOGGER.info("Total accounts loaded: {}", AccountList.JSON_ACCOUNTS.size());

        } catch (Exception e) {
            LOGGER.error("Failed to load from feather-accounts folder: {}", e.getMessage(), e);
        }
    }

    /**
     * Refreshes tokens in all JSON files in the feather-accounts folder.
     *
     * @param featherAccountsDir Path to feather-accounts directory
     */
    private void refreshTokensInFeatherFolder(Path featherAccountsDir) {
        try {
            if (!Files.exists(featherAccountsDir)) {
                LOGGER.warn("Feather-accounts directory does not exist: {}", featherAccountsDir);
                return;
            }

            // Find all JSON files in feather-accounts folder
            java.util.List<Path> jsonFiles = ru.vidtu.ias.json.JsonAccountLoader.findJsonFiles(featherAccountsDir);

            LOGGER.info("Refreshing tokens in {} JSON files", jsonFiles.size());

            for (Path jsonFile : jsonFiles) {
                try {
                    LOGGER.info("Refreshing tokens in: {}", jsonFile.getFileName());
                    refreshTokensInJsonFile(jsonFile);
                } catch (Exception e) {
                    LOGGER.error("Failed to refresh tokens in {}: {}", jsonFile, e.getMessage());
                }
            }

            LOGGER.info("Token refresh completed for feather-accounts folder");

        } catch (Exception e) {
            LOGGER.error("Failed to refresh tokens in feather-accounts folder: {}", e.getMessage(), e);
        }
    }

    /**
     * Opens file explorer to the specified directory.
     *
     * @param directory Directory to open
     */
    private void openFileExplorer(Path directory) {
        try {
            // Ensure directory exists
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                LOGGER.info("Created directory: {}", directory);
            }

            // Open file explorer based on OS
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder processBuilder;

            if (os.contains("win")) {
                // Windows
                processBuilder = new ProcessBuilder("explorer", directory.toString());
            } else if (os.contains("mac")) {
                // macOS
                processBuilder = new ProcessBuilder("open", directory.toString());
            } else {
                // Linux/Unix
                processBuilder = new ProcessBuilder("xdg-open", directory.toString());
            }

            processBuilder.start();
            LOGGER.info("Opened file explorer for: {}", directory);

        } catch (Exception e) {
            LOGGER.error("Failed to open file explorer for {}: {}", directory, e.getMessage());
        }
    }

    @Override
    protected void init() {
        assert this.minecraft != null;

        if (this.parent != null) {
            this.parent.init(this.minecraft, this.width, this.height);
        }

        Path gameDir = this.minecraft.gameDirectory.toPath();
        Path featherAccountsDir = gameDir.resolve("feather-accounts");

        try {
            if (!Files.exists(featherAccountsDir)) {
                Files.createDirectories(featherAccountsDir);
                LOGGER.info("Created feather-accounts directory: {}", featherAccountsDir);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create feather-accounts directory: {}", e.getMessage());
        }

        PopupButton button = new PopupButton(this.width / 2 - 90, this.height / 2 - 15, 180, 20,
                Component.translatable("ias.json.loadFeatherAccounts"), btn -> {
            try {
                LOGGER.info("Loading JSON accounts from feather-accounts folder...");
                loadFromFeatherAccountsFolder(featherAccountsDir);
                this.onLoad.run();
                this.minecraft.setScreen(this.parent);
            } catch (Exception e) {
                LOGGER.error("Failed to load from feather-accounts folder: {}", e.getMessage(), e);
            }
        }, Supplier::get);
        button.color(0.5F, 1.0F, 0.5F, true);
        this.addRenderableWidget(button);

        button = new PopupButton(this.width / 2 - 90, this.height / 2 + 10, 180, 20,
                Component.translatable("ias.json.refreshFeatherTokens"), btn -> {
            try {
                LOGGER.info("Refreshing tokens in feather-accounts folder...");
                refreshTokensInFeatherFolder(featherAccountsDir);
                loadFromFeatherAccountsFolder(featherAccountsDir);
                this.onLoad.run();
                this.minecraft.setScreen(this.parent);
            } catch (Exception e) {
                LOGGER.error("Failed to refresh tokens in feather-accounts folder: {}", e.getMessage(), e);
            }
        }, Supplier::get);
        button.color(0.5F, 0.5F, 1.0F, true);
        this.addRenderableWidget(button);

        button = new PopupButton(this.width / 2 - 90, this.height / 2 + 35, 180, 20,
                Component.translatable("ias.json.openFeatherFolder"), btn -> {
            try {
                openFileExplorer(featherAccountsDir);
            } catch (Exception e) {
                LOGGER.error("Failed to open feather-accounts folder: {}", e.getMessage(), e);
            }
        }, Supplier::get);
        button.color(0.8F, 0.8F, 0.8F, true);
        this.addRenderableWidget(button);

        this.addRenderableWidget(new PopupButton(this.width / 2 - 90, this.height / 2 + 60, 180, 20,
                CommonComponents.GUI_BACK, btn -> this.minecraft.setScreen(this.parent), Supplier::get));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        assert this.minecraft != null;
        PoseStack pose = graphics.pose();

        if (this.parent != null) {
            pose.pushPose();
            pose.translate(0.0F, 0.0F, -1000.0F);
            this.parent.render(graphics, 0, 0, delta);
            pose.popPose();
        }

        super.render(graphics, mouseX, mouseY, delta);

        pose.pushPose();
        pose.scale(2.0F, 2.0F, 2.0F);
        graphics.drawCenteredString(this.font, this.title, this.width / 4, this.height / 4 - 50 / 2, 0xFF_FF_FF_FF);
        pose.popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        assert this.minecraft != null;

        if (this.parent != null) {
            graphics.fill(0, 0, this.width, this.height, 0x80_00_00_00);
        } else {
            super.renderBackground(graphics, mouseX, mouseY, delta);
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        graphics.fill(centerX - 150, centerY - 50, centerX + 150, centerY + 80, 0xF8_20_20_30);
        graphics.fill(centerX - 99, centerY - 51, centerX + 99, centerY - 50, 0xF8_20_20_30);
        graphics.fill(centerX - 99, centerY + 80, centerX + 99, centerY + 81, 0xF8_20_20_30);
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        this.minecraft.setScreen(this.parent);
    }
}
