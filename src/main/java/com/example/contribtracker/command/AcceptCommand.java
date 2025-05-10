package com.example.contribtracker.command;

import com.example.contribtracker.ContribTrackerMod;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.DatabaseManager;
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
                    .executes(this::acceptInvitation)
                )
            );
    }

    private int acceptInvitation(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        int contributionId = IntegerArgumentType.getInteger(context, "contributionId");
        UUID playerUuid = player.getUuid();

        // 检查是否有待处理的邀请
        Map<UUID, Contribution> pendingContributions = ContribTrackerMod.getPendingContributions();
        Contribution contribution = pendingContributions.get(playerUuid);

        if (contribution == null || contribution.getId() != contributionId) {
            source.sendMessage(Text.of("§c你没有收到该贡献的邀请"));
            return 0;
        }

        try {
            // 添加贡献者
            DatabaseManager.addContributor(
                contributionId,
                player.getUuid(),
                player.getName().getString(),
                "",  // 空字符串，没有note
                contribution.getInviterUuid()
            );

            // 移除待处理的邀请
            pendingContributions.remove(playerUuid);
            ContribTrackerMod.getContributionExpiryTimes().remove(playerUuid);

            source.sendMessage(Text.of("§a你已成功加入贡献"));
            return 1;
        } catch (SQLException e) {
            LOGGER.error("接受邀请失败", e);
            source.sendMessage(Text.of("§c接受邀请失败：" + e.getMessage()));
            return 0;
        }
    }
} 