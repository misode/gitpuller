package dev.neylz.gitpuller.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.neylz.gitpuller.util.GitUtil;
import dev.neylz.gitpuller.util.ModConfig;
import dev.neylz.gitpuller.util.TokenManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.nio.file.Path;

public class GitCloneCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection environment) {
        if (ModConfig.isMonoRepo()) {
            return;
        }

        dispatcher.register(
            Commands.literal("git").then((
                Commands.literal("clone").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then((
                    Commands.argument("name", StringArgumentType.string())).then((
                        Commands.argument("url", StringArgumentType.greedyString()).executes(
                            (context) -> cloneDatapack(context, StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "url")))
                    ))
                )
            )

        );
    }

    private static int cloneDatapack(CommandContext<CommandSourceStack> ctx, String name, String remoteUrl) throws CommandSyntaxException {
        if (!GitUtil.URL_PATTERN.matcher(remoteUrl).matches()) {
            throw new CommandSyntaxException(null, () -> "Invalid URL: " + remoteUrl);
        }


        ctx.getSource().sendSuccess(() -> Component.empty()
                .append(Component.literal("Cloning from ").withStyle(ChatFormatting.RESET))
                .append(Component.literal(remoteUrl).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" into the datapack ").withStyle(ChatFormatting.RESET))
                .append(Component.literal("[" + name + "]").withStyle(ChatFormatting.YELLOW)),
            true);

        MinecraftServer server = ctx.getSource().getServer();
        try {
            clone(server, remoteUrl, name);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendSuccess(() -> Component.empty()
                    .append(Component.literal("Failed to clone repository: ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(e.getMessage()).withStyle(ChatFormatting.RED)), true);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.empty()
                .append(Component.literal("Successfully cloned repository").withStyle(ChatFormatting.GREEN)), true);


        return 1;
    }


    private static void clone(MinecraftServer server, String remoteUrl, String name) throws CommandSyntaxException {
        // create a new directory with the name of the datapack
        // if the directory already exists, return false
        // clone the repository into the directory
        // return true if successful, false otherwise

        Path worldDir = server.getWorldPath(LevelResource.DATAPACK_DIR);
        File datapackDir = new File(worldDir.toFile(), name);

        if (datapackDir.exists()) {
            throw new CommandSyntaxException(null, () -> "Datapack \"" + name + "\" already exists");
        }


        // clone the repository into the directory
        try {
            Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(datapackDir)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(TokenManager.getInstance().getToken(), ""))
                    .call();
        } catch (GitAPIException e) {
            e.printStackTrace();
            throw new CommandSyntaxException(null, () -> "Failed to clone repository: " + e.getMessage());
        }






    }


}
