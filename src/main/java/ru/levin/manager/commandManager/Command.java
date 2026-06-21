package ru.levin.manager.commandManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.NotNull;
import ru.levin.manager.IMinecraft;

@SuppressWarnings("All")
public abstract class Command implements IMinecraft {
    private final String command;
    protected Command(String command) {
       this.command = command;
    }

    public void register(CommandDispatcher<SharedSuggestionProvider> dispatcher) {
        LiteralArgumentBuilder<SharedSuggestionProvider> builder = LiteralArgumentBuilder.literal(command);
        execute(builder);
        dispatcher.register(builder);

    }
    public abstract void execute(LiteralArgumentBuilder<SharedSuggestionProvider> builder);
    protected static <T> @NotNull RequiredArgumentBuilder<SharedSuggestionProvider, T> arg(final String name, final ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
    protected static @NotNull LiteralArgumentBuilder<SharedSuggestionProvider> literal(final String name) {
        return LiteralArgumentBuilder.literal(name);
    }
}

