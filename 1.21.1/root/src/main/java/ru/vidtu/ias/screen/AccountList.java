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

import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.account.OfflineAccount;
import ru.vidtu.ias.auth.LoginData;
import ru.vidtu.ias.config.IASStorage;
import ru.vidtu.ias.json.JsonAccountLoader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Account GUI list.
 *
 * @author VidTu
 */
final class AccountList extends ObjectSelectionList<AccountEntry> {
    /**
     * Skins cache.
     */
    private static final Map<UUID, PlayerSkin> SKINS = new WeakHashMap<>(4);

    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/AccountList");

    /**
     * Parent screen.
     */
    private final AccountScreen screen;

    /**
     * JSON accounts loaded from external files.
     */
    static final java.util.List<Account> JSON_ACCOUNTS = new java.util.ArrayList<>();

    /**
     * Creates a new accounts list widget.
     *
     * @param minecraft Minecraft instance
     * @param width     List width
     * @param height    List height
     * @param offset    List Y offset
     * @param item      Entry height
     */
    AccountList(AccountScreen screen, Minecraft minecraft, int width, int height, int offset, int item) {
        super(minecraft, width, height, offset, item);
        this.screen = screen;
        this.update(this.screen.search().getValue());
    }

    @Override
    public int getRowWidth() {
        return Math.min(super.getRowWidth(), this.screen.width - (85 + 10) * 2);
    }

    @Override
    public void setSelected(@Nullable AccountEntry entry) {
        // Select.
        super.setSelected(entry);

        // Notify parent.
        this.screen.updateSelected();
    }

    /**
     * Update the list by query.
     *
     * @param query Search query
     */
    void update(String query) {
        // Combine IAS accounts and JSON accounts
        Stream<Account> allAccounts = Stream.concat(
                IASStorage.ACCOUNTS.stream(),
                JSON_ACCOUNTS.stream()
        );

        // Add all if blank.
        if (query == null || query.isBlank()) {
            // Add every account.
            AccountEntry selected = this.getSelected();
            this.replaceEntries(allAccounts
                    .map(account -> new AccountEntry(this.minecraft, this, account))
                    .toList());
            this.setSelected(this.children().contains(selected) ? selected : null);

            // Notify the root.
            this.screen.updateSelected();

            // Don't process search.
            return;
        }

        // Lowercase query.
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        // Add every account.
        AccountEntry selected = this.getSelected();
        this.replaceEntries(allAccounts
                .filter(account -> account.name().toLowerCase(Locale.ROOT).contains(lowerQuery))
                .sorted((f, s) -> Boolean.compare(
                        s.name().toLowerCase(Locale.ROOT).startsWith(lowerQuery),
                        f.name().toLowerCase(Locale.ROOT).startsWith(lowerQuery)
                ))
                .map(account -> new AccountEntry(this.minecraft, this, account))
                .toList());
        this.setSelected(this.children().contains(selected) ? selected : null);

        // Notify the root.
        this.screen.updateSelected();
    }

    /**
     * Log in to this account.
     *
     * @param online Whether to try using online authentication
     * @apiNote The {@code online} parameter may be ignored if the current account doesn't support online authentication
     */
    void login(boolean online) {
        // Skip if nothing is selected.
        AccountEntry selected = this.getSelected();
        if (selected == null) return;
        Account account = selected.account();

        // Check if we should log in online.
        if (online && account.canLogin()) {
            // Initialize and set the login screen.
            LoginPopupScreen login = new LoginPopupScreen(this.screen);
            this.minecraft.setScreen(login);

            // Start login.
            IAS.executor().execute(() -> account.login(login));

            // Don't process further.
            return;
        }

        // Initialize and set the login screen.
        LoginPopupScreen login = new LoginPopupScreen(this.screen);
        this.minecraft.setScreen(login);

        // Login offline.
        String name = account.name();
        LoginData data = new LoginData(name, OfflineAccount.uuid(name), "ias:offline", false);
        login.success(data, false);
    }

