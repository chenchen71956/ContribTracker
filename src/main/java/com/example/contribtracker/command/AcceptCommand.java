package com.example.contribtracker.command;

import com.example.contribtracker.ContribTrackerMod;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.DatabaseManager;
import com.example.contribtracker.websocket.WebSocketHandler;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public class AcceptCommand implements BaseCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);

    @Override
    public LiteralArgumentBuilder<ServerCommandSource> register() {
        return CommandManager.literal("contribtracker")
            .then(CommandManager.literal("accept")
                .then(CommandManager.argument("contributionId", IntegerArgumentType.integer(1))
                    .executes(this::acceptContribution)
                )
            );
    }

    private int acceptContribution(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        int contributionId = IntegerArgumentType.getInteger(context, "contributionId");
        UUID playerUuid = player.getUuid();

        try {
            // 检查贡献是否存在
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
            if (contribution == null) {
                source.sendMessage(Text.of("§c找不到ID为 " + contributionId + " 的贡献"));
                return 0;
            }

            // 检查是否已经是贡献者
            if (DatabaseManager.isContributor(contributionId, playerUuid)) {
                source.sendMessage(Text.of("§c你已经是该贡献的贡献者"));
                return 0;
            }

            // 检查是否有待处理的邀请
            Map<UUID, Contribution> pendingContributions = ContribTrackerMod.getPendingContributions();
            Contribution pendingContribution = pendingContributions.get(playerUuid);
            
            if (pendingContribution == null || pendingContribution.getId() != contributionId) {
                source.sendMessage(Text.of("§c你没有该贡献的邀请"));
                return 0;
            }

            // 获取邀请者信息
            UUID inviterUuid = pendingContribution.getInviterUuid();
            int inviterLevel = pendingContribution.getInviterLevel();

            // 添加贡献者
            DatabaseManager.addContributor(
                contributionId,
                playerUuid,
                player.getName().getString(),
                "",  // 空字符串，没有note
                inviterUuid
            );

            // 移除待处理的邀请
            pendingContributions.remove(playerUuid);
            ContribTrackerMod.getContributionExpiryTimes().remove(playerUuid);

            // 获取更新后的贡献信息
            Contribution updatedContribution = DatabaseManager.getContributionById(contributionId);
            if (updatedContribution != null) {
                // 广播WebSocket消息
                WebSocketHandler.broadcastContributionUpdate(updatedContribution);
            }

            source.sendMessage(Text.of("§a已成功加入贡献"));
            return 1;
        } catch (SQLException e) {
            LOGGER.error("接受贡献失败", e);
            source.sendMessage(Text.of("§c接受贡献失败：" + e.getMessage()));
            return 0;
        }
    }
} 