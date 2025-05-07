package cn.kongchengli.cn.contribtracker;

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
import cn.kongchengli.cn.contribtracker.database.DatabaseManager;
import cn.kongchengli.cn.contribtracker.database.Contribution;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import io.javalin.websocket.WsContext;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.sql.SQLException;
import java.util.Pair;

public class ContribTrackerMod implements ModInitializer {
    public static final String MOD_ID = "contribtracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static Javalin app;
    private static MinecraftServer server;
    private static Map<UUID, Contribution> pendingContributions = new HashMap<>();
    private static Map<UUID, Long> contributionExpiryTimes = new HashMap<>();
    private static final long INVITATION_EXPIRY_TIME = 5 * 60 * 1000;

    @Override
    public void onInitialize() {
        LOGGER.info("初始化贡献追踪器Mod");
        
        try {
            DatabaseManager.initialize();
        } catch (SQLException e) {
            LOGGER.error("初始化数据库失败", e);
            return;
        }
        
        // 设置WebSocket服务器
        setupWebSocketServer();
        
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
        registerCommands();
        
        // 注册事件监听器
        registerEventListeners();
    }

    private void setupWebSocketServer() {
        Javalin app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> {
                cors.add(it -> it.anyHost());
            });
        });

        // WebSocket端点
        app.ws("/ws", ws -> {
            ws.onConnect(WebSocketHandler::onConnect);
            ws.onClose(WebSocketHandler::onClose);
            ws.onMessage(WebSocketHandler::onMessage);
        });

        // 启动服务器
        app.start(25566);
        LOGGER.info("WebSocket服务器已启动在端口 25566");
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

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("contribtracker")
                .then(CommandManager.argument("type", StringArgumentType.string())
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .then(CommandManager.argument("note", StringArgumentType.string())
                            .executes(context -> executeContribCommand(context, null, null))
                            .then(CommandManager.argument("gameId", StringArgumentType.string())
                                .then(CommandManager.argument("note", StringArgumentType.string())
                                    .executes(context -> executeContribCommand(context, 
                                        StringArgumentType.getString(context, "gameId"),
                                        StringArgumentType.getString(context, "note")))
                                .then(CommandManager.argument("gameId2", StringArgumentType.string())
                                    .then(CommandManager.argument("note2", StringArgumentType.string())
                                        .executes(context -> executeContribCommand(context, 
                                            StringArgumentType.getString(context, "gameId2"),
                                            StringArgumentType.getString(context, "note2")))
                                    .then(CommandManager.argument("gameId3", StringArgumentType.string())
                                        .then(CommandManager.argument("note3", StringArgumentType.string())
                                            .executes(context -> executeContribCommand(context, 
                                                StringArgumentType.getString(context, "gameId3"),
                                                StringArgumentType.getString(context, "note3"))))
                                    .then(CommandManager.argument("gameId4", StringArgumentType.string())
                                        .then(CommandManager.argument("note4", StringArgumentType.string())
                                            .executes(context -> executeContribCommand(context, 
                                                StringArgumentType.getString(context, "gameId4"),
                                                StringArgumentType.getString(context, "note4"))))
                                    .then(CommandManager.argument("gameId5", StringArgumentType.string())
                                        .then(CommandManager.argument("note5", StringArgumentType.string())
                                            .executes(context -> executeContribCommand(context, 
                                                StringArgumentType.getString(context, "gameId5"),
                                                StringArgumentType.getString(context, "note5"))))))))))))
                .then(CommandManager.literal("n")
                    .executes(this::listNearbyContribs))
                .then(CommandManager.literal("delete")
                    .then(CommandManager.argument("id", IntegerArgumentType.integer())
                        .executes(this::deleteContrib)))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("id", IntegerArgumentType.integer())
                        .then(CommandManager.argument("player", StringArgumentType.string())
                            .executes(this::deleteContributor))))
                .then(CommandManager.literal("invite")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .then(CommandManager.argument("name", StringArgumentType.string())
                            .executes(this::invitePlayer))))
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("id", IntegerArgumentType.integer())
                        .then(CommandManager.argument("player", StringArgumentType.string())
                            .then(CommandManager.argument("note", StringArgumentType.string())
                                .executes(this::addContributor)))))
                .then(CommandManager.literal("list")
                    .then(CommandManager.argument("id", IntegerArgumentType.integer())
                        .executes(context -> ContribCommandHandler.listContributors(context)))
                    .then(CommandManager.literal("all")
                        .executes(context -> ContribCommandHandler.listAllContributions(context))))
                .then(CommandManager.literal("info")
                    .then(CommandManager.argument("id", IntegerArgumentType.integer())
                        .executes(context -> ContribCommandHandler.showContributionDetails(context)))));
        });
    }

    private int executeContribCommand(CommandContext<ServerCommandSource> context, String gameId, String note)
            throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        String type = StringArgumentType.getString(context, "type");
        String name = StringArgumentType.getString(context, "name");
        String creatorNote = StringArgumentType.getString(context, "note");

        try {
            // 保存贡献信息
            int contributionId = DatabaseManager.addContribution(
                name,
                type,
                gameId,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getWorld().getRegistryKey().getValue().toString(),
                player.getUuid()
            );

            // 添加创建者作为贡献者（level 1）
            DatabaseManager.addContributor(contributionId, player.getUuid(), creatorNote, null);

            // 如果有额外的贡献者，添加他们（level 2）
            if (gameId != null && note != null) {
                DatabaseManager.addContributor(contributionId, UUID.fromString(gameId), note, player.getUuid());
            }

            // 检查是否有更多贡献者
            for (int i = 2; i <= 5; i++) {
                try {
                    String additionalGameId = StringArgumentType.getString(context, "gameId" + i);
                    String additionalNote = StringArgumentType.getString(context, "note" + i);
                    if (additionalGameId != null && additionalNote != null) {
                        DatabaseManager.addContributor(contributionId, UUID.fromString(additionalGameId), additionalNote, player.getUuid());
                    }
                } catch (Exception e) {
                    // 如果没有更多参数，继续执行
                    break;
                }
            }

            // 通知附近玩家
            notifyNearbyPlayers(player, contributionId, name, type);

            source.sendMessage(Text.literal("§a内容已发布官网！").formatted(Formatting.GREEN));
            return 1;
        } catch (SQLException e) {
            source.sendMessage(Text.literal("§c记录内容时发生错误：" + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
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
            // 获取邀请者的UUID
            UUID inviterUuid = contribution.getInviterUuid();
            
            // 添加贡献者
            DatabaseManager.addContributor(
                contribution.getId(),
                player.getUuid(),
                player.getName().getString(),
                note,
                inviterUuid
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
            
            // 检查邀请者是否有权限邀请
            if (!ContribPermissionManager.canInviteContributor(inviter, contribution)) {
                source.sendMessage(Text.of("§c你没有权限邀请其他玩家参与此贡献"));
                return 0;
            }
            
            // 查找目标玩家
            PlayerEntity targetPlayer = server.getPlayerManager().getPlayer(playerName);
            if (targetPlayer == null) {
                source.sendMessage(Text.of("§c未找到玩家：" + playerName));
                return 0;
            }
            
            // 检查目标玩家是否已经是贡献者
            ContributorInfo existingContributor = DatabaseManager.getContributorInfo(contribution.getId(), targetPlayer.getUuid());
            if (existingContributor != null) {
                // 获取上级信息
                ContributorInfo superior = DatabaseManager.getContributorSuperior(contribution.getId(), targetPlayer.getUuid());
                String superiorInfo = superior != null ? 
                    String.format("（上级：%s，层级：%d）", superior.getPlayerName(), superior.getLevel()) : 
                    "（无上级）";
                
                source.sendMessage(Text.of(String.format("§c该玩家已经是贡献者，层级：%d %s", 
                    existingContributor.getLevel(), superiorInfo)));
                return 0;
            }
            
            // 设置邀请者UUID
            contribution.setInviterUuid(inviter.getUuid());
            
            // 发送邀请
            targetPlayer.sendMessage(Text.of("§a你收到了一个贡献邀请"));
            targetPlayer.sendMessage(Text.of("§a贡献名称：" + contributionName));
            targetPlayer.sendMessage(Text.of("§a邀请者：" + inviter.getName().getString()));
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

    private int addContributor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int contributionId = IntegerArgumentType.getInteger(context, "id");
        String playerName = StringArgumentType.getString(context, "player");
        String note = StringArgumentType.getString(context, "note");
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();
        
        try {
            // 检查贡献是否存在
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
            if (contribution == null) {
                source.sendMessage(Text.of("§c未找到贡献"));
                return 0;
            }
            
            // 检查玩家是否有权限直接添加贡献者
            if (!ContribPermissionManager.canDirectlyAddContributor(player, contribution)) {
                source.sendMessage(Text.of("§c你没有权限直接添加贡献者"));
                return 0;
            }
            
            // 查找目标玩家
            PlayerEntity targetPlayer = server.getPlayerManager().getPlayer(playerName);
            if (targetPlayer == null) {
                source.sendMessage(Text.of("§c未找到玩家：" + playerName));
                return 0;
            }
            
            // 检查目标玩家是否已经是贡献者
            ContributorInfo existingContributor = DatabaseManager.getContributorInfo(contributionId, targetPlayer.getUuid());
            if (existingContributor != null) {
                // 获取上级信息
                ContributorInfo superior = DatabaseManager.getContributorSuperior(contributionId, targetPlayer.getUuid());
                String superiorInfo = superior != null ? 
                    String.format("（上级：%s，层级：%d）", superior.getPlayerName(), superior.getLevel()) : 
                    "（无上级）";
                
                source.sendMessage(Text.of(String.format("§c该玩家已经是贡献者，层级：%d %s", 
                    existingContributor.getLevel(), superiorInfo)));
                return 0;
            }
            
            // 直接添加贡献者
            DatabaseManager.addContributor(
                contributionId,
                targetPlayer.getUuid(),
                targetPlayer.getName().getString(),
                note,
                player.getUuid()
            );
            
            source.sendMessage(Text.of("§a已成功添加贡献者：" + playerName));
            targetPlayer.sendMessage(Text.of("§a你已被添加为贡献者"));
            
        } catch (SQLException e) {
            LOGGER.error("添加贡献者失败", e);
            source.sendMessage(Text.of("§c添加贡献者失败"));
        }
        
        return 1;
    }
} 