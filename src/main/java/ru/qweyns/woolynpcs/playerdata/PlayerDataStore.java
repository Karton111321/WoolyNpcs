package ru.qweyns.woolynpcs.playerdata;

import java.util.UUID;

public interface PlayerDataStore {
    PlayerData load(UUID playerId);

    void save(PlayerData data);

    void close();

    String getName();
}
