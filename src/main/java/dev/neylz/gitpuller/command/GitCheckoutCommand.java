package dev.neylz.gitpuller.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public class GitCheckoutCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection environment) {
        LiteralArgumentBuilder<CommandSourceStack> checkoutCommand = Commands.literal("checkout").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        RequiredArgumentBuilder<CommandSourceStack, String> branchArg = Commands.argument("branch", StringArgumentType.greedyString());

        if (!ModConfig.isMonoRepo()) {
            checkoutCommand = checkoutCommand
                .then(Commands.argument("pack name", StringArgumentType.word()).suggests(
                    (ctx, builder) -> SharedSuggestionProvider.suggest(GitUtil.getTrackedDatapacks(ctx.getSource().getServer().getWorldPath(LevelResource.DATAPACK_DIR).toFile()), builder))
                .then(branchArg.suggests(
                    (ctx, builder) -> SharedSuggestionProvider.suggest(GitUtil.getBranches(new File(ctx.getSource().getServer().getWorldPath(LevelResource.DATAPACK_DIR).toFile(), StringArgumentType.getString(ctx, "pack name"))), builder))
                .executes(
                    (ctx) -> checkout(ctx, StringArgumentType.getString(ctx, "pack name"), StringArgumentType.getString(ctx, "branch"))
            )));

        } else {
            checkoutCommand = checkoutCommand
                .then(branchArg
                .executes(
                    (ctx) -> checkoutMono(ctx, StringArgumentType.getString(ctx, "branch"))
                ));
        }

        dispatcher.register(Commands.literal("git")
            .then(checkoutCommand)
        );
    }

    private static int checkoutMono(CommandContext<CommandSourceStack> ctx, String branch) throws CommandSyntaxException {
        ctx.getSource().sendSuccess(() -> Component.empty()
                .append(Component.literal("Checking out to ").withStyle(ChatFormatting.RESET))
                .append(Component.literal(branch).withStyle(ChatFormatting.DARK_GREEN))
                .append(Component.literal(" in the mono repo").withStyle(ChatFormatting.RESET)),
            true);

        File file = ctx.getSource().getServer().getWorldPath(LevelResource.DATAPACK_DIR).toFile();

        gitCheckout(ctx.getSource(), file, branch);

        return 1;
    }

    private static int checkout(CommandContext<CommandSourceStack> ctx, String pack, String branch) throws CommandSyntaxException {

        File packDir = new File(ctx.getSource().getServer().getWorldPath(LevelResource.DATAPACK_DIR).toFile(), pack);
        if (!packDir.exists()) {
            throw new CommandSyntaxException(null, () -> "Datapack " + pack + " does not exist");
        } else if (!GitUtil.isGitRepo(packDir)) {
            throw new CommandSyntaxException(null, () -> "Datapack " + pack + " is not a git repository");
        }

        gitCheckout(ctx.getSource(), packDir, branch);

//        if (!gitCheckout(packDir, branch)) {
//            throw new CommandSyntaxException(null, () -> "Failed to checkout branch " + branch + " in " + pack);
//        } else {
//            ctx.getSource()
//        }

        return 1;

    }

    private static void gitCheckout(CommandSourceStack source, File file, String ref) throws CommandSyntaxException {
        try (Git git = Git.open(file)) {
            // Fetch all branches from remote
            git.fetch()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(TokenManager.getInstance().getToken(), ""))
                    .call();

            // Determine if ref is a SHA-1 hash or a branch name
            if (isSHA1(ref)) {
                // Checkout to the specific commit
                Repository repository = git.getRepository();
                try (RevWalk revWalk = new RevWalk(repository)) {
                    RevCommit commit = revWalk.parseCommit(repository.resolve(ref));
                    git.checkout()
                            .setName(ref)
                            .setCreateBranch(true)
                            .setStartPoint(commit)
                            .call();

                    source.sendSuccess(
                        () -> Component.empty()
                                .append(Component.literal("Checked out commit ").withStyle(ChatFormatting.RESET))
                                .append(Component.literal(ref).withStyle(ChatFormatting.LIGHT_PURPLE)),
                            true);
                } catch (IOException e) {
//                    e.printStackTrace();
                    throw new CommandSyntaxException(null, () -> "Failed to checkout commit " + ref);

                }
            } else {
                // Check if the branch exists locally
                List<Ref> branchList = git.branchList().call();
                boolean branchExists = branchList.stream().anyMatch(branch -> branch.getName().equals("refs/heads/" + ref));

                if (!branchExists) {
                    // Create a new branch tracking the remote branch
                    git.checkout()
                            .setCreateBranch(true)
                            .setName(ref)
                            .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                            .setStartPoint("origin/" + ref)
                            .call();
                } else {
                    // Checkout to the existing branch
                    git.checkout()
                            .setName(ref)
                            .call();
                }

                source.sendSuccess(
                        () -> Component.empty()
                                .append(Component.literal("Checked out branch ").withStyle(ChatFormatting.RESET))
                                .append(Component.literal(ref).withStyle(ChatFormatting.DARK_GREEN))
                                .append(Component.literal(" in ").withStyle(ChatFormatting.RESET))
                                .append(Component.literal("[" + file.getName() + "]").withStyle(ChatFormatting.YELLOW)),
                        true);
            }

        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }
    }


    private static boolean isSHA1(String ref) {
        return Pattern.matches("^[a-fA-F0-9]{40}$", ref);
    }

}
