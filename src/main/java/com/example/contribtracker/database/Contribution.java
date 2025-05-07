package cn.kongchengli.contribtracker.database;

import java.sql.Timestamp;
import java.util.UUID;

public class Contribution {
    private int id;
    private String name;
    private String type;
    private String gameId;
    private double x;
    private double y;
    private double z;
    private String world;
    private Timestamp createdAt;
    private String contributors;
    private UUID creatorUuid;

    // Getters and Setters
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

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
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

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getContributors() {
        return contributors;
    }

    public void setContributors(String contributors) {
        this.contributors = contributors;
    }

    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    public void setCreatorUuid(UUID creatorUuid) {
        this.creatorUuid = creatorUuid;
    }
} 