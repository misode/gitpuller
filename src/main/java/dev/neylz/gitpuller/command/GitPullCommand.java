package dev.neylz.gitpuller.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.neylz.gitpuller.GitPuller;
import dev.neylz.gitpuller.util.GitUtil;
import dev.neylz.gitpuller.util.ModConfig;
import dev.neylz.gitpuller.util.TokenManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;

public class GitPullCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection environment) {
        LiteralArgumentBuilder<CommandSourceStack> pullCommand = Commands.literal("pull").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        if (!ModConfig.isMonoRepo()) {
            pullCommand = pullCommand.then(Commands.argument("pack name", StringArgumentType.word()).suggests(
                    (ctx, builder) -> SharedSuggestionProvider.suggest(GitUtil.getTrackedDatapacks(ctx.getSource().getServer().getWorldPath(LevelResource.DATAPACK_DIR).toFile()), builder))
                .executes((ctx) -> pullPack(ctx, StringArgumentType.getString(ctx, "pack name")))
            );
        } else {
            pullCommand = pullCommand.executes(GitPullCommand::pullMonoPack);
        }

        dispatcher.register(Commands.literal("git").then(pullCommand));
    }


    private static int pullMonoPack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ctx.getSource().sendSuccess(() -> Component.empty()
                .append(Component.literal("Pulling changes from remote repository").withStyle(ChatFormatting.GREEN)), true);

        File file = ctx.getSource().getServer().getWorldPath(LevelResource.DATAPACK_DIR).toFile();

        // git pull -f --all
        String sha1 = GitUtil.getCurrentHeadSha1(file, 7);
        if (!gitPull(ctx.getSource(), file)) {
            throw new CommandSyntaxException(null, () -> "Failed to pull changes from distant repository");
        }

        String newSha1 = GitUtil.getCurrentHeadSha1(file, 7);
        if (!sha1.equals(newSha1)) {
            ctx.getSource().sendSuccess(
                () -> Component.empty()
                        .append(Component.literal("Pulled changes").withStyle(ChatFormatting.RESET))
                        .append(Component.literal(" (").withStyle(ChatFormatting.RESET))
                        .append(Component.literal(sha1).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" -> ").withStyle(ChatFormatting.RESET))
                        .append(Component.literal(newSha1).withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(")").withStyle(ChatFormatting.RESET)),
                true
            );
        }
        return 1;
    }

    private static int pullPack(CommandContext<CommandSourceStack> ctx, String packName) throws CommandSyntaxException {
        // perform git pull -f --all on the specified pack

        File file = new File(ctx.getSource().getServer().getWorldPath(LevelResource.DATAPACK_DIR).toFile(), packName);

        if (!file.exists()) {
            throw new CommandSyntaxException(null, () -> "Datapack " + packName + " does not exist");
        } else if (!GitUtil.isGitRepo(file)) {
            throw new CommandSyntaxException(null, () -> "Datapack " + packName + " is not a git repository");
        }

        // git pull -f --all
        String sha1 = GitUtil.getCurrentHeadSha1(file, 7);
        if (!gitPull(ctx.getSource(), file)) {
            throw new CommandSyntaxException(null, () -> "Failed to pull changes from " + packName);
        }

        String newSha1 = GitUtil.getCurrentHeadSha1(file, 7);
        if (!sha1.equals(newSha1)) {
            ctx.getSource().sendSuccess(
                () -> Component.empty()
                        .append(Component.literal("Pulled changes from ").withStyle(ChatFormatting.RESET))
                        .append(Component.literal("[" + packName + "]").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" (").withStyle(ChatFormatting.RESET))
                        .append(Component.literal(sha1).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" -> ").withStyle(ChatFormatting.RESET))
                        .append(Component.literal(newSha1).withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(")").withStyle(ChatFormatting.RESET)),
                true
            );
        }



        return 1;
    }



    private static boolean gitPull(CommandSourceStack sender, File repoDir) throws CommandSyntaxException {
        try {
            Git git = Git.open(repoDir);

            git.fetch()
                    .setRemoveDeletedRefs(true)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(TokenManager.getInstance().getToken(), ""))
                    .call();

            git.pull()
                    .setRebase(true)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(TokenManager.getInstance().getToken(), ""))
                    .call();

            GitPuller.LOGGER.info("Fetched changes from remote repository");
            sender.sendSuccess(
                    () -> Component.empty()
                            .append("Fetched changes from remote repository").withStyle(ChatFormatting.GREEN),
                    true
            );


            return true;
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
            throw new CommandSyntaxException(null, () -> "Failed to pull changes from remote repository: " + e.getMessage());
        }
    }
}
