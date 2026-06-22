package ru.levin.util.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

// Персистентность очереди+истории. НЕ сериализуем lavaplayer AudioTrack (внутренние/несериализуемые) —
// храним URI + метаданные, на загрузке пере-резолвим через manager.loadItem. uri у YouTube и SoundCloud —
// настоящая шаренная ссылка, так что восстановление работает для обоих.
public final class QueueStore {

    public static final class Entry {
        public String uri;
        public String identifier;
        public String title;
        public String author;
        public String artworkUrl;
        public long lengthMs;
    }

    public static final class Model {
        public List<Entry> tracks = new ArrayList<>();
        public int index = 0;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private QueueStore() {}

    public static void save(File file, Model model) {
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(file)) {
                GSON.toJson(model, w);
            }
        } catch (Throwable ignored) {}
    }

    public static Model load(File file) {
        try {
            if (file == null || !file.exists()) return null;
            String json = Files.readString(file.toPath());
            if (json == null || json.isBlank()) return null;
            return GSON.fromJson(json, Model.class);
        } catch (Throwable t) {
            return null;
        }
    }
}