    void edit() {
        AccountEntry selected = this.getSelected();
        if (selected == null) return;
        Account account = selected.account();

        if (JSON_ACCOUNTS.contains(account)) {
            LOGGER.warn("Cannot edit JSON account (read-only): {}", account.name());
            return;
        }

        int index = this.children().indexOf(selected);
        if (index < 0 || index >= IASStorage.ACCOUNTS.size()) return;

        this.minecraft.setScreen(new AddPopupScreen(this.screen, true, newAccount -> {
            this.minecraft.setScreen(this.screen);

            IASStorage.ACCOUNTS.removeIf(Predicate.isEqual(account));
            if (index >= IASStorage.ACCOUNTS.size()) {
                IASStorage.ACCOUNTS.add(newAccount);
            } else {
                IASStorage.ACCOUNTS.set(index, newAccount);
            }

            try {
                IAS.disclaimersStorage();
                IAS.saveStorage();
            } catch (Throwable t) {
                LOGGER.error("IAS: Unable to save storage.", t);
            }

            this.update(this.screen.search().getValue());
        }));
    }

    void delete(boolean confirm) {
        AccountEntry selected = this.getSelected();
        if (selected == null) return;
        Account account = selected.account();

        if (JSON_ACCOUNTS.contains(account)) {
            JSON_ACCOUNTS.remove(account);
            this.update(this.screen.search().getValue());
            return;
        }

        if (!confirm) {
            IASStorage.ACCOUNTS.remove(account);

            try {
                IAS.disclaimersStorage();
                IAS.saveStorage();
            } catch (Throwable t) {
                LOGGER.error("IAS: Unable to save storage.", t);
            }

            this.update(this.screen.search().getValue());
            return;
        }

        this.minecraft.setScreen(new DeletePopupScreen(this.screen, account, () -> {
            if (JSON_ACCOUNTS.contains(account)) {
                JSON_ACCOUNTS.remove(account);
            } else {
                IASStorage.ACCOUNTS.removeIf(Predicate.isEqual(account));
                try {
                    IAS.disclaimersStorage();
                    IAS.saveStorage();
                } catch (Throwable t) {
                    LOGGER.error("IAS: Unable to save storage.", t);
                }
            }
            this.update(this.screen.search().getValue());
        }));
    }

    /**
     * Opens the account adding screen.
     */
    void add() {
        this.minecraft.setScreen(new AddPopupScreen(this.screen, false, account -> {
            // Set to this.
            this.minecraft.setScreen(this.screen);

            // Add the account.
            IASStorage.ACCOUNTS.removeIf(Predicate.isEqual(account));
            IASStorage.ACCOUNTS.add(account);

            // Save storage.
            try {
                IAS.disclaimersStorage();
                IAS.saveStorage();
            } catch (Throwable t) {
                LOGGER.error("IAS: Unable to save storage.", t);
            }

            // Update the list.
            this.update(this.screen.search().getValue());
        }));
    }

    /**
     * Gets the skin for the account entry.
     *
     * @param entry Target account entry
     * @return Player skin, fetched or default
     */
    PlayerSkin skin(AccountEntry entry) {
        // Get and return the skin if already stored.
        UUID uuid = entry.account().skin();
        PlayerSkin skin = SKINS.get(uuid);
        if (skin != null) return skin;

        // Quickly put the replacer to avoid fetch spam.
        skin = DefaultPlayerSkin.get(uuid);
        SKINS.put(uuid, skin);

        // Skip fetching offline skins.
        if (uuid.version() != 4) return skin;

        // Load the skin.
        CompletableFuture.supplyAsync(() -> {
            // Fetch the profile
            ProfileResult result = this.minecraft.getMinecraftSessionService().fetchProfile(uuid, false);

            // Skip if profile is null.
            if (result == null) return null;

            // Return the profile.
            return result.profile();
        }, IAS.executor()).thenComposeAsync(profile -> {
            // Skip if profile is null.
            if (profile == null) return CompletableFuture.completedFuture(null);

            // Load the skin.
            return this.minecraft.getSkinManager().getOrLoad(profile);
        }, IAS.executor()).thenAcceptAsync(loaded -> {
            // Skip if skin is null.
            if (loaded == null) return;

            // Put into map.
            SKINS.put(uuid, loaded);
        }, this.minecraft).exceptionally(t -> {
            // Log it.
            LOGGER.warn("IAS: Unable to load skin: {}", entry, t);

            // Return null.
            return null;
        });

        // Return quick skin.
        return skin;
    }

