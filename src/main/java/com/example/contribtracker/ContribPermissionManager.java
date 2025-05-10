package com.example.contribtracker;

import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.DatabaseManager;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContribPermissionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);

    // 管理员UUID列表，可以在代码中硬编码，也可以从配置文件加载
    private static final List<UUID> ADMIN_UUIDS = Arrays.asList(
        // 这里可以添加管理员的UUID
        // 例如: UUID.fromString("00000000-0000-0000-0000-000000000000")
        UUID.fromString("a98ea164-344b-4211-9684-b65d1dfb74e7")
    );
    
    // 管理员玩家名列表，可以在代码中硬编码，也可以从配置文件加载
    private static final List<String> ADMIN_NAMES = Arrays.asList(
    );
    
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

        // 检查UUID是否在管理员列表中
        UUID playerUuid = player.getUuid();
        if (isAdminUuid(playerUuid)) {
            return true;
        }

        // 检查玩家名是否在管理员名单中
        String playerName = player.getName().getString();
        if (isAdminName(playerName)) {
            return true;
        }

        return false;
    }

    /**
     * 检查UUID是否是管理员
     * @param uuid 要检查的UUID
     * @return 是否是管理员
     */
    public static boolean isAdminUuid(UUID uuid) {
        return ADMIN_UUIDS.contains(uuid);
    }

    /**
     * 检查玩家名是否是管理员
     * @param playerName 要检查的玩家名
     * @return 是否是管理员
     */
    public static boolean isAdminName(String playerName) {
        // 开发环境中，以'Dev'开头的玩家名视为管理员
        if (playerName.startsWith("Dev")) {
            return true;
        }

        return ADMIN_NAMES.contains(playerName);
    }
} 