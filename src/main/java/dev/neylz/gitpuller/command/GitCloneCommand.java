package dev.neylz.gitpuller.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.neylz.gitpuller.util.TokenManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class GitCloneCommand {
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?|ftp)://[^\\s/$.?#].\\S*$");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("git")
                .then(CommandManager.literal("clone")
                        .requires((source) -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("url", StringArgumentType.greedyString())
                                .executes((context) -> cloneDatapack(context, StringArgumentType.getString(context, "url"))))));
    }

    private static int cloneDatapack(CommandContext<ServerCommandSource> ctx, String remoteUrl) throws CommandSyntaxException {
        if (!URL_PATTERN.matcher(remoteUrl).matches()) {
            throw new CommandSyntaxException(null, () -> "Invalid URL: " + remoteUrl);
        }


        ctx.getSource().sendFeedback(() -> Text.empty()
                .append(Text.literal("Cloning from ").formatted(Formatting.RESET))
                .append(Text.literal(remoteUrl).formatted(Formatting.AQUA)),
            true);

        MinecraftServer server = ctx.getSource().getServer();
        try {
            clone(server, remoteUrl);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFeedback(() -> Text.empty()
                    .append(Text.literal("Failed to clone repository: ").formatted(Formatting.RED))
                    .append(Text.literal(e.getMessage()).formatted(Formatting.RED)), true);
            return 0;
        }

        ctx.getSource().sendFeedback(() -> Text.empty()
                .append(Text.literal("Successfully cloned repository").formatted(Formatting.GREEN)), true);


        return 1;
    }


    private static void clone(MinecraftServer server, String remoteUrl) throws CommandSyntaxException {
        // create a new directory with the name of the datapack
        // if the directory already exists, return false
        // clone the repository into the directory
        // return true if successful, false otherwise

        File datapacksDir = server.getSavePath(WorldSavePath.DATAPACKS).toFile();

        // clone the repository into the datapcks directory
        try {
            Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(datapacksDir)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(TokenManager.getInstance().getToken(), ""))
                    .call();
        } catch (GitAPIException e) {
            e.printStackTrace();
            throw new CommandSyntaxException(null, () -> "Failed to clone repository: " + e.getMessage());
        }






    }


}
