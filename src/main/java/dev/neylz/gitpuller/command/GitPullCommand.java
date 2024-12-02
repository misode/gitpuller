package dev.neylz.gitpuller.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.neylz.gitpuller.GitPuller;
import dev.neylz.gitpuller.util.GitUtil;
import dev.neylz.gitpuller.util.TokenManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;

public class GitPullCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("git")
                .then(CommandManager.literal("pull")
                        .requires((source) -> source.hasPermissionLevel(2))
                        .executes(GitPullCommand::pullPacks)));
    }

    private static int pullPacks(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        // perform git pull -f --all on the specified pack

        File datapacksDir = ctx.getSource().getServer().getSavePath(WorldSavePath.DATAPACKS).toFile();
        if (!datapacksDir.exists()) {
            throw new CommandSyntaxException(null, () -> "Datapacks folder does not exist");
        } else if (!GitUtil.isGitRepo(datapacksDir)) {
            throw new CommandSyntaxException(null, () -> "Datapacks folder is not a git repository");
        }

        // git pull -f --all
        String sha1 = GitUtil.getCurrentHeadSha1(datapacksDir, 7);
        if (!gitPull(ctx.getSource(), datapacksDir)) {
            throw new CommandSyntaxException(null, () -> "Failed to pull changes");
        }

        String newSha1 = GitUtil.getCurrentHeadSha1(datapacksDir, 7);
        if (!sha1.equals(newSha1)) {
            ctx.getSource().sendFeedback(
                () -> Text.empty()
                        .append(Text.literal("Pulled changes").formatted(Formatting.RESET))
                        .append(Text.literal(" (").formatted(Formatting.RESET))
                        .append(Text.literal(sha1).formatted(Formatting.AQUA))
                        .append(Text.literal(" -> ").formatted(Formatting.RESET))
                        .append(Text.literal(newSha1).formatted(Formatting.LIGHT_PURPLE))
                        .append(Text.literal(")").formatted(Formatting.RESET)),
                true
            );
        }



        return 1;
    }



    private static boolean gitPull(ServerCommandSource sender, File repoDir) throws CommandSyntaxException {
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
            sender.sendFeedback(
                    () -> Text.empty()
                            .append("Fetched changes from remote repository").formatted(Formatting.GREEN),
                    true
            );


            return true;
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
            throw new CommandSyntaxException(null, () -> "Failed to pull changes from remote repository: " + e.getMessage());
        }
    }
}
