package com.example.contribtracker;

import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.ContributorInfo;
import com.example.contribtracker.database.DatabaseManager;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.UUID;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContribPermissionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);

    /**
     * 检查玩家是否有权限删除贡献
     * @param player 执行删除的玩家
     * @param contribution 要删除的贡献
     * @return true如果玩家是管理员或贡献的创建者
     */
    public static boolean canDeleteContribution(ServerPlayerEntity player, Contribution contribution) {
        try {
            // 检查是否是管理员
            if (isAdmin(player)) {
                return true;
            }
            
            // 检查是否是创建者
            return contribution.getCreatorUuid().equals(player.getUuid());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查玩家是否有权限删除贡献者
     * @param player 执行删除的玩家
     * @param contribution 贡献
     * @param targetUuid 要删除的贡献者的UUID
     * @return true如果玩家是管理员、贡献的创建者，或者玩家是目标贡献者的上级
     */
    public static boolean canDeleteContributor(ServerPlayerEntity player, Contribution contribution, UUID targetUuid) {
        try {
            // 检查是否是管理员
            if (isAdmin(player)) {
                return true;
            }
            
            // 检查是否是创建者
            if (contribution.getCreatorUuid().equals(player.getUuid())) {
                return true;
            }
            
            // 检查是否有权限管理目标贡献者
            return DatabaseManager.canManageContributor(contribution.getId(), player.getUuid(), targetUuid);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查玩家是否有权限邀请新贡献者
     * @param player 执行邀请的玩家
     * @param contribution 贡献
     * @return true如果玩家是管理员、贡献的创建者，或者是现有贡献者
     */
    public static boolean canInviteContributor(ServerPlayerEntity player, Contribution contribution) {
        try {
            // 检查是否是管理员
            if (isAdmin(player)) {
                return true;
            }
            
            // 检查是否是创建者
            if (contribution.getCreatorUuid().equals(player.getUuid())) {
                return true;
            }
            
            // 检查是否是贡献者
            return DatabaseManager.isContributor(contribution.getId(), player.getUuid());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查玩家是否有权限直接添加贡献者（不需要邀请流程）
     * @param player 执行添加的玩家
     * @param contribution 贡献
     * @return true如果玩家是管理员、贡献的创建者，或者是一级贡献者
     */
    public static boolean canDirectlyAddContributor(ServerPlayerEntity player, Contribution contribution) {
        try {
            // 检查是否是管理员
            if (isAdmin(player)) {
                return true;
            }
            
            // 检查是否是创建者
            if (contribution.getCreatorUuid().equals(player.getUuid())) {
                return true;
            }
            
            // 检查是否是一级贡献者
            return DatabaseManager.isLevelOneContributor(contribution.getId(), player.getUuid());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 获取玩家在贡献中的级别
     * @param contributionId 贡献ID
     * @param playerUuid 玩家UUID
     * @return 玩家级别，如果不是贡献者则返回-1
     */
    public static int getContributorLevel(long contributionId, UUID playerUuid) {
        try {
            ContributorInfo info = DatabaseManager.getContributorInfo((int)contributionId, playerUuid);
            if (info != null) {
                return info.getLevel();
            }
            return -1;
        } catch (SQLException e) {
            LOGGER.error("获取贡献者级别时发生错误", e);
            return -1;
        }
    }
    
    /**
     * 检查玩家是否是管理员
     * @param player 要检查的玩家
     * @return 是否是管理员
     */
    public static boolean isAdmin(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }

        // 检查是否是服务器OP
        if (player.hasPermissionLevel(4)) {
            return true;
        }

        // 开发环境中，以'Dev'开头的玩家名视为管理员
        String playerName = player.getName().getString();
        if (playerName.startsWith("Dev")) {
            return true;
        }

        return false;
    }
} 