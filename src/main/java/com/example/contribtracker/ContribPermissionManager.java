package com.example.contribtracker;

import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.DatabaseManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;

public class ContribPermissionManager {
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
    
    /**
     * 检查玩家是否是管理员
     * @param player 玩家
     * @return true如果玩家是管理员
     */
    public static boolean isAdmin(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        
        // 检查是否是OP
        if (player instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            if (serverPlayer.hasPermissionLevel(4)) { // OP级别 4 对应最高权限
                ContribTrackerMod.debug(player.getName().getString() + " 是服务器OP，判定为管理员");
                return true;
            }
        }
        
        // 检查UUID是否在管理员列表中
        if (ADMIN_UUIDS.contains(player.getUuid())) {
            ContribTrackerMod.debug(player.getName().getString() + " 的UUID在管理员列表中，判定为管理员");
            return true;
        }
        
        // 检查玩家名是否在管理员列表中
        String playerName = player.getName().getString();
        if (ADMIN_NAMES.contains(playerName)) {
            ContribTrackerMod.debug(playerName + " 在管理员名单中，判定为管理员");
            return true;
        }
        
        // 如果在开发环境中，可以通过特定的玩家名判断为管理员（用于测试）
        if (isDevelopmentEnvironment() && playerName.startsWith("Dev")) {
            ContribTrackerMod.debug("开发环境中，" + playerName + " 以'Dev'开头，判定为管理员");
            return true;
        }
        
        ContribTrackerMod.debug(playerName + " 不是管理员");
        return false;
    }
    
    /**
     * 判断当前是否是开发环境
     * @return true如果是开发环境
     */
    private static boolean isDevelopmentEnvironment() {
        // 可以通过系统属性或其他方式判断
        return System.getProperty("dev.mode") != null;
    }
} 