package com.example.contribtracker.command;

import com.example.contribtracker.ContribPermissionManager;
import com.example.contribtracker.ContribTrackerMod;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.ContributorInfo;
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

/**
 * 删除贡献命令
 * - 二级及以下不允许删除贡献
 * - 一级贡献者可以删除自己的贡献
 * - admin可以删除任意贡献
 */
public class DeleteCommand implements BaseCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);

    @Override
    public LiteralArgumentBuilder<ServerCommandSource> register() {
        return CommandManager.literal("contribtracker")
            .then(CommandManager.literal("delete")
                .then(CommandManager.argument("contributionId", IntegerArgumentType.integer(1))
                    .executes(this::deleteContribution)
                )
            );
    }

    /**
     * 删除贡献
     */
    private int deleteContribution(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        int contributionId = IntegerArgumentType.getInteger(context, "contributionId");

        try {
            // 获取贡献信息
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
            if (contribution == null) {
                source.sendMessage(Text.of("§c找不到ID为 " + contributionId + " 的贡献"));
                return 0;
            }

            // 检查删除权限
            boolean canDelete = false;
            
            // 检查是否是管理员
            if (ContribPermissionManager.isAdmin(player)) {
                canDelete = true;
            } 
            // 检查是否是一级贡献者且是贡献创建者
            else {
                // 获取玩家在该贡献中的角色
                ContributorInfo contributorInfo = DatabaseManager.getContributorInfo(contributionId, player.getUuid());
                if (contributorInfo != null && contributorInfo.getLevel() == 1) {
                    // 检查是否是创建者
                    if (contribution.getCreatorUuid().equals(player.getUuid())) {
                        canDelete = true;
                    }
                }
            }

            if (!canDelete) {
                source.sendMessage(Text.of("§c你没有权限删除该贡献"));
                return 0;
            }

            // 执行删除操作
            DatabaseManager.deleteContribution(contributionId);
            source.sendMessage(Text.of("§a已成功删除ID为 " + contributionId + " 的贡献"));
            return 1;
        } catch (SQLException e) {
            LOGGER.error("删除贡献失败", e);
            source.sendMessage(Text.of("§c删除贡献失败：" + e.getMessage()));
            return 0;
        }
    }
} 