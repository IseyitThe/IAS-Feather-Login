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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Main JSON structure for account files.
 *
 * @author VidTu
 */
public record JsonAccountFile(
        @NotNull String selectedAccountID,
        @NotNull List<JsonAccountData> ms
) {
    /**
     * Gets the selected account from the ms list by selectedAccountID.
     *
     * @return Selected account data or null if not found
     */
    @Nullable
    public JsonAccountData getSelectedAccount() {
        return ms.stream()
                .filter(account -> account.minecraftUuid().equals(selectedAccountID))
                .findFirst()
                .orElse(null);
    }
}
