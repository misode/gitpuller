package dev.neylz.gitpuller.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.neylz.gitpuller.util.TokenManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class GitTokenCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("git")
                .then(Commands.literal("token")
                    .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                    .then(Commands.argument("token", StringArgumentType.greedyString())
                        .executes((context) -> setToken(context, StringArgumentType.getString(context, "token"))
                    )
                )
            )
        );
    }

    private static int setToken(CommandContext<CommandSourceStack> ctx, String tk) throws CommandSyntaxException {
        TokenManager.getInstance().setToken(tk);

        ctx.getSource().sendSuccess(() -> Component.literal("Git organization token has been set.").withStyle(ChatFormatting.GREEN), true);

        return 1;
    }
}
