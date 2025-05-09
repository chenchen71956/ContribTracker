package com.example.contribtracker.command;

import com.example.contribtracker.ContribTrackerMod;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.DatabaseManager;
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
import java.util.List;

/**
 * 显示附近贡献命令
 * 任何人都可以执行此命令
 * 显示半径32格内的所有贡献，格式与list命令一致
 */
public class NearCommand implements BaseCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);
    private static final double SEARCH_RADIUS = 32.0;

    @Override
    public LiteralArgumentBuilder<ServerCommandSource> register() {
        return CommandManager.literal("contribtracker")
            .then(CommandManager.literal("near")
                .executes(this::listNearbyContributions)
            )
            .then(CommandManager.literal("n")
                .executes(this::listNearbyContributions)
            );
    }

    /**
     * 列出附近贡献
     */
    private int listNearbyContributions(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        
        if (player == null) {
            source.sendMessage(Text.of("§c此命令只能由玩家执行"));
            return 0;
        }

        try {
            // 获取玩家当前位置
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            
            // 获取附近贡献
            List<Contribution> contributions = DatabaseManager.getNearbyContributions(x, y, z, SEARCH_RADIUS);

            if (contributions.isEmpty()) {
                source.sendMessage(Text.of("§c附近" + SEARCH_RADIUS + "格范围内没有任何贡献记录"));
                return 0;
            }

            // 发送表头
            source.sendMessage(Text.of("§a========== 附近贡献列表（" + SEARCH_RADIUS + "格范围）=========="));
            source.sendMessage(Text.of("§7ID | 贡献类型 | 贡献名称 | 创建人 | 坐标"));
            source.sendMessage(Text.of("§7------------------------------------"));

            // 发送贡献列表
            for (Contribution contribution : contributions) {
                String coordinates = String.format("%.1f, %.1f, %.1f", 
                    contribution.getX(), 
                    contribution.getY(), 
                    contribution.getZ()
                );

                source.sendMessage(Text.of(String.format(
                    "§a%d §7| §f%s §7| §f%s §7| §f%s §7| §f%s", 
                    contribution.getId(),
                    contribution.getType(),
                    contribution.getName(),
                    contribution.getCreatorName(),
                    coordinates
                )));
            }

            source.sendMessage(Text.of("§a=============================="));
            source.sendMessage(Text.of("§7共显示 " + contributions.size() + " 条附近贡献记录"));

            return 1;
        } catch (SQLException e) {
            LOGGER.error("获取附近贡献列表失败", e);
            source.sendMessage(Text.of("§c获取附近贡献列表失败：" + e.getMessage()));
            return 0;
        }
    }
} 