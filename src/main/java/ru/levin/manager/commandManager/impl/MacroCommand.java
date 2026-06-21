package ru.levin.manager.commandManager.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.ChatFormatting;
import ru.levin.manager.ClientManager;
import ru.levin.manager.Manager;
import ru.levin.manager.commandManager.Command;
import ru.levin.manager.macroManager.Macro;
import ru.levin.util.KeyMappings;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class MacroCommand extends Command {

    public MacroCommand() {
        super("macro");
    }

    @Override
    public void execute(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {

        SuggestionProvider<SharedSuggestionProvider> keySuggestions = (context, builder1) -> {
            for (String key : KeyMappings.getAllKeys()) {
                builder1.suggest(key);
            }
            return builder1.buildFuture();
        };
        SuggestionProvider<SharedSuggestionProvider> macroSuggestions = (context, builder1) -> {
            for (Macro macro : Manager.MACROS_MANAGER.getMacros()) {
                String keyName = KeyMappings.keyMappings(macro.getKey());
                if (keyName != null)
                    builder1.suggest(keyName);
            }
            return builder1.buildFuture();
        };

        RequiredArgumentBuilder<SharedSuggestionProvider, String> addKeyArg = arg("key", StringArgumentType.word()).suggests(keySuggestions);
        RequiredArgumentBuilder<SharedSuggestionProvider, String> addMessageArg = arg("message", StringArgumentType.greedyString())
                .executes(ctx -> {
                    String keyName = StringArgumentType.getString(ctx, "key").toUpperCase();
                    Integer key = KeyMappings.keyCode(keyName);

                    if (key == null) {
                        ClientManager.message(ChatFormatting.RED + "Не найдена кнопка " + keyName);
                        return SINGLE_SUCCESS;
                    }

                    String message = StringArgumentType.getString(ctx, "message");
                    Manager.MACROS_MANAGER.addMacros(new Macro(message, key));

                    ClientManager.message(ChatFormatting.GREEN + "Добавлен макрос для кнопки "
                            + ChatFormatting.RED + keyName + ChatFormatting.WHITE + " с командой " + ChatFormatting.RED + message);
                    return SINGLE_SUCCESS;
                });
        addKeyArg.then(addMessageArg);
        builder.then(literal("add").then(addKeyArg));
        RequiredArgumentBuilder<SharedSuggestionProvider, String> removeKeyArg = arg("key", StringArgumentType.word())
                .suggests(macroSuggestions)
                .executes(ctx -> {
                    String keyName = StringArgumentType.getString(ctx, "key").toUpperCase();
                    Integer key = KeyMappings.keyCode(keyName);

                    if (key == null) {
                        ClientManager.message(ChatFormatting.RED + "Не найдена кнопка " + keyName);
                        return SINGLE_SUCCESS;
                    }

                    Macro macro = Manager.MACROS_MANAGER.getMacroByKey(key);
                    if (macro == null) {
                        ClientManager.message(ChatFormatting.RED + "На кнопке " + keyName + " нет макроса.");
                        return SINGLE_SUCCESS;
                    }

                    Manager.MACROS_MANAGER.deleteMacro(key);
                    ClientManager.message(ChatFormatting.GREEN + "Макрос удален с кнопки " + ChatFormatting.RED + keyName);
                    return SINGLE_SUCCESS;
                });
        builder.then(literal("remove").then(removeKeyArg));

        builder.then(literal("list").executes(ctx -> {
            if (Manager.MACROS_MANAGER.getMacros().isEmpty()) {
                ClientManager.message("Список макросов пуст");
            } else {
                ClientManager.message(ChatFormatting.GREEN + "Список макросов:");
                Manager.MACROS_MANAGER.getMacros().forEach(macro -> {
                    String keyName = KeyMappings.keyMappings(macro.getKey());
                    ClientManager.message(ChatFormatting.WHITE + "Команда: " + ChatFormatting.RED + macro.getMessage()
                            + ChatFormatting.WHITE + ", Кнопка: " + ChatFormatting.RED + keyName);
                });
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(ctx -> {
            if (Manager.MACROS_MANAGER.getMacros().isEmpty()) {
                ClientManager.message(ChatFormatting.RED + "Список макросов пуст");
            } else {
                Manager.MACROS_MANAGER.getMacros().clear();
                Manager.MACROS_MANAGER.updateFile();
                ClientManager.message(ChatFormatting.GREEN + "Список макросов успешно очищен");
            }
            return SINGLE_SUCCESS;
        }));
    }

}
