package com.example.contribtracker.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.contribtracker.ContribTrackerMod;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令管理器类，负责注册和管理所有命令
 */
public class CommandManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);
    private static final List<BaseCommand> commands = new ArrayList<>();

    static {
        commands.add(new AddCommand());
        commands.add(new DeleteCommand());
        commands.add(new ListCommand());
        commands.add(new NearCommand());
        commands.add(new RemoveCommand());
        commands.add(new AcceptCommand());
        commands.add(new RejectCommand());
    }

    public static List<BaseCommand> getCommands() {
        return commands;
    }

    /**
     * 注册命令
     */
    public static void registerCommands() {
        // 注册所有命令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            for (BaseCommand command : commands) {
                dispatcher.register(command.register());
                LOGGER.info("注册命令: " + command.getClass().getSimpleName());
            }
        });
    }
} 