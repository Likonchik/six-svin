package ru.levin.manager.commandManager.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import ru.levin.manager.ClientManager;
import ru.levin.manager.Manager;
import ru.levin.manager.commandManager.Command;
import ru.levin.modules.render.MediaPlayer;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

// .music play <url|запрос> | pause | next | prev | stop | clear | vol <0-100>
public class MusicCommand extends Command {

    public MusicCommand() {
        super("music");
    }

    private MediaPlayer mp() {
        return Manager.FUNCTION_MANAGER.mediaPlayer;
    }

    @Override
    public void execute(LiteralArgumentBuilder<SharedSuggestionProvider> b) {
        b.then(literal("play").then(arg("query", StringArgumentType.greedyString()).executes(ctx -> {
            String q = StringArgumentType.getString(ctx, "query");
            MediaPlayer m = mp();
            if (m == null) return SINGLE_SUCCESS;
            if (!m.state) m.setState(true);
            m.play(q);
            ClientManager.message(ChatFormatting.GREEN + "Загружаю: " + ChatFormatting.GRAY + q);
            return SINGLE_SUCCESS;
        })));

        b.then(literal("login").executes(ctx -> {
            MediaPlayer m = mp();
            if (m == null) return SINGLE_SUCCESS;
            if (!m.state) m.setState(true);
            m.getEngine().spotifyLogin();
            ClientManager.message(ChatFormatting.GREEN + "Spotify: запускаю вход (откроется браузер / ссылка в буфере)");
            return SINGLE_SUCCESS;
        }));

        b.then(literal("pause").executes(ctx -> {
            if (mp() != null) mp().getEngine().playPause();
            return SINGLE_SUCCESS;
        }));

        b.then(literal("next").executes(ctx -> {
            if (mp() != null) mp().getEngine().next();
            return SINGLE_SUCCESS;
        }));

        b.then(literal("prev").executes(ctx -> {
            if (mp() != null) mp().getEngine().prev();
            return SINGLE_SUCCESS;
        }));

        b.then(literal("stop").executes(ctx -> {
            if (mp() != null) mp().getEngine().stop();
            ClientManager.message(ChatFormatting.GRAY + "Музыка остановлена");
            return SINGLE_SUCCESS;
        }));

        b.then(literal("queue").then(arg("query", StringArgumentType.greedyString()).executes(ctx -> {
            String q = StringArgumentType.getString(ctx, "query");
            MediaPlayer m = mp();
            if (m != null) m.queue(q);
            ClientManager.message(ChatFormatting.GREEN + "В очередь: " + ChatFormatting.GRAY + q);
            return SINGLE_SUCCESS;
        })));

        b.then(literal("playnext").then(arg("query", StringArgumentType.greedyString()).executes(ctx -> {
            String q = StringArgumentType.getString(ctx, "query");
            MediaPlayer m = mp();
            if (m != null) m.playNext(q);
            ClientManager.message(ChatFormatting.GREEN + "Следующим: " + ChatFormatting.GRAY + q);
            return SINGLE_SUCCESS;
        })));

        b.then(literal("restore").executes(ctx -> {
            MediaPlayer m = mp();
            if (m != null) m.restoreQueue();
            ClientManager.message(ChatFormatting.GRAY + "Восстанавливаю прошлую очередь (без автоплея)...");
            return SINGLE_SUCCESS;
        }));

        b.then(literal("gui").executes(ctx -> {
            MediaPlayer m = mp();
            if (m != null) { if (!m.state) m.setState(true); m.openMenu(); }
            return SINGLE_SUCCESS;
        }));

        b.then(literal("clear").executes(ctx -> {
            if (mp() != null) mp().getEngine().stop();
            return SINGLE_SUCCESS;
        }));

        b.then(literal("vol").then(arg("v", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
            int v = IntegerArgumentType.getInteger(ctx, "v");
            MediaPlayer m = mp();
            if (m != null) m.setVolume(v);
            ClientManager.message(ChatFormatting.GRAY + "Громкость: " + v + "%");
            return SINGLE_SUCCESS;
        })));
    }
}
