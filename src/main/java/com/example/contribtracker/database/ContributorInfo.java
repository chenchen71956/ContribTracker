package com.example.contribtracker.database;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;

public class ContributorInfo {
    @SerializedName("playerUuid")
    private UUID playerUuid;
    
    @SerializedName("playerName")
    private String playerName;
    
    @SerializedName("level")
    private int level;
    
    @SerializedName("inviterUuid")
    private UUID inviterUuid;

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public UUID getInviterUuid() {
        return inviterUuid;
    }

    public void setInviterUuid(UUID inviterUuid) {
        this.inviterUuid = inviterUuid;
    }
} 