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

package ru.vidtu.ias.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.crypt.DummyCrypt;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for loading JSON account files.
 *
 * @author VidTu
 */
public final class JsonAccountLoader {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/JsonAccountLoader");

    /**
     * Gson instance for JSON parsing.
     */
    private static final Gson GSON = new GsonBuilder().create();

    /**
     * An instance of this class cannot be created.
     *
     * @throws AssertionError Always
     */
    private JsonAccountLoader() {
        throw new AssertionError("No instances.");
    }

    /**
     * Loads accounts from a JSON file.
     *
     * @param jsonPath Path to the JSON file
     * @return List of loaded accounts, empty if file doesn't exist or can't be parsed
     */
    @NotNull
    public static List<Account> loadAccountsFromJson(@NotNull Path jsonPath) {
        List<Account> accounts = new ArrayList<>();
        
        try {
            // Check if file exists
            if (!Files.exists(jsonPath)) {
                LOGGER.warn("JSON account file not found: {}", jsonPath);
                return accounts;
            }

            // Read and parse JSON
            String jsonContent = Files.readString(jsonPath);
            JsonAccountFile accountFile = GSON.fromJson(jsonContent, JsonAccountFile.class);
            
            if (accountFile == null || accountFile.ms() == null) {
                LOGGER.warn("Invalid JSON structure in file: {}", jsonPath);
                return accounts;
            }

            // Convert each account
            for (JsonAccountData jsonAccount : accountFile.ms()) {
                try {
                    Account account = createMicrosoftAccountFromJson(jsonAccount);
                    if (account != null) {
                        accounts.add(account);
                        LOGGER.info("Loaded account: {} ({})", jsonAccount.minecraftUsername(), jsonAccount.minecraftUuid());
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to create account for user {}: {}", jsonAccount.minecraftUsername(), e.getMessage(), e);
                }
            }

            LOGGER.info("Successfully loaded {} accounts from JSON file: {}", accounts.size(), jsonPath);
            
        } catch (JsonSyntaxException e) {
            LOGGER.error("Invalid JSON syntax in file {}: {}", jsonPath, e.getMessage());
        } catch (IOException e) {
            LOGGER.error("Failed to read JSON file {}: {}", jsonPath, e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Unexpected error loading JSON accounts from {}: {}", jsonPath, e.getMessage(), e);
        }

        return accounts;
    }

    /**
     * Creates a MicrosoftAccount from JSON data with automatic token refresh.
     *
     * @param jsonAccount JSON account data
     * @return MicrosoftAccount instance or null if creation fails
     */
    @Nullable
    private static MicrosoftAccount createMicrosoftAccountFromJson(@NotNull JsonAccountData jsonAccount) {
        try {
            String accessToken = jsonAccount.accessToken();
            String refreshToken = jsonAccount.refreshToken();
            
            LOGGER.info("Processing account: {} - Attempting token refresh...", jsonAccount.minecraftUsername());
            
            // Try to refresh tokens using the enhanced refresh system
            try {
                TokenRefresher.RefreshResult refreshResult = TokenRefresher.performFullRefresh(refreshToken).get();
                
                if (refreshResult != null) {
                    LOGGER.info("Token refresh successful for: {}", jsonAccount.minecraftUsername());
                    // Use refreshed tokens
                    accessToken = refreshResult.minecraftAccessToken();
                    refreshToken = refreshResult.refreshToken();
                } else {
                    LOGGER.warn("Token refresh failed for: {}, using original tokens", jsonAccount.minecraftUsername());
                }
            } catch (Exception e) {
                LOGGER.warn("Token refresh failed for: {}, using original tokens. Error: {}", jsonAccount.minecraftUsername(), e.getMessage());
            }
            
            // Create the token data
            byte[] tokenData = createTokenData(accessToken, refreshToken);
            
            // Create encrypted data using insecure crypt (since we're importing from external JSON)
            DummyCrypt crypt = DummyCrypt.INSTANCE;
            byte[] encryptedData = crypt.encrypt(tokenData);
            
            // Create final data structure
            byte[] finalData;
            try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                 DataOutputStream out = new DataOutputStream(byteOut)) {
                
                out.writeUTF(crypt.type());
                out.write(encryptedData);
                finalData = byteOut.toByteArray();
            }
            
            return new MicrosoftAccount(
                    true, // insecure = true since we're importing from external source
                    jsonAccount.getMinecraftUUID(),
                    jsonAccount.minecraftUsername(),
                    finalData
            );
            
        } catch (Exception e) {
            LOGGER.error("Failed to create MicrosoftAccount for {}: {}", jsonAccount.minecraftUsername(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Creates the token data byte array from access and refresh tokens.
     *
     * @param accessToken  Access token
     * @param refreshToken Refresh token
     * @return Token data as byte array
     * @throws IOException If writing fails
     */
    @NotNull
    private static byte[] createTokenData(@NotNull String accessToken, @NotNull String refreshToken) throws IOException {
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteOut)) {
            
            out.writeUTF(accessToken);
            out.writeUTF(refreshToken);
            
            return byteOut.toByteArray();
        }
    }

    /**
     * Finds all JSON files in a directory recursively.
     *
     * @param directory Directory to search
     * @return List of JSON file paths
     */
    @NotNull
    public static List<Path> findJsonFiles(@NotNull Path directory) {
        List<Path> jsonFiles = new ArrayList<>();
        
        try {
            if (Files.exists(directory) && Files.isDirectory(directory)) {
                Files.walk(directory)
                        .filter(path -> path.toString().toLowerCase().endsWith(".json"))
                        .filter(Files::isRegularFile)
                        .forEach(jsonFiles::add);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to search for JSON files in {}: {}", directory, e.getMessage());
        }
        
        return jsonFiles;
    }
}
