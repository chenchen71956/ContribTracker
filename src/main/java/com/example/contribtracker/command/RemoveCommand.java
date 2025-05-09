package com.example.contribtracker.command;

import com.example.contribtracker.ContribPermissionManager;
import com.example.contribtracker.ContribTrackerMod;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.ContributorInfo;
import com.example.contribtracker.database.DatabaseManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import java.util.UUID;

/**
 * 删除贡献者命令类
 */
public class RemoveCommand implements BaseCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);

    @Override
    public LiteralArgumentBuilder<ServerCommandSource> register() {
        return CommandManager.literal("contribtracker")
            .then(CommandManager.literal("remove")
                .executes(this::showRemoveHelp)
                .then(CommandManager.argument("contributionId", IntegerArgumentType.integer(1))
                    .then(CommandManager.argument("playerName", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            ServerCommandSource source = context.getSource();
                            for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                                builder.suggest(player.getName().getString());
                            }
                            return builder.buildFuture();
                        })
                        .executes(this::removeContributor)
                    )
                )
            );
    }

    /**
     * 显示删除帮助信息
     */
    private int showRemoveHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        source.sendMessage(Text.of("§a使用方法: /contribtracker remove <贡献ID> <玩家名>"));
        return 1;
    }

    /**
     * 删除贡献者
     */
    private int removeContributor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        int contributionId = IntegerArgumentType.getInteger(context, "contributionId");
        String targetPlayerName = StringArgumentType.getString(context, "playerName");

        try {
            // 检查贡献是否存在
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
            if (contribution == null) {
                source.sendMessage(Text.of("§c找不到ID为 " + contributionId + " 的贡献"));
                return 0;
            }

            // 获取目标玩家UUID
            UUID targetUuid = null;
            ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(targetPlayerName);
            if (targetPlayer != null) {
                targetUuid = targetPlayer.getUuid();
            } else {
                // 尝试从离线玩家中查找
                targetUuid = DatabaseManager.getPlayerUuidByName(targetPlayerName, contributionId);
                if (targetUuid == null) {
                    source.sendMessage(Text.of("§c找不到玩家：" + targetPlayerName));
                    return 0;
                }
            }

            // 检查目标是否是贡献者
            ContributorInfo targetInfo = DatabaseManager.getContributorInfo(contributionId, targetUuid);
            if (targetInfo == null) {
                source.sendMessage(Text.of("§c玩家 " + targetPlayerName + " 不是该贡献的贡献者"));
                return 0;
            }

            // 检查操作者权限
            boolean canRemove = false;
            
            // 管理员可以删除任何人
            if (ContribPermissionManager.isAdmin(player)) {
                canRemove = true;
            } 
            // 贡献创建者可以删除任何人
            else if (DatabaseManager.isContributionCreator(contributionId, player.getUuid())) {
                canRemove = true;
            }
            // 一级贡献者可以删除任何人
            else {
                ContributorInfo removerInfo = DatabaseManager.getContributorInfo(contributionId, player.getUuid());
                if (removerInfo != null) {
                    if (removerInfo.getLevel() == 1) {
                        canRemove = true;
                    } 
                    // 二级及以下贡献者只能删除自己邀请的玩家
                    else if (targetInfo.getInviterUuid() != null && targetInfo.getInviterUuid().equals(player.getUuid())) {
                        canRemove = true;
                    }
                }
            }

            if (!canRemove) {
                source.sendMessage(Text.of("§c你没有权限删除该贡献者"));
                return 0;
            }

            // 执行删除
            DatabaseManager.deleteContributor(contributionId, targetUuid);
            source.sendMessage(Text.of("§a已从贡献中移除玩家：" + targetPlayerName));

            // 通知被删除的玩家
            if (targetPlayer != null) {
                targetPlayer.sendMessage(Text.of("§c你已被从贡献 ID:" + contributionId + " 中移除"));
            }

            return 1;
        } catch (SQLException e) {
            LOGGER.error("删除贡献者失败", e);
            source.sendMessage(Text.of("§c删除贡献者失败：" + e.getMessage()));
            return 0;
        }
    }
} 