    /**
     * Swaps the entry with the account above, if possible.
     *
     * @param entry Target entry
     */
    void swapUp(AccountEntry entry) {
        // Get and validate indexes.
        int idx = this.children().indexOf(entry);
        if (idx < 0 || idx >= IASStorage.ACCOUNTS.size()) return;
        int upIdx = idx - 1;
        if (upIdx < 0) return;

        // Move storage.
        IASStorage.ACCOUNTS.set(idx, IASStorage.ACCOUNTS.get(upIdx));
        IASStorage.ACCOUNTS.set(upIdx, entry.account());

        // Save storage.
        try {
            IAS.disclaimersStorage();
            IAS.saveStorage();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to save storage.", t);
        }

        // Move elements.
        this.children().set(idx, this.children().get(upIdx));
        this.children().set(upIdx, entry);
        this.setSelected(entry);
    }

    /**
     * Swaps the entry with the account below, if possible.
     *
     * @param entry Target entry
     */
    void swapDown(AccountEntry entry) {
        // Get and validate indexes.
        int idx = this.children().indexOf(entry);
        if (idx < 0 || idx >= IASStorage.ACCOUNTS.size()) return;
        int downIdx = idx + 1;
        if (downIdx >= this.children().size() || downIdx >= IASStorage.ACCOUNTS.size()) return;

        // Move storage.
        IASStorage.ACCOUNTS.set(idx, IASStorage.ACCOUNTS.get(downIdx));
        IASStorage.ACCOUNTS.set(downIdx, entry.account());

        // Save storage.
        try {
            IAS.disclaimersStorage();
            IAS.saveStorage();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to save storage.", t);
        }

        // Move elements.
        this.children().set(idx, this.children().get(downIdx));
        this.children().set(downIdx, entry);
        this.setSelected(entry);
    }

    /**
     * Loads JSON accounts from specified directory.
     *
     * @param jsonDirectory Directory containing JSON files
     */
    static void loadJsonAccounts(Path jsonDirectory) {
        JSON_ACCOUNTS.clear();

        // Find and load all JSON files
        for (Path jsonFile : JsonAccountLoader.findJsonFiles(jsonDirectory)) {
            JSON_ACCOUNTS.addAll(JsonAccountLoader.loadAccountsFromJson(jsonFile));
        }

        LOGGER.info("Loaded {} JSON accounts from directory: {}", JSON_ACCOUNTS.size(), jsonDirectory);
    }

    /**
     * Loads JSON accounts from the default directory (Minecraft directory).
     */
    static void loadDefaultJsonAccounts() {
        try {
            // Try common locations for account files
            Path minecraftDir = Paths.get(System.getProperty("user.home"), ".minecraft");
            Path lunarDir = Paths.get(System.getProperty("user.home"), ".lunarclient");
            Path labymodDir = Paths.get(System.getProperty("user.home"), ".minecraft", "LabyMod");

            // Load from .minecraft directory
            if (java.nio.file.Files.exists(minecraftDir)) {
                loadJsonAccounts(minecraftDir);
            }

            // Also try lunar client directory
            if (java.nio.file.Files.exists(lunarDir)) {
                for (Path jsonFile : JsonAccountLoader.findJsonFiles(lunarDir)) {
                    JSON_ACCOUNTS.addAll(JsonAccountLoader.loadAccountsFromJson(jsonFile));
                }
            }

            // Also try LabyMod directory
            if (java.nio.file.Files.exists(labymodDir)) {
                for (Path jsonFile : JsonAccountLoader.findJsonFiles(labymodDir)) {
                    JSON_ACCOUNTS.addAll(JsonAccountLoader.loadAccountsFromJson(jsonFile));
                }
            }

        } catch (Exception e) {
            LOGGER.error("Failed to load default JSON accounts: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the screen.
     *
     * @return Parent accounts screen
     */
    AccountScreen screen() {
        return this.screen;
    }

    @Override
    public String toString() {
        return "AccountList{" +
                "children=" + this.children() +
                '}';
    }
}
