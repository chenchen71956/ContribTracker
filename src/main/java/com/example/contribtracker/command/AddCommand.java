package com.example.contribtracker.command;

import com.example.contribtracker.ContribPermissionManager;
import com.example.contribtracker.ContribTrackerMod;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.ContributorInfo;
import com.example.contribtracker.database.DatabaseManager;
import com.example.contribtracker.util.LogHelper;
import com.example.contribtracker.websocket.WebSocketHandler;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * 添加贡献命令
 * - /contribtracker add player：列出所有玩家，然后可以使用/contribtracker add player {contribtracker id}添加玩家
 * - /contribtracker add type {name}：添加一个类型为{name}的贡献
 */
public class AddCommand implements BaseCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);

    // 贡献的固定类型列表
    private static final String[] CONTRIBUTION_TYPES = {"redstone", "building", "landmark", "other"};

    private static final int ADMIN_LEVEL = 0;
    private static final int CREATOR_LEVEL = 1;
    private static final int CONTRIBUTOR_LEVEL = 2;

    // 玩家名称补全提供器
    private static final SuggestionProvider<ServerCommandSource> PLAYER_SUGGESTION_PROVIDER = 
            (context, builder) -> {
                MinecraftServer server = context.getSource().getServer();
                return suggestPlayers(builder, server.getPlayerManager().getPlayerList());
            };
    
    // 贡献ID补全提供器
    private static final SuggestionProvider<ServerCommandSource> CONTRIB_ID_SUGGESTION_PROVIDER = 
            (context, builder) -> {
                CompletableFuture<Suggestions> future = new CompletableFuture<>();
                
                // 在工作线程中获取所有贡献ID
                ContribTrackerMod.WORKER_POOL.submit(() -> {
                    try {
                        List<Contribution> contributions = DatabaseManager.getAllContributions();
                        List<String> ids = contributions.stream()
                                .map(c -> String.valueOf(c.getId()))
                                .collect(Collectors.toList());
                        
                        SuggestionsBuilder newBuilder = builder.createOffset(builder.getStart());
                        for (String id : ids) {
                            newBuilder.suggest(id);
                        }
                        future.complete(newBuilder.build());
                    } catch (Exception e) {
                        LOGGER.error("获取贡献ID建议时出错", e);
                        // 发生错误时返回空建议
                        future.complete(builder.build());
                    }
                });
                
                return future;
            };
    
    // 贡献类型补全提供器
    private static final SuggestionProvider<ServerCommandSource> TYPE_SUGGESTION_PROVIDER = 
            (context, builder) -> {
                SuggestionsBuilder newBuilder = builder.createOffset(builder.getStart());
                for (String type : CONTRIBUTION_TYPES) {
                    newBuilder.suggest(type);
                }
                return newBuilder.buildFuture();
            };

    @Override
    public LiteralArgumentBuilder<ServerCommandSource> register() {
        return literal("contribtracker")
            .then(literal("add")
                .then(literal("player")
                    .then(argument("playerName", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTION_PROVIDER)
                        .then(argument("contribId", IntegerArgumentType.integer(1))
                            .suggests(CONTRIB_ID_SUGGESTION_PROVIDER)
                            .executes(this::executeAddPlayer)
                        )
                    )
                )
                .then(literal("type")
                    .then(argument("type", StringArgumentType.word())
                        .suggests(TYPE_SUGGESTION_PROVIDER)
                        .then(argument("name", StringArgumentType.greedyString())
                            .executes(this::executeAddContribution)
                        )
                    )
                )
            );
    }

    /**
     * 为命令补全建议玩家名
     */
    private static CompletableFuture<Suggestions> suggestPlayers(
            SuggestionsBuilder builder, List<ServerPlayerEntity> players) {
        String remaining = builder.getRemaining().toLowerCase();
        
        for (ServerPlayerEntity player : players) {
            String name = player.getName().getString();
            if (name.toLowerCase().startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        
        return builder.buildFuture();
    }

    /**
     * 列出系统中所有贡献者玩家
     */
    private int listPlayers(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("只有玩家可以执行此命令"));
            return 0;
        }
        
        source.sendFeedback(() -> Text.literal("正在获取玩家列表...").formatted(Formatting.GOLD), false);
        
        CompletableFuture.runAsync(() -> {
            try {
                List<ContributorInfo> allPlayers = new ArrayList<>();
                List<Contribution> allContributions = DatabaseManager.getAllContributions();
                
                // 收集所有贡献者
                for (Contribution contribution : allContributions) {
                    List<ContributorInfo> contributors = contribution.getContributorList();
                    if (contributors != null) {
                        for (ContributorInfo contributor : contributors) {
                            // 避免重复
                            boolean isDuplicate = false;
                            for (ContributorInfo existingPlayer : allPlayers) {
                                if (existingPlayer.getPlayerUuid().equals(contributor.getPlayerUuid())) {
                                    isDuplicate = true;
                                    break;
                                }
                            }
                            if (!isDuplicate) {
                                allPlayers.add(contributor);
                            }
                        }
                    }
                }
                
                MinecraftServer server = source.getServer();
                
                server.execute(() -> {
                    if (allPlayers.isEmpty()) {
                        source.sendFeedback(() -> Text.literal("没有找到任何玩家").formatted(Formatting.YELLOW), false);
                        return;
                    }
                    
                    source.sendFeedback(() -> Text.literal("系统中的所有玩家:").formatted(Formatting.GREEN), false);
                    
                    for (ContributorInfo playerInfo : allPlayers) {
                        // 创建可点击文本，点击后会自动填充命令
                        Text playerText = Text.literal("- " + playerInfo.getPlayerName())
                            .formatted(Formatting.GOLD)
                            .styled(style -> style
                                .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.SUGGEST_COMMAND, 
                                    "/contribtracker add player " + playerInfo.getPlayerName()
                                ))
                                .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("点击选择此玩家")
                                ))
                            );
                        
                        source.sendFeedback(() -> playerText, false);
                    }
                    
                    source.sendFeedback(() -> Text.literal("使用 /contribtracker add player <贡献ID> <玩家名> 添加玩家到指定贡献").formatted(Formatting.GRAY), false);
                });
            } catch (SQLException e) {
                LOGGER.error("获取玩家列表时发生数据库错误", e);
                MinecraftServer server = source.getServer();
                server.execute(() -> {
                    source.sendError(Text.literal("获取玩家列表时发生错误: " + e.getMessage()));
                });
            } catch (Exception e) {
                LOGGER.error("获取玩家列表时发生错误", e);
                MinecraftServer server = source.getServer();
                server.execute(() -> {
                    source.sendError(Text.literal("获取玩家列表时发生未知错误"));
                });
            }
        }, ContribTrackerMod.WORKER_POOL);
        
            return 1;
    }

    /**
     * 列出指定贡献ID下所有玩家
     */
    private int listPlayersForContribution(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("只有玩家可以执行此命令"));
            return 0;
        }
        
        int contributionId = IntegerArgumentType.getInteger(context, "contribId");
        
        source.sendFeedback(() -> Text.literal("正在获取贡献 #" + contributionId + " 的玩家列表...").formatted(Formatting.GOLD), false);
        
        CompletableFuture.runAsync(() -> {
            try {
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
                
            if (contribution == null) {
                    MinecraftServer server = source.getServer();
                    server.execute(() -> {
                        source.sendError(Text.literal("找不到ID为 " + contributionId + " 的贡献"));
                    });
                    return;
            }
            
                MinecraftServer server = source.getServer();
                
                server.execute(() -> {
                    source.sendFeedback(() -> Text.literal("贡献名称: " + contribution.getName())
                        .formatted(Formatting.GREEN), false);
                    
                    // 获取在线玩家列表
                    List<ServerPlayerEntity> onlinePlayers = new ArrayList<>(server.getPlayerManager().getPlayerList());
                    
                    if (onlinePlayers.isEmpty()) {
                        source.sendFeedback(() -> Text.literal("当前没有在线玩家").formatted(Formatting.YELLOW), false);
                        return;
                    }
                    
                    source.sendFeedback(() -> Text.literal("在线玩家:").formatted(Formatting.GREEN), false);
                    
                    for (ServerPlayerEntity onlinePlayer : onlinePlayers) {
                        // 创建可点击文本，点击后会自动填充命令
                        Text playerText = Text.literal("- " + onlinePlayer.getName().getString())
                            .formatted(Formatting.GOLD)
                            .styled(style -> style
                                .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.SUGGEST_COMMAND, 
                                    "/contribtracker add player " + contributionId + " " + onlinePlayer.getName().getString()
                                ))
                                .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("点击选择此玩家添加到贡献 #" + contributionId)
                                ))
                            );
                        
                        source.sendFeedback(() -> playerText, false);
            }
            
                    source.sendFeedback(() -> Text.literal("使用 /contribtracker add player " + contributionId + " <玩家名> 添加玩家到此贡献").formatted(Formatting.GRAY), false);
                });
        } catch (SQLException e) {
                LOGGER.error("获取贡献玩家列表时发生数据库错误", e);
                MinecraftServer server = source.getServer();
                server.execute(() -> {
                    source.sendError(Text.literal("获取贡献玩家列表时发生错误: " + e.getMessage()));
                });
            } catch (Exception e) {
                LOGGER.error("获取贡献玩家列表时发生错误", e);
                MinecraftServer server = source.getServer();
                server.execute(() -> {
                    source.sendError(Text.literal("获取贡献玩家列表时发生未知错误"));
                });
            }
        }, ContribTrackerMod.WORKER_POOL);
        
        return 1;
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
                MinecraftServer server = source.getServer();
                
                // 创建贡献
                Contribution contribution = new Contribution();
                contribution.setType(type);
                contribution.setName(name);
                contribution.setX(pos.x);
                contribution.setY(pos.y);
                contribution.setZ(pos.z);
                contribution.setWorld(worldName);
                contribution.setCreatorUuid(playerUUID);
                contribution.setCreatorName(playerName);
                
                // 保存到数据库
                int id = DatabaseManager.addContribution(contribution);
                
                if (id > 0) {
                    // 添加创建者作为一级贡献者
                    ContributorInfo creator = new ContributorInfo();
                    creator.setPlayerUuid(playerUUID);
                    creator.setPlayerName(playerName);
                    creator.setLevel(CREATOR_LEVEL);
                    creator.setInviterUuid(null);
                    
                    DatabaseManager.addContributor(id, creator);
                    
                    contribution.setId(id);
                    // 通知客户端
                    final int finalId = id;
                    server.execute(() -> {
                        source.sendFeedback(() -> Text.literal(
                            String.format("成功创建贡献: ID=%d, 类型=%s, 名称=%s", finalId, type, name)
                        ).formatted(Formatting.GREEN), true);
                    });
                    
                    // 广播更新
                    com.example.contribtracker.websocket.WebSocketHandler.broadcastContributionUpdate(contribution);
                } else {
                    server.execute(() -> {
                        source.sendError(Text.literal("创建贡献失败"));
                    });
                }
            } catch (SQLException e) {
                ContribTrackerMod.LOGGER.error("创建贡献时发生数据库错误", e);
                MinecraftServer server = source.getServer();
                server.execute(() -> {
                    source.sendError(Text.literal("创建贡献时发生错误: " + e.getMessage()));
                });
            } catch (Exception e) {
                ContribTrackerMod.LOGGER.error("创建贡献时发生错误", e);
                MinecraftServer server = source.getServer();
                server.execute(() -> {
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

        // 检查权限 - 要求0级权限
        if (!source.hasPermissionLevel(2)) {
            source.sendError(Text.literal("你没有足够的权限执行此命令，需要管理员权限"));
            return 0;
        }

        // 获取参数
        String targetPlayerName = StringArgumentType.getString(context, "playerName");
        int contributionId = IntegerArgumentType.getInteger(context, "contribId");
        
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
                
                ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetPlayerName);
                
                // 管理员可以直接添加贡献者
                addContributor(source, contribution, playerUUID, targetPlayerName, targetPlayer);
            } catch (SQLException e) {
                ContribTrackerMod.LOGGER.error("添加贡献者时发生数据库错误", e);
                MinecraftServer server = source.getServer();
                server.execute(() -> {
                    source.sendError(Text.literal("添加贡献者时发生错误: " + e.getMessage()));
                });
            } catch (Exception e) {
                ContribTrackerMod.LOGGER.error("添加贡献者时发生错误", e);
                MinecraftServer server = source.getServer();
                server.execute(() -> {
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
            contributor.setPlayerUuid(targetPlayer.getUuid());
            contributor.setPlayerName(targetPlayer.getName().getString());
            contributor.setLevel(CONTRIBUTOR_LEVEL);
            contributor.setInviterUuid(inviterUUID);
            
            if (DatabaseManager.isContributor(contribution.getId(), targetPlayer.getUuid())) {
                source.sendFeedback(() -> Text.literal(
                    String.format("玩家 %s 已经是贡献 %s 的贡献者", targetPlayerName, contribution.getName())
                ).formatted(Formatting.YELLOW), false);
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
                contributor.setInviterUuid(inviterUUID);
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