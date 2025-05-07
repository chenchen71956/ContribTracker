package cn.kongchengli.cn.contribtracker;

import cn.kongchengli.cn.contribtracker.database.Contribution;
import cn.kongchengli.cn.contribtracker.database.DatabaseManager;
import net.minecraft.entity.player.PlayerEntity;
import java.sql.SQLException;
import java.util.UUID;

public class ContribPermissionManager {
    /**
     * 检查玩家是否有权限删除贡献
     * @param player 执行删除的玩家
     * @param contribution 要删除的贡献
     * @return true如果玩家是管理员或贡献的创建者
     */
    public static boolean canDeleteContribution(PlayerEntity player, Contribution contribution) {
        return player.hasPermissionLevel(2) || 
               contribution.getCreatorUuid().equals(player.getUuid());
    }

    /**
     * 检查玩家是否有权限删除贡献者
     * @param player 执行删除的玩家
     * @param contribution 贡献
     * @param targetContributorUuid 要删除的贡献者的UUID
     * @return true如果玩家是管理员、贡献的创建者，或者玩家是目标贡献者的上级
     */
    public static boolean canDeleteContributor(PlayerEntity player, Contribution contribution, UUID targetContributorUuid) {
        try {
            // 管理员和创建者可以删除任何贡献者
            if (player.hasPermissionLevel(2) || contribution.getCreatorUuid().equals(player.getUuid())) {
                return true;
            }
            
            // 检查玩家是否是目标贡献者的上级
            return DatabaseManager.canManageContributor(contribution.getId(), player.getUuid(), targetContributorUuid);
        } catch (SQLException e) {
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
            // 管理员和创建者可以邀请任何人
            if (player.hasPermissionLevel(2) || contribution.getCreatorUuid().equals(player.getUuid())) {
                return true;
            }
            
            // 检查玩家是否是现有贡献者
            return DatabaseManager.isContributor(contribution.getId(), player.getUuid());
        } catch (SQLException e) {
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
            // 管理员和创建者可以直接添加贡献者
            if (player.hasPermissionLevel(2) || contribution.getCreatorUuid().equals(player.getUuid())) {
                return true;
            }
            
            // 检查玩家是否是一级贡献者
            return DatabaseManager.isLevelOneContributor(contribution.getId(), player.getUuid());
        } catch (SQLException e) {
            return false;
        }
    }
} 