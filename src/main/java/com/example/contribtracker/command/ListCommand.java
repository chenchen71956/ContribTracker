package com.example.contribtracker.command;

import com.example.contribtracker.ContribTrackerMod;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.DatabaseManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * 列出所有贡献命令
 * 任何人都可以执行此命令
 * 格式为：贡献类型 | 贡献名称 | 创建人 | xyz坐标
 */
public class ListCommand implements BaseCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);

    @Override
    public LiteralArgumentBuilder<ServerCommandSource> register() {
        return CommandManager.literal("contribtracker")
            .then(CommandManager.literal("list")
                .executes(this::listContributions)
            );
    }

    /**
     * 列出所有贡献
     */
    private int listContributions(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();

        try {
            // 获取所有贡献
            List<Contribution> contributions = DatabaseManager.getAllContributions();

            if (contributions.isEmpty()) {
                source.sendMessage(Text.of("§c目前还没有任何贡献记录"));
                return 0;
            }

            // 发送表头
            source.sendMessage(Text.of("§a========== 贡献列表 =========="));
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
            source.sendMessage(Text.of("§7共显示 " + contributions.size() + " 条贡献记录"));

            return 1;
        } catch (SQLException e) {
            LOGGER.error("获取贡献列表失败", e);
            source.sendMessage(Text.of("§c获取贡献列表失败：" + e.getMessage()));
            return 0;
        }
    }
} 