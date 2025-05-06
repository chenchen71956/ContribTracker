package com.example.contribtracker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.contribtracker.database.DatabaseManager;
import com.example.contribtracker.database.Contribution;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class ContribTrackerMod implements ModInitializer {
    public static final String MOD_ID = "contribtracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static Javalin app;
    private static MinecraftServer server;
    private static Map<UUID, Contribution> pendingContributions = new HashMap<>();
    private static Map<UUID, Long> contributionExpiryTimes = new HashMap<>();
    private static final long INVITATION_EXPIRY_TIME = 5 * 60 * 1000; // 5分钟

    @Override
    public void onInitialize() {
        LOGGER.info("初始化贡献追踪器Mod");
        
        try {
            DatabaseManager.initialize();
        } catch (SQLException e) {
            LOGGER.error("初始化数据库失败", e);
            return;
        }
        
        // 初始化HTTP服务器
        initHttpServer();
        
        // 注册服务器启动和停止事件
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        
        // 添加定时任务检查过期邀请
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            long currentTime = System.currentTimeMillis();
            contributionExpiryTimes.entrySet().removeIf(entry -> {
                if (currentTime - entry.getValue() > INVITATION_EXPIRY_TIME) {
                    pendingContributions.remove(entry.getKey());
                    return true;
                }
                return false;
            });
        });
        
        // 注册命令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.server.command.CommandManager.literal(MOD_ID)
                .then(net.minecraft.server.command.CommandManager.argument("type", StringArgumentType.string())
                    .then(net.minecraft.server.command.CommandManager.argument("name", StringArgumentType.string())
                        .then(net.minecraft.server.command.CommandManager.argument("gameId", StringArgumentType.string())
                            .then(net.minecraft.server.command.CommandManager.argument("note", StringArgumentType.string())
                                .executes(this::executeContribCommand)
                            )
                        )
                    )
                )
                .then(net.minecraft.server.command.CommandManager.literal("n")
                    .executes(this::listNearbyContribs)
                )
                .then(net.minecraft.server.command.CommandManager.literal("delete")
                    .then(net.minecraft.server.command.CommandManager.argument("id", IntegerArgumentType.integer())
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(this::deleteContrib)
                    )
                )
                .then(net.minecraft.server.command.CommandManager.argument("note", StringArgumentType.string())
                    .executes(this::executeParticipateCommand)
                )
                .then(net.minecraft.server.command.CommandManager.literal("ignore")
                    .executes(this::executeIgnoreCommand)
                )
                .then(net.minecraft.server.command.CommandManager.literal("invite")
                    .then(net.minecraft.server.command.CommandManager.argument("player", StringArgumentType.string())
                        .then(net.minecraft.server.command.CommandManager.argument("contribution", StringArgumentType.string())
                            .executes(this::executeInviteCommand)
                        )
                    )
                )
            );
        });
    }

    private void initHttpServer() {
        app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> {
                cors.add(corsConfig -> {
                    corsConfig.anyHost();
                });
            });
        });
        
        // 贡献者列表接口
        app.get("/api/contributors", ctx -> {
            // TODO: 实现获取贡献者列表的逻辑
            ctx.json(List.of());
        });
        
        // 所有贡献列表接口
        app.get("/api/contributions", ctx -> {
            // TODO: 实现获取所有贡献列表的逻辑
            ctx.json(List.of());
        });
        
        // 单个贡献者贡献列表接口
        app.get("/api/contributions/:playerId", ctx -> {
            // TODO: 实现获取单个贡献者贡献列表的逻辑
            ctx.json(List.of());
        });
        
        // 单个资源详细信息接口
        app.get("/api/contributions/details/:contributionId", ctx -> {
            // TODO: 实现获取单个资源详细信息的逻辑
            ctx.json(new Object());
        });
    }

    private void onServerStarting(MinecraftServer server) {
        ContribTrackerMod.server = server;
        app.start(8080);
        LOGGER.info("HTTP服务器已启动在端口8080");
    }

    private void onServerStopping(MinecraftServer server) {
        app.stop();
        try {
            DatabaseManager.close();
        } catch (SQLException e) {
            LOGGER.error("关闭数据库连接失败", e);
        }
        LOGGER.info("HTTP服务器已停止");
    }

    private int executeContribCommand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String type = StringArgumentType.getString(context, "type");
        String name = StringArgumentType.getString(context, "name");
        String gameId = StringArgumentType.getString(context, "gameId");
        String note = StringArgumentType.getString(context, "note");
        
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();
        
        try {
            // 保存贡献信息到数据库
            DatabaseManager.addContribution(
                name,
                type,
                gameId,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getWorld().getRegistryKey().getValue().toString(),
                player.getUuid()
            );
            
            // 添加贡献者
            DatabaseManager.addContributor(
                getLastContributionId(),
                player.getUuid(),
                player.getName().getString(),
                note
            );
            
            // 创建贡献对象用于后续玩家参与
            Contribution contribution = new Contribution();
            contribution.setName(name);
            contribution.setType(type);
            contribution.setGameId(gameId);
            contribution.setX(player.getX());
            contribution.setY(player.getY());
            contribution.setZ(player.getZ());
            contribution.setWorld(player.getWorld().getRegistryKey().getValue().toString());
            contribution.setCreatorUuid(player.getUuid());
            
            // 发送通知给附近的玩家
            notifyNearbyPlayers(player, contribution);
            
            source.sendMessage(Text.of("§a内容已发布官网"));
        } catch (SQLException e) {
            LOGGER.error("保存贡献信息失败", e);
            source.sendMessage(Text.of("§c保存贡献信息失败"));
        }
        
        return 1;
    }

    private int executeParticipateCommand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String note = StringArgumentType.getString(context, "note");
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();
        
        Contribution contribution = pendingContributions.get(player.getUuid());
        if (contribution == null) {
            source.sendMessage(Text.of("§c你没有待处理的贡献邀请"));
            return 0;
        }
        
        try {
            // 添加贡献者
            DatabaseManager.addContributor(
                getLastContributionId(),
                player.getUuid(),
                player.getName().getString(),
                note
            );
            
            source.sendMessage(Text.of("§a你已成功参与贡献"));
            pendingContributions.remove(player.getUuid());
        } catch (SQLException e) {
            LOGGER.error("保存贡献信息失败", e);
            source.sendMessage(Text.of("§c保存贡献信息失败"));
        }
        
        return 1;
    }

    private int executeIgnoreCommand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();
        
        Contribution contribution = pendingContributions.get(player.getUuid());
        if (contribution == null) {
            source.sendMessage(Text.of("§c你没有待处理的贡献邀请"));
            return 0;
        }
        
        pendingContributions.remove(player.getUuid());
        source.sendMessage(Text.of("§c那真是太可惜了......"));
        
        return 1;
    }

    private int getLastContributionId() throws SQLException {
        return DatabaseManager.getLastInsertId();
    }

    private int listNearbyContribs(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        PlayerEntity player = context.getSource().getPlayer();
        
        try {
            List<Contribution> contributions = DatabaseManager.getNearbyContributions(
                player.getX(), player.getY(), player.getZ(), 32.0
            );
            
            if (contributions.isEmpty()) {
                context.getSource().sendMessage(Text.of("§c附近没有找到任何贡献"));
            } else {
                for (Contribution contribution : contributions) {
                    context.getSource().sendMessage(Text.of(String.format(
                        "§a| %d | %s | %s |",
                        contribution.getId(),
                        contribution.getName(),
                        contribution.getCreatorName()
                    )));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("获取附近贡献信息失败", e);
            context.getSource().sendMessage(Text.of("§c获取附近贡献信息失败"));
        }
        
        return 1;
    }

    private int deleteContrib(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int contributionId = IntegerArgumentType.getInteger(context, "id");
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();
        
        try {
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
            if (contribution == null) {
                source.sendMessage(Text.of("§c未找到贡献"));
                return 0;
            }
            
            if (!ContribPermissionManager.canDeleteContribution(player, contribution)) {
                source.sendMessage(Text.of("§c你没有权限删除这个贡献"));
                return 0;
            }
            
            DatabaseManager.deleteContribution(contributionId);
            source.sendMessage(Text.of("§a已删除贡献"));
        } catch (SQLException e) {
            LOGGER.error("删除贡献失败", e);
            source.sendMessage(Text.of("§c删除贡献失败"));
        }
        
        return 1;
    }

    private int deleteContributor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int contributionId = IntegerArgumentType.getInteger(context, "id");
        String playerName = StringArgumentType.getString(context, "player");
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();
        
        try {
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
            if (contribution == null) {
                source.sendMessage(Text.of("§c未找到贡献"));
                return 0;
            }
            
            PlayerEntity targetPlayer = server.getPlayerManager().getPlayer(playerName);
            if (targetPlayer == null) {
                source.sendMessage(Text.of("§c未找到玩家"));
                return 0;
            }
            
            if (!ContribPermissionManager.canDeleteContributor(player, contribution, targetPlayer.getUuid())) {
                source.sendMessage(Text.of("§c你没有权限删除这个贡献者"));
                return 0;
            }
            
            DatabaseManager.deleteContributor(contributionId, targetPlayer.getUuid());
            source.sendMessage(Text.of("§a已删除贡献者"));
        } catch (SQLException e) {
            LOGGER.error("删除贡献者失败", e);
            source.sendMessage(Text.of("§c删除贡献者失败"));
        }
        
        return 1;
    }

    private void notifyNearbyPlayers(PlayerEntity player, Contribution contribution) {
        Vec3d pos = player.getPos();
        Box box = new Box(pos.add(-32, -32, -32), pos.add(32, 32, 32));
        List<PlayerEntity> nearbyPlayers = player.getWorld().getEntitiesByType(
            net.minecraft.entity.EntityType.PLAYER,
            box,
            p -> p != player
        );
        
        for (PlayerEntity nearbyPlayer : nearbyPlayers) {
            nearbyPlayer.sendMessage(Text.of("§a你周围刚刚有人发布了一个新的贡献"));
            nearbyPlayer.sendMessage(Text.of("§a贡献者：" + contribution.getContributors()));
            nearbyPlayer.sendMessage(Text.of("§a您的选择将会影响官网的内容，请如实选择"));
            
            // 创建可点击的消息
            Text participateText = Text.literal("§2我参与啦")
                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/contribtracker <你为此次内容贡献了什么?>"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("点击输入你的贡献内容并发布至官网"))));
            
            Text ignoreText = Text.literal("§c与我无关")
                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/contribtracker ignore"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("表示不参与，我只是来凑热闹的"))));
            
            nearbyPlayer.sendMessage(Text.literal("").append(participateText).append(" | ").append(ignoreText));
            pendingContributions.put(nearbyPlayer.getUuid(), contribution);
            contributionExpiryTimes.put(nearbyPlayer.getUuid(), System.currentTimeMillis());
        }
    }

    private int executeInviteCommand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String playerName = StringArgumentType.getString(context, "player");
        String contributionName = StringArgumentType.getString(context, "contribution");
        ServerCommandSource source = context.getSource();
        PlayerEntity inviter = source.getPlayer();
        
        try {
            // 检查贡献是否存在
            Contribution contribution = DatabaseManager.getContributionByName(contributionName);
            if (contribution == null) {
                source.sendMessage(Text.of("§c未找到贡献：" + contributionName));
                return 0;
            }
            
            // 检查邀请者是否是贡献者
            if (!DatabaseManager.isContributor(contribution.getId(), inviter.getUuid())) {
                source.sendMessage(Text.of("§c你不是该贡献的贡献者，无法邀请他人"));
                return 0;
            }
            
            // 查找目标玩家
            PlayerEntity targetPlayer = server.getPlayerManager().getPlayer(playerName);
            if (targetPlayer == null) {
                source.sendMessage(Text.of("§c未找到玩家：" + playerName));
                return 0;
            }
            
            // 检查目标玩家是否已经是贡献者
            if (DatabaseManager.isContributor(contribution.getId(), targetPlayer.getUuid())) {
                source.sendMessage(Text.of("§c该玩家已经是贡献者"));
                return 0;
            }
            
            // 发送邀请
            targetPlayer.sendMessage(Text.of("§a你收到了一个贡献邀请"));
            targetPlayer.sendMessage(Text.of("§a贡献名称：" + contributionName));
            targetPlayer.sendMessage(Text.of("§a贡献者：" + contribution.getContributors()));
            targetPlayer.sendMessage(Text.of("§a您的选择将会影响官网的内容，请如实选择"));
            
            Text participateText = Text.literal("§2我参与啦")
                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/contribtracker <你为此次内容贡献了什么?>"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("点击输入你的贡献内容并发布至官网"))));
            
            Text ignoreText = Text.literal("§c与我无关")
                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/contribtracker ignore"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("表示不参与，我只是来凑热闹的"))));
            
            targetPlayer.sendMessage(Text.literal("").append(participateText).append(" | ").append(ignoreText));
            
            pendingContributions.put(targetPlayer.getUuid(), contribution);
            contributionExpiryTimes.put(targetPlayer.getUuid(), System.currentTimeMillis());
            
            source.sendMessage(Text.of("§a已向玩家 " + playerName + " 发送贡献邀请"));
            
        } catch (SQLException e) {
            LOGGER.error("邀请玩家失败", e);
            source.sendMessage(Text.of("§c邀请玩家失败"));
        }
        
        return 1;
    }
} 