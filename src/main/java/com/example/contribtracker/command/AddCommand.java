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

    @Override
    public LiteralArgumentBuilder<ServerCommandSource> register() {
        LiteralArgumentBuilder<ServerCommandSource> builder = CommandManager.literal("contribtracker")
            .then(CommandManager.literal("add")
                // 针对最新贡献添加贡献者或发邀请
                .then(createPlayerArgument(1))
                // 针对特定贡献ID添加贡献者或发邀请
                .then(CommandManager.argument("contributionId", IntegerArgumentType.integer(1))
                    .then(createPlayerArgument(1, true))
                )
            );
            
        // 为每种贡献类型创建子命令
        for (String type : CONTRIBUTION_TYPES) {
            builder.then(CommandManager.literal("add")
                .then(CommandManager.literal(type)
                    .then(CommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(context -> createContributionWithType(context, type))
                    )
                )
            );
        }
            
        return builder;
    }

    /**
     * 递归创建玩家参数，针对最新贡献
     */
    private ArgumentBuilder<ServerCommandSource, ?> createPlayerArgument(int index) {
        return createPlayerArgument(index, false);
    }

    /**
     * 递归创建玩家参数，支持无限添加
     * @param index 参数索引
     * @param needContributionId 是否需要贡献ID
     */
    private ArgumentBuilder<ServerCommandSource, ?> createPlayerArgument(int index, boolean needContributionId) {
        String argName = "player" + index;
        
        // 设置最大递归深度，最多添加10个玩家
        final int MAX_PLAYERS = 10;
        
        ArgumentBuilder<ServerCommandSource, ?> builder = CommandManager.argument(argName, StringArgumentType.word())
            .suggests((context, suggestionsBuilder) -> {
                ServerCommandSource source = context.getSource();
                for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                    suggestionsBuilder.suggest(player.getName().getString());
                }
                return suggestionsBuilder.buildFuture();
            })
            .executes(context -> {
                if (needContributionId) {
                    return processContributorsWithId(context, index);
                } else {
                    return processContributors(context, index);
                }
            });
        
        // 只有当索引小于最大限制时，才继续递归添加下一个玩家参数
        if (index < MAX_PLAYERS) {
            builder = builder.then(createPlayerArgument(index + 1, needContributionId));
        }
        
        return builder;
    }

    /**
     * 创建新贡献
     */
    private int createContribution(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        try {
            // 创建贡献
            DatabaseManager.addContribution(
                "新贡献", // 默认名称，可以后续修改
                "默认类型",
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getWorld().getRegistryKey().getValue().toString(),
                player.getUuid()
            );

            int contributionId = DatabaseManager.getLastInsertId();

            // 添加创建者作为贡献者（level 1）
            DatabaseManager.addContributor(contributionId, player.getUuid(), player.getName().getString(), "", null);

            // 获取贡献对象
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
            if (contribution != null) {
                // 通知附近玩家
                notifyNearbyPlayers(player, contribution);
                // 广播WebSocket消息
                WebSocketHandler.broadcastContributionUpdate(contribution);
            }

            source.sendMessage(Text.of("§a已创建新贡献（ID: " + contributionId + "）"));
            return 1;
        } catch (SQLException e) {
            LOGGER.error("创建贡献失败", e);
            source.sendMessage(Text.of("§c创建贡献失败：" + e.getMessage()));
            return 0;
        }
    }

    /**
     * 创建带类型的贡献
     */
    private int createContributionWithType(CommandContext<ServerCommandSource> context, String type) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        
        String name = StringArgumentType.getString(context, "name");

        try {
            // 创建贡献
            DatabaseManager.addContribution(
                name,
                type,
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getWorld().getRegistryKey().getValue().toString(),
                player.getUuid()
            );

            int contributionId = DatabaseManager.getLastInsertId();

            // 添加创建者作为贡献者（level 1）
            DatabaseManager.addContributor(contributionId, player.getUuid(), player.getName().getString(), "", null);

            // 获取贡献对象
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
            if (contribution != null) {
                // 通知附近玩家
                notifyNearbyPlayers(player, contribution);
                // 广播WebSocket消息
                WebSocketHandler.broadcastContributionUpdate(contribution);
            }

            source.sendMessage(Text.of("§a已创建新贡献（ID: " + contributionId + "，类型: " + type + "，名称: " + name + "）"));
            return 1;
        } catch (SQLException e) {
            LOGGER.error("创建贡献失败", e);
            source.sendMessage(Text.of("§c创建贡献失败：" + e.getMessage()));
            return 0;
        }
    }

    /**
     * 处理针对最新贡献的添加或邀请
     */
    private int processContributors(CommandContext<ServerCommandSource> context, int maxIndex) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        try {
            // 获取创建者最后创建的贡献
            List<Contribution> contributions = DatabaseManager.getAllContributionsByCreator(player.getUuid());
            if (contributions.isEmpty()) {
                source.sendMessage(Text.of("§c你还没有创建过贡献"));
                return 0;
            }

            Contribution contribution = contributions.get(0); // 获取最新的贡献
            return processContributorsById(context, maxIndex, player, contribution.getId());
        } catch (SQLException e) {
            LOGGER.error("处理贡献者失败", e);
            source.sendMessage(Text.of("§c处理贡献者失败：" + e.getMessage()));
            return 0;
        }
    }

    /**
     * 处理针对特定贡献ID的添加或邀请
     */
    private int processContributorsWithId(CommandContext<ServerCommandSource> context, int maxIndex) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        int contributionId = IntegerArgumentType.getInteger(context, "contributionId");
        
        try {
            // 检查贡献是否存在
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
            if (contribution == null) {
                source.sendMessage(Text.of("§c找不到ID为 " + contributionId + " 的贡献"));
                return 0;
            }
            
            // 检查是否是贡献者
            ContributorInfo contributorInfo = DatabaseManager.getContributorInfo(contributionId, player.getUuid());
            boolean isCreator = contribution.getCreatorUuid().equals(player.getUuid());
            
            if (!isCreator && contributorInfo == null) {
                source.sendMessage(Text.of("§c你不是该贡献的贡献者，无法添加或邀请其他玩家"));
                return 0;
            }
            
            return processContributorsById(context, maxIndex, player, contributionId);
        } catch (SQLException e) {
            LOGGER.error("处理贡献者失败", e);
            source.sendMessage(Text.of("§c处理贡献者失败：" + e.getMessage()));
            return 0;
        }
    }

    /**
     * 根据贡献ID处理添加贡献者或发送邀请
     */
    private int processContributorsById(CommandContext<ServerCommandSource> context, int maxIndex, ServerPlayerEntity player, int contributionId) throws SQLException {
        ServerCommandSource source = context.getSource();
        
        // 检查权限 - 确定是哪个级别的贡献者
        boolean isAdmin = ContribPermissionManager.isAdmin(player);
        boolean isCreator = DatabaseManager.isContributionCreator(contributionId, player.getUuid());
        int contributorLevel = 0; // 默认不是贡献者

        // 获取贡献者信息
        ContributorInfo contributorInfo = null;
        if (!isCreator && !isAdmin) {
            contributorInfo = DatabaseManager.getContributorInfo(contributionId, player.getUuid());
            if (contributorInfo != null) {
                contributorLevel = contributorInfo.getLevel();
            }
        } else if (isCreator) {
            contributorLevel = 1; // 创建者视为一级贡献者
        }

        // 管理员或一级贡献者可以直接添加
        boolean canDirectlyAdd = isAdmin || isCreator || contributorLevel == 1;
        
        // 记录处理结果
        List<String> addedPlayers = new ArrayList<>();
        List<String> invitedPlayers = new ArrayList<>();
        List<String> existingPlayers = new ArrayList<>();
        List<String> notFoundPlayers = new ArrayList<>();

        Contribution contribution = DatabaseManager.getContributionById(contributionId);

        // 获取所有玩家名称并处理
        for (int i = 1; i <= maxIndex; i++) {
            String playerName = StringArgumentType.getString(context, "player" + i);
            ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(playerName);
            
            if (targetPlayer == null) {
                notFoundPlayers.add(playerName);
                continue;
            }
            
            // 检查目标玩家是否已经是贡献者
            ContributorInfo existingContributor = DatabaseManager.getContributorInfo(contributionId, targetPlayer.getUuid());
            if (existingContributor != null) {
                existingPlayers.add(targetPlayer.getName().getString());
                continue;
            }

            // 根据权限级别决定添加方式
            if (canDirectlyAdd) {
                // 直接添加贡献者
                DatabaseManager.addContributor(
                    contributionId,
                    targetPlayer.getUuid(),
                    targetPlayer.getName().getString(),
                    "",  // 空字符串，没有note
                    player.getUuid()
                );
                
                addedPlayers.add(targetPlayer.getName().getString());
                targetPlayer.sendMessage(Text.of("§a你已被添加为贡献者（级别：" + (contributorLevel + 1) + "）"));
            } else {
                // 发送邀请
                sendInvitation(targetPlayer, player, contribution, contributorLevel);
                invitedPlayers.add(targetPlayer.getName().getString());
            }
        }

        // 发送反馈信息
        if (!addedPlayers.isEmpty()) {
            source.sendMessage(Text.of("§a已直接添加贡献者：" + String.join(", ", addedPlayers)));
        }
        
        if (!invitedPlayers.isEmpty()) {
            source.sendMessage(Text.of("§a已发送邀请给：" + String.join(", ", invitedPlayers)));
        }
        
        if (!existingPlayers.isEmpty()) {
            source.sendMessage(Text.of("§c以下玩家已经是贡献者：" + String.join(", ", existingPlayers)));
        }
        
        if (!notFoundPlayers.isEmpty()) {
            source.sendMessage(Text.of("§c未找到以下玩家：" + String.join(", ", notFoundPlayers)));
        }

        return 1;
    }

    /**
     * 发送邀请给目标玩家
     */
    private void sendInvitation(ServerPlayerEntity targetPlayer, ServerPlayerEntity inviter, Contribution contribution, int inviterLevel) {
        // 设置邀请者信息
        contribution.setInviterUuid(inviter.getUuid());
        contribution.setInviterLevel(inviterLevel);
        
        // 发送邀请消息
        targetPlayer.sendMessage(Text.of("§a你收到了一个贡献邀请"));
        targetPlayer.sendMessage(Text.of("§a贡献名称：" + contribution.getName()));
        targetPlayer.sendMessage(Text.of("§a贡献ID：" + contribution.getId()));
        targetPlayer.sendMessage(Text.of("§a邀请者：" + inviter.getName().getString()));
        
        // 创建接受和拒绝按钮
        Text acceptText = Text.literal("§2[接受]")
            .styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/contribtracker accept " + contribution.getId()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("点击接受邀请")))
            );
        
        Text rejectText = Text.literal("§c[拒绝]")
            .styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/contribtracker reject " + contribution.getId()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("点击拒绝邀请")))
            );
        
        targetPlayer.sendMessage(Text.literal("").append(acceptText).append(" ").append(rejectText));
        
        // 存储邀请信息
        Map<UUID, Contribution> pendingContributions = ContribTrackerMod.getPendingContributions();
        Map<UUID, Long> contributionExpiryTimes = ContribTrackerMod.getContributionExpiryTimes();
        
        pendingContributions.put(targetPlayer.getUuid(), contribution);
        contributionExpiryTimes.put(targetPlayer.getUuid(), System.currentTimeMillis());
    }

    /**
     * 通知附近玩家
     */
    private void notifyNearbyPlayers(ServerPlayerEntity player, Contribution contribution) {
        Vec3d pos = player.getPos();
        Box box = new Box(pos.add(-32, -32, -32), pos.add(32, 32, 32));
        List<PlayerEntity> nearbyPlayers = player.getWorld().getEntitiesByType(
            net.minecraft.entity.EntityType.PLAYER,
            box,
            p -> p != player
        );

        for (PlayerEntity nearbyPlayer : nearbyPlayers) {
            nearbyPlayer.sendMessage(Text.of("§a你周围有人发布了一个新的贡献"));
            nearbyPlayer.sendMessage(Text.of("§a贡献者：" + player.getName().getString()));
            nearbyPlayer.sendMessage(Text.of("§a贡献ID：" + contribution.getId()));
            
            // 创建接受邀请按钮
            Text acceptText = Text.literal("§2[加入贡献]")
                .styled(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/contribtracker accept " + contribution.getId()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("点击加入贡献")))
                );
            
            nearbyPlayer.sendMessage(acceptText);
            
            // 将附近玩家添加到待邀请列表
            Map<UUID, Contribution> pendingContributions = ContribTrackerMod.getPendingContributions();
            Map<UUID, Long> contributionExpiryTimes = ContribTrackerMod.getContributionExpiryTimes();
            
            pendingContributions.put(nearbyPlayer.getUuid(), contribution);
            contributionExpiryTimes.put(nearbyPlayer.getUuid(), System.currentTimeMillis());
        }
    }
} 