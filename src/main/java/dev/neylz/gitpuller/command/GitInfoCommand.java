package dev.neylz.gitpuller.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.neylz.gitpuller.util.GitUtil;
import dev.neylz.gitpuller.util.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.eclipse.jgit.api.Git;

import java.io.File;
import java.io.IOException;

public class GitInfoCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection environment) {
        LiteralArgumentBuilder<CommandSourceStack> infoCommand = Commands.literal("info");

        if (!ModConfig.isMonoRepo()) {
            infoCommand = infoCommand.executes(GitInfoCommand::datapackInfo);
        } else {
            infoCommand = infoCommand.executes(GitInfoCommand::datapackMonoInfo);
        }

        dispatcher.register(Commands.literal("git")
            .then(infoCommand)
        );
    }

    private static int datapackMonoInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        File file = ctx.getSource().getServer().getWorldPath(LevelResource.DATAPACK_DIR).toFile();
        String remote = "";

        try (Git git = Git.open(file)) {
            remote = git.getRepository().getConfig().getString("remote", "origin", "url");
        } catch (IOException e) {
            throw new CommandSyntaxException(null, () -> "Failed to open git repository: " + e.getMessage());
        }

        String finalRemote = remote;
        ctx.getSource().sendSuccess(() -> {
            return Component.empty()
                    .append(Component.literal("Currently tracking as monorepo ")
                    .append(Component.literal(finalRemote).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\n  (").withStyle(ChatFormatting.RESET))
                    .append(Component.literal(GitUtil.getCurrentBranch(file)).withStyle(ChatFormatting.DARK_GREEN))
                    .append(Component.literal("-").withStyle(ChatFormatting.RESET))
                    .append(Component.literal(GitUtil.getCurrentHeadSha1(file, 7)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(")").withStyle(ChatFormatting.RESET))
            );
        }, false);

        return 1;
    }

    private static int datapackInfo(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();

        File file = server.getWorldPath(LevelResource.DATAPACK_DIR).toFile();

        // list all files
        File[] files = file.listFiles();
        if (files != null) {
            ctx.getSource().sendSuccess(() -> {
                MutableComponent text = Component.empty()
                        .append(Component.literal("Available datapacks:").withStyle(ChatFormatting.UNDERLINE));
                for (File f : files) {
                    if (!f.isDirectory()) continue;

                    text.append(Component.literal("\n   ").withStyle(ChatFormatting.RESET))
                        .append(Component.literal("[" + f.getName() + "]").withStyle(ChatFormatting.YELLOW));

                    if (GitUtil.isGitRepo(f)) {
                        text.append(Component.literal("  (").withStyle(ChatFormatting.RESET))
                            .append(Component.literal(GitUtil.getCurrentBranch(f)).withStyle(ChatFormatting.DARK_GREEN))
                            .append(Component.literal("-").withStyle(ChatFormatting.RESET))
                            .append(Component.literal(GitUtil.getCurrentHeadSha1(f, 7)).withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(")").withStyle(ChatFormatting.RESET));
                    } else {
                        text.append(Component.literal("  (untracked)").withStyle(ChatFormatting.RED).withStyle(ChatFormatting.ITALIC));
                    }
                }

                return text;
            }, false);
        }


        return 1;
    }

}
