package com.example.contribtracker.database;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

public class Contribution {
    @SerializedName("id")
    private int id;
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("type")
    private String type;
    
    @SerializedName("creatorUuid")
    private UUID creatorUuid;
    
    @SerializedName("creatorName")
    private String creatorName;
    
    @SerializedName("x")
    private double x;
    
    @SerializedName("y")
    private double y;
    
    @SerializedName("z")
    private double z;
    
    @SerializedName("world")
    private String world;
    
    @SerializedName("createdAt")
    private long createdAt;
    
    @SerializedName("dimension")
    private String dimension;
    
    @SerializedName("gameId")
    private String gameId;
    
    @SerializedName("contributors")
    private String contributors;
    
    @SerializedName("inviterUuid")
    private UUID inviterUuid;

    @SerializedName("inviterLevel")
    private int inviterLevel;
    
    @SerializedName("contributorList")
    private List<ContributorInfo> contributorList;

    public Contribution() {
        this.contributorList = new ArrayList<>();
    }

    /**
     * 创建当前贡献对象的深拷贝
     * @return 当前对象的深拷贝
     */
    public Contribution copy() {
        Contribution copy = new Contribution();
        copy.id = this.id;
        copy.name = this.name;
        copy.type = this.type;
        copy.creatorUuid = this.creatorUuid;
        copy.creatorName = this.creatorName;
        copy.x = this.x;
        copy.y = this.y;
        copy.z = this.z;
        copy.world = this.world;
        copy.createdAt = this.createdAt;
        copy.dimension = this.dimension;
        copy.gameId = this.gameId;
        copy.contributors = this.contributors;
        copy.inviterUuid = this.inviterUuid;
        copy.inviterLevel = this.inviterLevel;
        
        if (this.contributorList != null) {
            List<ContributorInfo> copyList = new ArrayList<>();
            for (ContributorInfo info : this.contributorList) {
                ContributorInfo infoCopy = new ContributorInfo();
                infoCopy.setPlayerUuid(info.getPlayerUuid());
                infoCopy.setPlayerName(info.getPlayerName());
                infoCopy.setLevel(info.getLevel());
                infoCopy.setInviterUuid(info.getInviterUuid());
                copyList.add(infoCopy);
            }
            copy.contributorList = copyList;
        }
        
        return copy;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    public void setCreatorUuid(UUID creatorUuid) {
        this.creatorUuid = creatorUuid;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getContributors() {
        return contributors;
    }

    public void setContributors(String contributors) {
        this.contributors = contributors;
    }

    public UUID getInviterUuid() {
        return inviterUuid;
    }

    public void setInviterUuid(UUID inviterUuid) {
        this.inviterUuid = inviterUuid;
    }

    public int getInviterLevel() {
        return inviterLevel;
    }

    public void setInviterLevel(int inviterLevel) {
        this.inviterLevel = inviterLevel;
    }

    public List<ContributorInfo> getContributorList() {
        return contributorList;
    }

    public void setContributorList(List<ContributorInfo> contributorList) {
        this.contributorList = contributorList;
    }
} 