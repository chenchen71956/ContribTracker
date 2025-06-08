package com.example.contribtracker.command;

import com.example.contribtracker.ContribPermissionManager;
import com.example.contribtracker.ContribTrackerMod;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.ContributorInfo;
import com.example.contribtracker.database.DatabaseManager;
import com.example.contribtracker.websocket.WebSocketHandler;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * 添加贡献命令
 * - 无参数：创建新贡献
 * - 对于一级贡献者：可以直接添加其他玩家为二级贡献者
 * - 对于二级及以下贡献者：只能发送邀请
 */
public class AddCommand implements BaseCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);

    // 贡献的固定类型列表
    private static final String[] CONTRIBUTION_TYPES = {"redstone", "building", "landmark", "other"};

    private static final int ADMIN_LEVEL = 0;
    private static final int CREATOR_LEVEL = 1;
    private static final int CONTRIBUTOR_LEVEL = 2;

    @Override
    public LiteralArgumentBuilder<ServerCommandSource> register() {
        return literal("contribtracker")
            .then(literal("add")
                .then(argument("type", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        builder.suggest("redstone");
                        builder.suggest("building");
                        builder.suggest("landmark");
                        builder.suggest("other");
                        return builder.buildFuture();
                    })
                    .then(argument("name", StringArgumentType.greedyString())
                        .executes(this::executeAddContribution)
                    )
                    .then(argument("name", StringArgumentType.string())
                        .then(argument("player", StringArgumentType.word())
                            .executes(this::executeAddContributionWithPlayer)
                        )
                    )
                )
                .then(literal("e")
                    .then(argument("id", StringArgumentType.word())
                        .then(argument("player", StringArgumentType.word())
                            .executes(this::executeAddPlayer)
                        )
                    )
                )
            );
        }
            
    private int executeAddContribution(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // 快速检查权限，防止明显无权限的操作阻塞主线程
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("只有玩家可以执行此命令"));
            return 0;
        }

        // 获取参数
        String type = StringArgumentType.getString(context, "type");
        String name = StringArgumentType.getString(context, "name");
        
        // 验证贡献类型
        if (!isValidType(type)) {
            source.sendError(Text.literal("无效的贡献类型，有效类型: redstone, building, landmark, other"));
            return 0;
        }
        
        // 显示处理提示
        source.sendFeedback(() -> Text.literal("正在处理贡献创建请求...").formatted(Formatting.GOLD), false);
        
        // 在工作线程中处理数据库操作
        CompletableFuture.runAsync(() -> {
            try {
                UUID playerUUID = player.getUuid();
                String playerName = player.getName().getString();
                Vec3d pos = player.getPos();
                String worldName = getWorldName(player.getWorld());
                
                // 创建贡献
                Contribution contribution = new Contribution();
                contribution.setType(type);
                contribution.setName(name);
                contribution.setX(pos.x);
                contribution.setY(pos.y);
                contribution.setZ(pos.z);
                contribution.setWorld(worldName);
                contribution.setCreatorUuid(playerUUID.toString());
                contribution.setCreatorName(playerName);
                
                // 保存到数据库
                long id = DatabaseManager.addContribution(contribution);
                
                if (id > 0) {
                    // 添加创建者作为一级贡献者
                    ContributorInfo creator = new ContributorInfo();
                    creator.setPlayerUuid(playerUUID.toString());
                    creator.setPlayerName(playerName);
                    creator.setLevel(CREATOR_LEVEL);
                    creator.setInviterUuid(null);
                    
                    DatabaseManager.addContributor(id, creator);
                    
                    contribution.setId(id);
                    // 通知客户端
                    source.getServer().execute(() -> {
                        source.sendFeedback(() -> Text.literal(
                            String.format("成功创建贡献: ID=%d, 类型=%s, 名称=%s", id, type, name)
                        ).formatted(Formatting.GREEN), true);
                    });
                    
                    // 广播更新
                    com.example.contribtracker.websocket.WebSocketHandler.broadcastContributionUpdate(contribution);
                } else {
                    source.getServer().execute(() -> {
                        source.sendError(Text.literal("创建贡献失败"));
                    });
                }
            } catch (SQLException e) {
                ContribTrackerMod.LOGGER.error("创建贡献时发生数据库错误", e);
                source.getServer().execute(() -> {
                    source.sendError(Text.literal("创建贡献时发生错误: " + e.getMessage()));
                });
            } catch (Exception e) {
                ContribTrackerMod.LOGGER.error("创建贡献时发生错误", e);
                source.getServer().execute(() -> {
                    source.sendError(Text.literal("创建贡献时发生未知错误"));
                });
            }
        }, ContribTrackerMod.WORKER_POOL);
        
        return 1;
    }

    private int executeAddContributionWithPlayer(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // 快速检查权限
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("只有玩家可以执行此命令"));
            return 0;
        }
        
        // 获取参数
        String type = StringArgumentType.getString(context, "type");
        String name = StringArgumentType.getString(context, "name");
        String targetPlayerName = StringArgumentType.getString(context, "player");
        
        // 验证贡献类型
        if (!isValidType(type)) {
            source.sendError(Text.literal("无效的贡献类型，有效类型: redstone, building, landmark, other"));
            return 0;
        }
        
        // 显示处理提示
        source.sendFeedback(() -> Text.literal("正在处理贡献创建请求...").formatted(Formatting.GOLD), false);
        
        // 在工作线程中处理
        CompletableFuture.runAsync(() -> {
            try {
                UUID playerUUID = player.getUuid();
                String playerName = player.getName().getString();
                MinecraftServer server = source.getServer();
                Vec3d pos = player.getPos();
                String worldName = getWorldName(player.getWorld());
                ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetPlayerName);
                
                Contribution contribution = new Contribution();
                contribution.setType(type);
                contribution.setName(name);
                contribution.setX(pos.x);
                contribution.setY(pos.y);
                contribution.setZ(pos.z);
                contribution.setWorld(worldName);
                contribution.setCreatorUuid(playerUUID.toString());
                contribution.setCreatorName(playerName);
                
                long id = DatabaseManager.addContribution(contribution);
                
                if (id > 0) {
                    // 添加创建者作为一级贡献者
                    ContributorInfo creator = new ContributorInfo();
                    creator.setPlayerUuid(playerUUID.toString());
                    creator.setPlayerName(playerName);
                    creator.setLevel(CREATOR_LEVEL);
                    creator.setInviterUuid(null);
                    DatabaseManager.addContributor(id, creator);
                    
                    // 添加指定玩家为二级贡献者
                    ContributorInfo contributor = new ContributorInfo();
                    
                    if (targetPlayer != null) {
                        // 在线玩家
                        contributor.setPlayerUuid(targetPlayer.getUuid().toString());
                        contributor.setPlayerName(targetPlayer.getName().getString());
                        contributor.setLevel(CONTRIBUTOR_LEVEL);
                        contributor.setInviterUuid(playerUUID.toString());
                        DatabaseManager.addContributor(id, contributor);
                        
                        server.execute(() -> {
                            targetPlayer.sendMessage(Text.literal(
                                String.format("你已被添加为贡献 %s (ID: %d) 的贡献者", name, id)
                            ).formatted(Formatting.GREEN));
                            
                            source.sendFeedback(() -> Text.literal(
                                String.format("成功创建贡献: ID=%d, 类型=%s, 名称=%s，并添加玩家 %s 为贡献者",
                                    id, type, name, targetPlayer.getName().getString())
                            ).formatted(Formatting.GREEN), true);
                        });
                    } else {
                        // 尝试添加离线玩家
                        List<ContributorInfo> offlinePlayerInfo = DatabaseManager.findPlayerByName(targetPlayerName);
                        
                        if (!offlinePlayerInfo.isEmpty()) {
                            ContributorInfo offlinePlayer = offlinePlayerInfo.get(0);
                            contributor.setPlayerUuid(offlinePlayer.getPlayerUuid());
                            contributor.setPlayerName(offlinePlayer.getPlayerName());
                            contributor.setLevel(CONTRIBUTOR_LEVEL);
                            contributor.setInviterUuid(playerUUID.toString());
                            DatabaseManager.addContributor(id, contributor);
                            
                            server.execute(() -> {
                                source.sendFeedback(() -> Text.literal(
                                    String.format("成功创建贡献: ID=%d, 类型=%s, 名称=%s，并添加离线玩家 %s 为贡献者",
                                        id, type, name, offlinePlayer.getPlayerName())
                                ).formatted(Formatting.GREEN), true);
                            });
                        } else {
                            server.execute(() -> {
                                source.sendError(Text.literal("找不到玩家: " + targetPlayerName));
                            });
                        }
                    }
                    
                    // 获取完整贡献信息并广播
                    Contribution updatedContribution = DatabaseManager.getContributionById(id);
                    if (updatedContribution != null) {
                        com.example.contribtracker.websocket.WebSocketHandler.broadcastContributionUpdate(updatedContribution);
                    }
                } else {
                    server.execute(() -> {
                        source.sendError(Text.literal("创建贡献失败"));
                    });
                }
            } catch (SQLException e) {
                ContribTrackerMod.LOGGER.error("创建贡献时发生数据库错误", e);
                source.getServer().execute(() -> {
                    source.sendError(Text.literal("创建贡献时发生错误: " + e.getMessage()));
                });
            } catch (Exception e) {
                ContribTrackerMod.LOGGER.error("创建贡献时发生错误", e);
                source.getServer().execute(() -> {
                    source.sendError(Text.literal("创建贡献时发生未知错误"));
                });
            }
        }, ContribTrackerMod.WORKER_POOL);
        
            return 1;
    }

    private int executeAddPlayer(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // 快速基本检查
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("只有玩家可以执行此命令"));
                return 0;
            }

        // 获取参数
        String idStr = StringArgumentType.getString(context, "id");
        String targetPlayerName = StringArgumentType.getString(context, "player");
        long contributionId;
        
        try {
            contributionId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            source.sendError(Text.literal("无效的贡献ID，必须是数字"));
            return 0;
        }
        
        // 显示处理提示
        source.sendFeedback(() -> Text.literal("正在处理添加贡献者请求...").formatted(Formatting.GOLD), false);
        
        // 在工作线程中处理
        CompletableFuture.runAsync(() -> {
            try {
                UUID playerUUID = player.getUuid();
                String playerName = player.getName().getString();
                MinecraftServer server = source.getServer();
                
            // 检查贡献是否存在
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
            if (contribution == null) {
                    server.execute(() -> {
                        source.sendError(Text.literal("找不到ID为 " + contributionId + " 的贡献"));
                    });
                    return;
                }
                
                // 检查权限
                int playerLevel = ContribPermissionManager.getContributorLevel(contributionId, playerUUID.toString());
                boolean isAdmin = source.hasPermissionLevel(2);
                
                if (!isAdmin && playerLevel > CREATOR_LEVEL) {
                    server.execute(() -> {
                        source.sendError(Text.literal("你没有权限添加贡献者"));
                    });
                    return;
                }
                
                ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetPlayerName);
                
                if (targetPlayer != null || isAdmin || playerLevel <= CREATOR_LEVEL) {
                    // 管理员、创建者或一级贡献者可以直接添加贡献者
                    addContributor(source, contribution, playerUUID, targetPlayerName, targetPlayer);
                } else {
                    // 其他贡献者只能发送邀请
                    if (targetPlayer == null) {
                        server.execute(() -> {
                            source.sendError(Text.literal("无法邀请离线玩家: " + targetPlayerName));
                        });
                        return;
                    }
                    
                    // 生成邀请
                    UUID inviteId = UUID.randomUUID();
                    ContribTrackerMod.getPendingContributions().put(inviteId, contribution);
                    ContribTrackerMod.getContributionExpiryTimes().put(inviteId, System.currentTimeMillis());
                    
                    server.execute(() -> {
                        // 发送邀请
                        targetPlayer.sendMessage(Text.literal(
                            String.format("%s 邀请你加入贡献 %s (ID: %d)",
                                playerName, contribution.getName(), contribution.getId())
                        ).formatted(Formatting.GOLD));
                        
                        targetPlayer.sendMessage(Text.literal(
                            String.format("输入 /contribtracker accept %s 接受邀请", inviteId)
                        ).formatted(Formatting.GOLD));
                        
                        targetPlayer.sendMessage(Text.literal(
                            String.format("输入 /contribtracker reject %s 拒绝邀请", inviteId)
                        ).formatted(Formatting.GOLD));
                        
                        targetPlayer.sendMessage(Text.literal("邀请将在5分钟后过期").formatted(Formatting.GRAY));
                        
                        source.sendFeedback(() -> Text.literal(
                            String.format("已向 %s 发送贡献邀请，ID: %s", targetPlayer.getName().getString(), inviteId)
                        ).formatted(Formatting.GREEN), false);
                    });
                }
            } catch (SQLException e) {
                ContribTrackerMod.LOGGER.error("添加贡献者时发生数据库错误", e);
                source.getServer().execute(() -> {
                    source.sendError(Text.literal("添加贡献者时发生错误: " + e.getMessage()));
                });
            } catch (Exception e) {
                ContribTrackerMod.LOGGER.error("添加贡献者时发生错误", e);
                source.getServer().execute(() -> {
                    source.sendError(Text.literal("添加贡献者时发生未知错误"));
                });
            }
        }, ContribTrackerMod.WORKER_POOL);
        
        return 1;
    }

    private void addContributor(ServerCommandSource source, Contribution contribution, UUID inviterUUID, 
                              String targetPlayerName, ServerPlayerEntity targetPlayer) throws SQLException {
        MinecraftServer server = source.getServer();
        ContributorInfo contributor = new ContributorInfo();
        
        if (targetPlayer != null) {
            // 在线玩家
            contributor.setPlayerUuid(targetPlayer.getUuid().toString());
            contributor.setPlayerName(targetPlayer.getName().getString());
            contributor.setLevel(CONTRIBUTOR_LEVEL);
            contributor.setInviterUuid(inviterUUID.toString());
            
            // 检查是否已经是贡献者
            if (DatabaseManager.isContributor(contribution.getId(), targetPlayer.getUuid().toString())) {
                server.execute(() -> {
                    source.sendError(Text.literal(targetPlayer.getName().getString() + " 已经是此贡献的贡献者"));
                });
                return;
            }
            
            DatabaseManager.addContributor(contribution.getId(), contributor);
            
            server.execute(() -> {
                targetPlayer.sendMessage(Text.literal(
                    String.format("你已被添加为贡献 %s (ID: %d) 的贡献者", 
                        contribution.getName(), contribution.getId())
                ).formatted(Formatting.GREEN));
                
                source.sendFeedback(() -> Text.literal(
                    String.format("已将 %s 添加为贡献 %s (ID: %d) 的贡献者",
                        targetPlayer.getName().getString(), contribution.getName(), contribution.getId())
                ).formatted(Formatting.GREEN), true);
            });
        } else {
            // 尝试添加离线玩家
            List<ContributorInfo> offlinePlayerInfo = DatabaseManager.findPlayerByName(targetPlayerName);
            
            if (!offlinePlayerInfo.isEmpty()) {
                ContributorInfo offlinePlayer = offlinePlayerInfo.get(0);
                
                // 检查是否已经是贡献者
                if (DatabaseManager.isContributor(contribution.getId(), offlinePlayer.getPlayerUuid())) {
                    server.execute(() -> {
                        source.sendError(Text.literal(offlinePlayer.getPlayerName() + " 已经是此贡献的贡献者"));
                    });
                    return;
                }
                
                contributor.setPlayerUuid(offlinePlayer.getPlayerUuid());
                contributor.setPlayerName(offlinePlayer.getPlayerName());
                contributor.setLevel(CONTRIBUTOR_LEVEL);
                contributor.setInviterUuid(inviterUUID.toString());
                DatabaseManager.addContributor(contribution.getId(), contributor);
                
                server.execute(() -> {
                    source.sendFeedback(() -> Text.literal(
                        String.format("已将离线玩家 %s 添加为贡献 %s (ID: %d) 的贡献者",
                            offlinePlayer.getPlayerName(), contribution.getName(), contribution.getId())
                    ).formatted(Formatting.GREEN), true);
                });
            } else {
                server.execute(() -> {
                    source.sendError(Text.literal("找不到玩家: " + targetPlayerName));
                });
                return;
            }
        }
        
        // 广播更新
        Contribution updatedContribution = DatabaseManager.getContributionById(contribution.getId());
        if (updatedContribution != null) {
            com.example.contribtracker.websocket.WebSocketHandler.broadcastContributionUpdate(updatedContribution);
        }
    }
    
    private boolean isValidType(String type) {
        return type.equalsIgnoreCase("redstone") || 
               type.equalsIgnoreCase("building") || 
               type.equalsIgnoreCase("landmark") || 
               type.equalsIgnoreCase("other");
    }
    
    private String getWorldName(World world) {
        RegistryKey<World> key = world.getRegistryKey();
        
        if (key == World.OVERWORLD) {
            return "overworld";
        } else if (key == World.NETHER) {
            return "the_nether";
        } else if (key == World.END) {
            return "the_end";
        } else {
            return key.getValue().toString();
        }
    }
} 