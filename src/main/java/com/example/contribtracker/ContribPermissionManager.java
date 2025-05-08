package com.example.contribtracker;

import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.DatabaseManager;
import net.minecraft.entity.player.PlayerEntity;
import java.util.UUID;

public class ContribPermissionManager {
    /**
     * 检查玩家是否有权限删除贡献
     * @param player 执行删除的玩家
     * @param contribution 要删除的贡献
     * @return true如果玩家是管理员或贡献的创建者
     */
    public static boolean canDeleteContribution(PlayerEntity player, Contribution contribution) {
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
    public static boolean canDeleteContributor(PlayerEntity player, Contribution contribution, UUID targetUuid) {
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
    public static boolean canInviteContributor(PlayerEntity player, Contribution contribution) {
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
    public static boolean canDirectlyAddContributor(PlayerEntity player, Contribution contribution) {
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
    
    private static boolean isAdmin(PlayerEntity player) {
        // TODO: 实现管理员检查逻辑
        return false;
    }
} 