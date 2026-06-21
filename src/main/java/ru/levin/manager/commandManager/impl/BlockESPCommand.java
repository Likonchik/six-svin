package ru.levin.manager.commandManager.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import ru.levin.manager.ClientManager;
import ru.levin.manager.Manager;
import ru.levin.manager.commandManager.Command;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class BlockESPCommand extends Command {

    private static final Map<String, Color> COLOR_NAMES = Map.ofEntries(
            Map.entry("white", Color.WHITE),
            Map.entry("black", Color.BLACK),
            Map.entry("red", Color.RED),
            Map.entry("green", Color.GREEN),
            Map.entry("blue", Color.BLUE),
            Map.entry("yellow", Color.YELLOW),
            Map.entry("cyan", Color.CYAN),
            Map.entry("magenta", Color.MAGENTA),
            Map.entry("gray", Color.GRAY),
            Map.entry("darkgray", Color.DARK_GRAY),
            Map.entry("lightgray", Color.LIGHT_GRAY),
            Map.entry("orange", Color.ORANGE),
            Map.entry("pink", Color.PINK),
            Map.entry("белый", Color.WHITE),
            Map.entry("чёрный", Color.BLACK),
            Map.entry("черный", Color.BLACK),
            Map.entry("красный", Color.RED),
            Map.entry("зелёный", Color.GREEN),
            Map.entry("зеленый", Color.GREEN),
            Map.entry("синий", Color.BLUE),
            Map.entry("жёлтый", Color.YELLOW),
            Map.entry("желтый", Color.YELLOW),
            Map.entry("голубой", Color.CYAN),
            Map.entry("пурпурный", Color.MAGENTA),
            Map.entry("серый", Color.GRAY),
            Map.entry("тёмно-серый", Color.DARK_GRAY),
            Map.entry("темно-серый", Color.DARK_GRAY),
            Map.entry("светло-серый", Color.LIGHT_GRAY),
            Map.entry("оранжевый", Color.ORANGE),
            Map.entry("розовый", Color.PINK)
    );

    public BlockESPCommand() {
        super("blockesp");
    }

    @Override
    public void execute(LiteralArgumentBuilder<SharedSuggestionProvider> root) {

        root.then(literal("add")
                .then(arg("block", StringArgumentType.word())
                        .suggests(this::suggestBlocks)
                        .then(arg("color", StringArgumentType.word())
                                .suggests(this::suggestColors)
                                .executes(ctx -> addBlock(ctx)))));

        root.then(literal("remove")
                .then(arg("block", StringArgumentType.word())
                        .suggests(this::suggestBlocks)
                        .executes(ctx -> removeBlock(ctx))));
    }

    private int addBlock(CommandContext<SharedSuggestionProvider> ctx) {
        String blockName = StringArgumentType.getString(ctx, "block");
        String colorStr = StringArgumentType.getString(ctx, "color").toLowerCase();

        if (!blockName.contains(":")) blockName = "minecraft:" + blockName;

        Color color = COLOR_NAMES.get(colorStr);
        if (color == null) {
            try {
                if (!colorStr.startsWith("#")) colorStr = "#" + colorStr;
                color = Color.decode(colorStr);
            } catch (NumberFormatException ex) {
                ClientManager.message(ChatFormatting.RED + "Неверный цвет. Используй название цвета или HEX (например, ff00ff)");
                return SINGLE_SUCCESS;
            }
        }

        color = new Color(color.getRed(), color.getGreen(), color.getBlue(), 150);
        Manager.FUNCTION_MANAGER.blockESP.addCustomBlock(blockName, color);
        ClientManager.message(ChatFormatting.GREEN + "Добавлен блок " + blockName + " с цветом " + colorStr);
        return SINGLE_SUCCESS;
    }

    private int removeBlock(CommandContext<SharedSuggestionProvider> ctx) {
        String blockName = StringArgumentType.getString(ctx, "block");
        if (!blockName.contains(":")) blockName = "minecraft:" + blockName;

        Manager.FUNCTION_MANAGER.blockESP.removeCustomBlock(blockName);
        ClientManager.message(ChatFormatting.GREEN + "Удалён блок " + blockName + " из подсветки");
        return SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestColors(CommandContext<SharedSuggestionProvider> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(COLOR_NAMES.keySet(), builder);
    }

    private CompletableFuture<Suggestions> suggestBlocks(CommandContext<SharedSuggestionProvider> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                BuiltInRegistries.BLOCK.stream().map(b -> BuiltInRegistries.BLOCK.getKey(b).getPath()).toList(),
                builder
        );
    }
}
