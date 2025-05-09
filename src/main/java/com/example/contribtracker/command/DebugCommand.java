package com.example.contribtracker.command;

import com.example.contribtracker.ContribPermissionManager;
import com.example.contribtracker.ContribTrackerMod;
import com.mojang.brigadier.arguments.BooleanArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 调试模式命令类
 * 仅管理员可以使用
 */
public class DebugCommand implements BaseCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);

    @Override
    public LiteralArgumentBuilder<ServerCommandSource> register() {
        return CommandManager.literal("contribtracker")
            .then(CommandManager.literal("debug")
                .executes(this::toggleDebugMode) // 无参数时切换调试模式
                .then(CommandManager.argument("enable", BooleanArgumentType.bool())
                    .executes(this::setDebugMode) // 带参数时设置调试模式
                )
            );
    }

    /**
     * 切换调试模式
     */
    private int toggleDebugMode(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        
        if (player == null) {
            source.sendMessage(Text.of("§c该命令只能由玩家执行"));
            return 0;
        }
        
        // 检查权限
        if (!ContribPermissionManager.isAdmin(player)) {
            source.sendMessage(Text.of("§c你没有权限使用此命令"));
            return 0;
        }
        
        // 切换调试模式
        boolean newMode = !ContribTrackerMod.isDebugMode();
        ContribTrackerMod.setDebugMode(newMode);
        
        source.sendMessage(Text.of("§a调试模式已" + (newMode ? "开启" : "关闭")));
        LOGGER.info("玩家 " + player.getName().getString() + " " + (newMode ? "开启" : "关闭") + "了调试模式");
        
        return 1;
    }

    /**
     * 设置调试模式
     */
    private int setDebugMode(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        
        if (player == null) {
            source.sendMessage(Text.of("§c该命令只能由玩家执行"));
            return 0;
        }
        
        // 检查权限
        if (!ContribPermissionManager.isAdmin(player)) {
            source.sendMessage(Text.of("§c你没有权限使用此命令"));
            return 0;
        }
        
        // 设置调试模式
        boolean enable = BooleanArgumentType.getBool(context, "enable");
        ContribTrackerMod.setDebugMode(enable);
        
        source.sendMessage(Text.of("§a调试模式已" + (enable ? "开启" : "关闭")));
        LOGGER.info("玩家 " + player.getName().getString() + " " + (enable ? "开启" : "关闭") + "了调试模式");
        
        return 1;
    }
} 