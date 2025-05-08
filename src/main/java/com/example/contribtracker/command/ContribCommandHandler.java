package com.example.contribtracker.command;

import com.example.contribtracker.database.DatabaseManager;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.ContributorInfo;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class ContribCommandHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("ContribTracker");

    /**
     * 显示指定贡献的所有贡献者列表
     */
    public static int listContributors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int contributionId = IntegerArgumentType.getInteger(context, "id");
        ServerCommandSource source = context.getSource();
        
        try {
            // 获取贡献信息
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
            if (contribution == null) {
                source.sendMessage(Text.of("§c未找到贡献"));
                return 0;
            }
            
            // 获取所有贡献者信息
            List<ContributorInfo> contributors = com.example.contribtracker.database.DatabaseManager.getContributorsByContributionId(contributionId);
            if (contributors.isEmpty()) {
                source.sendMessage(Text.of("§c该贡献暂无贡献者"));
                return 0;
            }
            
            // 显示贡献信息
            source.sendMessage(Text.of(String.format("§a贡献信息：%s（ID：%d）", 
                contribution.getName(), contributionId)));
            source.sendMessage(Text.of("§a贡献者列表："));
            
            // 显示每个贡献者的信息
            for (ContributorInfo contributor : contributors) {
                // 获取上级信息
                ContributorInfo superior = DatabaseManager.getContributorSuperior(
                    contributionId, contributor.getPlayerUuid());
                String superiorInfo = superior != null ? 
                    String.format("（上级：%s，层级：%d）", superior.getPlayerName(), superior.getLevel()) : 
                    "（无上级）";
                
                source.sendMessage(Text.of(String.format("§a- %s（层级：%d）%s", 
                    contributor.getPlayerName(), 
                    contributor.getLevel(),
                    superiorInfo)));
            }
            
        } catch (SQLException e) {
            LOGGER.error("获取贡献者列表失败", e);
            source.sendMessage(Text.of("§c获取贡献者列表失败"));
        }
        
        return 1;
    }

    /**
     * 显示贡献的详细信息
     */
    public static int showContributionDetails(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int contributionId = IntegerArgumentType.getInteger(context, "id");
        ServerCommandSource source = context.getSource();
        
        try {
            // 获取贡献信息
            Contribution contribution = DatabaseManager.getContributionById(contributionId);
            if (contribution == null) {
                source.sendMessage(Text.of("§c未找到贡献"));
                return 0;
            }
            
            // 显示基本信息
            source.sendMessage(Text.of("§a=== 贡献详细信息 ==="));
            source.sendMessage(Text.of(String.format("§aID：%d", contribution.getId())));
            source.sendMessage(Text.of(String.format("§a名称：%s", contribution.getName())));
            source.sendMessage(Text.of(String.format("§a类型：%s", contribution.getType())));
            source.sendMessage(Text.of(String.format("§a创建者：%s", contribution.getCreatorName())));
            source.sendMessage(Text.of(String.format("§a创建时间：%s", contribution.getCreatedAt())));
            source.sendMessage(Text.of(String.format("§a位置：%.1f, %.1f, %.1f", 
                contribution.getX(), contribution.getY(), contribution.getZ())));
            source.sendMessage(Text.of(String.format("§a世界：%s", contribution.getWorld())));
            
            // 获取并显示贡献者信息
            List<ContributorInfo> contributors = DatabaseManager.getContributorsByContributionId(contributionId);
            if (!contributors.isEmpty()) {
                source.sendMessage(Text.of("§a贡献者列表："));
                for (ContributorInfo contributor : contributors) {
                    // 获取上级信息
                    ContributorInfo superior = DatabaseManager.getContributorSuperior(
                        contributionId, contributor.getPlayerUuid());
                    String superiorInfo = superior != null ? 
                        String.format("（上级：%s，层级：%d）", superior.getPlayerName(), superior.getLevel()) : 
                        "（无上级）";
                    
                    source.sendMessage(Text.of(String.format("§a- %s（层级：%d）%s", 
                        contributor.getPlayerName(), 
                        contributor.getLevel(),
                        superiorInfo)));
                }
            }
            
        } catch (SQLException e) {
            LOGGER.error("获取贡献详情失败", e);
            source.sendMessage(Text.of("§c获取贡献详情失败"));
        }
        
        return 1;
    }

    /**
     * 显示所有贡献列表
     */
    public static int listAllContributions(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        
        try {
            // 获取所有贡献信息
            List<Contribution> contributions = DatabaseManager.getAllContributions();
            if (contributions.isEmpty()) {
                source.sendMessage(Text.of("§c暂无任何贡献"));
                return 0;
            }
            
            // 显示贡献列表
            source.sendMessage(Text.of("§a=== 所有贡献列表 ==="));
            for (Contribution contribution : contributions) {
                // 获取贡献者数量
                int contributorCount = DatabaseManager.getContributorCount(contribution.getId());
                
                source.sendMessage(Text.of(String.format("§a| %d | %s | %s | 贡献者：%d人 |", 
                    contribution.getId(),
                    contribution.getName(),
                    contribution.getCreatorName(),
                    contributorCount)));
            }
            
        } catch (SQLException e) {
            LOGGER.error("获取贡献列表失败", e);
            source.sendMessage(Text.of("§c获取贡献列表失败"));
        }
        
        return 1;
    }
} 