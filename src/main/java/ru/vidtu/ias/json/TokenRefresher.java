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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced Microsoft token refresh utility matching Feather launcher patterns.
 *
 * @author VidTu
 */
public final class TokenRefresher {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/TokenRefresher");

    /**
     * Microsoft OAuth client ID - Feather launcher pattern.
     */
    private static final String CLIENT_ID = "7c82c234-67f5-4ab1-a8a2-842fe81ac39e";

    /**
     * User agent matching Feather launcher.
     */
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) FeatherLauncher/2.3.8 Chrome/132.0.6834.210 Electron/34.5.5 Safari/537.36 (hello@feathermc.com)";

    /**
     * HTTP client for requests.
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * An instance of this class cannot be created.
     *
     * @throws AssertionError Always
     */
    private TokenRefresher() {
        throw new AssertionError("No instances.");
    }

    /**
     * Refreshes Microsoft access token using refresh token.
     *
     * @param refreshToken The refresh token
     * @return CompletableFuture with new token data or null if failed
     */
    @NotNull
    public static CompletableFuture<TokenData> refreshMicrosoftToken(@NotNull String refreshToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Refreshing Microsoft token...");
                
                // Prepare form data - Feather launcher pattern
                String formData = "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) +
                        "&code=" + // Empty for refresh
                        "&grant_type=refresh_token" +
                        "&redirect_uri=" + URLEncoder.encode("https://login.live.com/oauth20_desktop.srf", StandardCharsets.UTF_8) +
                        "&scope=" + URLEncoder.encode("XboxLive.SignIn XboxLive.offline_access profile openid email", StandardCharsets.UTF_8) +
                        "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);

                // Build request
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://login.live.com/oauth20_token.srf"))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Accept-Encoding", "gzip, deflate, br, zstd")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("sec-ch-ua-platform", "\"Windows\"")
                        .header("sec-ch-ua", "\"Not A(Brand\";v=\"8\", \"Chromium\";v=\"132\"")
                        .header("sec-ch-ua-mobile", "?0")
                        .header("Sec-Fetch-Site", "cross-site")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .POST(HttpRequest.BodyPublishers.ofString(formData))
                        .build();

                // Send request
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                
                LOGGER.info("Microsoft token refresh response: {}", response.statusCode());
                
                if (response.statusCode() != 200) {
                    LOGGER.error("Microsoft token refresh failed: {}", response.body());
                    return null;
                }

                // Parse response
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                
                String accessToken = json.get("access_token").getAsString();
                String newRefreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : refreshToken;
                int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600;
                
                // Calculate expiry time
                Instant expiresAt = Instant.now().plusSeconds(expiresIn);
                String expiresAtStr = DateTimeFormatter.ISO_INSTANT.format(expiresAt);
                
                LOGGER.info("Microsoft token refreshed successfully!");
                return new TokenData(accessToken, newRefreshToken, expiresAtStr);
                
            } catch (Exception e) {
                LOGGER.error("Failed to refresh Microsoft token", e);
                return null;
            }
        });
    }

    /**
     * Authenticates with Xbox Live using Microsoft access token.
     *
     * @param accessToken Microsoft access token
     * @return CompletableFuture with Xbox Live token and user hash, or null if failed
     */
    @NotNull
    public static CompletableFuture<XboxData> authenticateXboxLive(@NotNull String accessToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Authenticating with Xbox Live...");
                
                // Prepare JSON payload - Feather pattern with 'd=' prefix
                String jsonPayload = "{\n" +
                        "  \"Properties\": {\n" +
                        "    \"AuthMethod\": \"RPS\",\n" +
                        "    \"SiteName\": \"user.auth.xboxlive.com\",\n" +
                        "    \"RpsTicket\": \"d=" + accessToken + "\"\n" +
                        "  },\n" +
                        "  \"RelyingParty\": \"http://auth.xboxlive.com\",\n" +
                        "  \"TokenType\": \"JWT\"\n" +
                        "}";

                // Build request
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .header("Accept-Encoding", "gzip, deflate, br, zstd")
                        .header("Content-Type", "application/json")
                        .header("sec-ch-ua-platform", "\"Windows\"")
                        .header("sec-ch-ua", "\"Not A(Brand\";v=\"8\", \"Chromium\";v=\"132\"")
                        .header("sec-ch-ua-mobile", "?0")
                        .header("sec-fetch-site", "cross-site")
                        .header("sec-fetch-mode", "cors")
                        .header("sec-fetch-dest", "empty")
                        .header("accept-language", "en-US,en;q=0.9")
                        .header("priority", "u=1, i")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                // Send request
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                
                LOGGER.info("Xbox Live auth response: {}", response.statusCode());
                
                if (response.statusCode() != 200) {
                    LOGGER.error("Xbox Live auth failed: {}", response.body());
                    return null;
                }

                // Parse response
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String xboxToken = json.get("Token").getAsString();
                String userHash = json.getAsJsonObject("DisplayClaims")
                        .getAsJsonArray("xui")
                        .get(0).getAsJsonObject()
                        .get("uhs").getAsString();
                
                LOGGER.info("Xbox Live authentication successful!");
                return new XboxData(xboxToken, userHash);
                
            } catch (Exception e) {
                LOGGER.error("Failed to authenticate with Xbox Live", e);
                return null;
            }
        });
    }

    /**
     * Authenticates with XSTS using Xbox Live token.
     *
     * @param xboxToken Xbox Live token
     * @return CompletableFuture with XSTS token and user hash, or null if failed
     */
    @NotNull
    public static CompletableFuture<XboxData> authenticateXSTS(@NotNull String xboxToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Authenticating with XSTS...");
                
                // Prepare JSON payload
                String jsonPayload = "{\n" +
                        "  \"Properties\": {\n" +
                        "    \"SandboxId\": \"RETAIL\",\n" +
                        "    \"UserTokens\": [\"" + xboxToken + "\"]\n" +
                        "  },\n" +
                        "  \"RelyingParty\": \"rp://api.minecraftservices.com/\",\n" +
                        "  \"TokenType\": \"JWT\"\n" +
                        "}";

                // Build request
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .header("Accept-Encoding", "gzip, deflate, br, zstd")
                        .header("Content-Type", "application/json")
                        .header("sec-ch-ua-platform", "\"Windows\"")
                        .header("sec-ch-ua", "\"Not A(Brand\";v=\"8\", \"Chromium\";v=\"132\"")
                        .header("sec-ch-ua-mobile", "?0")
                        .header("sec-fetch-site", "cross-site")
                        .header("sec-fetch-mode", "cors")
                        .header("sec-fetch-dest", "empty")
                        .header("accept-language", "en-US,en;q=0.9")
                        .header("priority", "u=1, i")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                // Send request
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                
                LOGGER.info("XSTS auth response: {}", response.statusCode());
                
                if (response.statusCode() != 200) {
                    LOGGER.error("XSTS auth failed: {}", response.body());
                    return null;
                }

                // Parse response
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String xstsToken = json.get("Token").getAsString();
                String userHash = json.getAsJsonObject("DisplayClaims")
                        .getAsJsonArray("xui")
                        .get(0).getAsJsonObject()
                        .get("uhs").getAsString();
                
                LOGGER.info("XSTS authentication successful!");
                return new XboxData(xstsToken, userHash);
                
            } catch (Exception e) {
                LOGGER.error("Failed to authenticate with XSTS", e);
                return null;
            }
        });
    }

    /**
     * Authenticates with Minecraft using XSTS token.
     *
     * @param xstsToken XSTS token
     * @param userHash User hash
     * @return CompletableFuture with Minecraft access token, or null if failed
     */
    @NotNull
    public static CompletableFuture<String> authenticateMinecraft(@NotNull String xstsToken, @NotNull String userHash) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Authenticating with Minecraft...");
                
                // Prepare JSON payload
                String jsonPayload = "{\n" +
                        "  \"identityToken\": \"XBL3.0 x=" + userHash + ";" + xstsToken + "\"\n" +
                        "}";

                // Build request
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("sec-ch-ua-platform", "\"Windows\"")
                        .header("sec-ch-ua", "\"Not A(Brand\";v=\"8\", \"Chromium\";v=\"132\"")
                        .header("sec-ch-ua-mobile", "?0")
                        .header("sec-fetch-site", "cross-site")
                        .header("sec-fetch-mode", "cors")
                        .header("sec-fetch-dest", "empty")
                        .header("accept-language", "en-US,en;q=0.9")
                        .header("priority", "u=1, i")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                // Send request
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                
                LOGGER.info("Minecraft auth response: {}", response.statusCode());
                
                if (response.statusCode() != 200) {
                    LOGGER.error("Minecraft auth failed: {}", response.body());
                    return null;
                }

                // Parse response
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String minecraftToken = json.get("access_token").getAsString();
                
                LOGGER.info("Minecraft authentication successful!");
                return minecraftToken;
                
            } catch (Exception e) {
                LOGGER.error("Failed to authenticate with Minecraft", e);
                return null;
            }
        });
    }

    /**
     * Performs full token refresh chain.
     *
     * @param refreshToken Original refresh token
     * @return CompletableFuture with new Minecraft access token and updated tokens, or null if failed
     */
    @NotNull
    public static CompletableFuture<RefreshResult> performFullRefresh(@NotNull String refreshToken) {
        return refreshMicrosoftToken(refreshToken)
                .thenCompose(tokenData -> {
                    if (tokenData == null) return CompletableFuture.completedFuture(null);
                    return authenticateXboxLive(tokenData.accessToken())
                            .thenCompose(xboxData -> {
                                if (xboxData == null) return CompletableFuture.completedFuture(null);
                                return authenticateXSTS(xboxData.token())
                                        .thenCompose(xstsData -> {
                                            if (xstsData == null) return CompletableFuture.completedFuture(null);
                                            return authenticateMinecraft(xstsData.token(), xstsData.userHash())
                                                    .thenApply(minecraftToken -> {
                                                        if (minecraftToken == null) return null;
                                                        return new RefreshResult(
                                                                minecraftToken,
                                                                tokenData.accessToken(),
                                                                tokenData.refreshToken(),
                                                                tokenData.expiresAt()
                                                        );
                                                    });
                                        });
                            });
                });
    }

    /**
     * Token data container.
     */
    public record TokenData(@NotNull String accessToken, @NotNull String refreshToken, @NotNull String expiresAt) {}

    /**
     * Xbox authentication data container.
     */
    public record XboxData(@NotNull String token, @NotNull String userHash) {}

    /**
     * Full refresh result container.
     */
    public record RefreshResult(@NotNull String minecraftAccessToken, @NotNull String microsoftAccessToken, 
                               @NotNull String refreshToken, @NotNull String expiresAt) {}
}
