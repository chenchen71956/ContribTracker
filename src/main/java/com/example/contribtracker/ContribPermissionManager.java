package com.example.contribtracker;

import com.example.contribtracker.database.Contribution;
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
        return player.hasPermissionLevel(2) || 
               contribution.getCreatorUuid().equals(player.getUuid());
    }

    /**
     * 检查玩家是否有权限删除贡献者
     * @param player 执行删除的玩家
     * @param contribution 贡献
     * @param targetContributorUuid 要删除的贡献者的UUID
     * @return true如果玩家是管理员或贡献的创建者
     */
    public static boolean canDeleteContributor(PlayerEntity player, Contribution contribution, UUID targetContributorUuid) {
        return player.hasPermissionLevel(2) || 
               contribution.getCreatorUuid().equals(player.getUuid());
    }
} 