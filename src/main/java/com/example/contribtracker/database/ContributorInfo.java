package com.example.contribtracker.database;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;

/**
 * 贡献者信息类
 */
public class ContributorInfo {
    @SerializedName("playerUuid")
    private UUID playerUuid;
    
    @SerializedName("playerName")
    private String playerName;
    
    @SerializedName("level")
    private int level = 1;
    
    @SerializedName("inviterUuid")
    private UUID inviterUuid;
    
    @SerializedName("contributionId")
    private int contributionId;

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
    
    public int getContributionId() {
        return contributionId;
    }
    
    public void setContributionId(int contributionId) {
        this.contributionId = contributionId;
    }
} 