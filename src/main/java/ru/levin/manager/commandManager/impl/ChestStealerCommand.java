package ru.levin.manager.commandManager.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import ru.levin.manager.ClientManager;
import ru.levin.manager.Manager;
import ru.levin.manager.commandManager.Command;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ChestStealerCommand extends Command {

    public ChestStealerCommand() {
        super("cheststealer");
    }

    @Override
    public void execute(LiteralArgumentBuilder<SharedSuggestionProvider> root) {
        root.then(literal("add")
                .then(arg("item", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Item item : BuiltInRegistries.ITEM) {
                                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                                builder.suggest(id.replace("minecraft:", ""));
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String input = StringArgumentType.getString(ctx, "item");
                            String itemName = input.contains(":") ? input : "minecraft:" + input;

                            if (Manager.CHESTSTEALER_MANAGER.addItem(itemName)) {
                                ClientManager.message(ChatFormatting.GREEN + "Добавлен предмет " + itemName + " в список ChestStealer");
                            } else {
                                ClientManager.message(ChatFormatting.RED + "Не удалось добавить предмет: " + itemName);
                            }
                            return SINGLE_SUCCESS;
                        })));

        root.then(literal("remove")
                .then(arg("item", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Item item : Manager.CHESTSTEALER_MANAGER.getWhitelist()) {
                                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                                builder.suggest(id.replace("minecraft:", ""));
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String input = StringArgumentType.getString(ctx, "item");
                            String itemName = input.contains(":") ? input : "minecraft:" + input;

                            if (Manager.CHESTSTEALER_MANAGER.removeItem(itemName)) {
                                ClientManager.message(ChatFormatting.GREEN + "Удалён предмет " + itemName + " из списка ChestStealer");
                            } else {
                                ClientManager.message(ChatFormatting.RED + "Не удалось удалить предмет: " + itemName);
                            }
                            return SINGLE_SUCCESS;
                        })));

        root.then(literal("list")
                .executes(ctx -> {
                    if (Manager.CHESTSTEALER_MANAGER.getWhitelist().isEmpty()) {
                        ClientManager.message(ChatFormatting.RED + "Добавленных предметов нет!");
                    } else {
                        ClientManager.message(ChatFormatting.GREEN + "Список whitelisted предметов:");
                        for (Item item : Manager.CHESTSTEALER_MANAGER.getWhitelist()) {
                            ClientManager.message(ChatFormatting.GRAY + "- " + ChatFormatting.WHITE + BuiltInRegistries.ITEM.getKey(item).toString());
                        }
                    }
                    return SINGLE_SUCCESS;
                }));
    }

}
