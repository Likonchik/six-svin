package ru.levin.mixin.chat;


import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import ru.levin.manager.ClientManager;
import ru.levin.manager.Manager;
import ru.levin.manager.commandManager.CommandManager;


import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class MixinChatInputSuggestor {
    @Final @Shadow EditBox input;
    @Shadow boolean keepSuggestions;
    @Shadow private ParseResults<SharedSuggestionProvider> currentParse;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private CommandSuggestions.SuggestionsList suggestions;

    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Inject(method = "updateCommandInfo", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;canRead()Z", remap = false), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    public void refreshHook(CallbackInfo ci, String string, StringReader reader) {
        CommandManager commandManager = Manager.COMMAND_MANAGER;
        if (!ClientManager.legitMode) {
            if (reader.canRead(commandManager.getPrefix().length()) && reader.getString().startsWith(commandManager.getPrefix(), reader.getCursor())) {
                reader.setCursor(reader.getCursor() + 1);
                if (currentParse == null)
                    currentParse = commandManager.getDispatcher().parse(reader, commandManager.getSource());
                final int cursor = input.getCursorPosition();
                if (cursor >= 1 && (suggestions == null || !keepSuggestions)) {
                    pendingSuggestions = commandManager.getDispatcher().getCompletionSuggestions(currentParse, cursor);
                    pendingSuggestions.thenRun(() -> {
                        if (pendingSuggestions.isDone()) showSuggestions(false);
                    });
                }

                ci.cancel();
            }
        }
    }
}