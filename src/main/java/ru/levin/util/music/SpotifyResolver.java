package ru.levin.util.music;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ЧЕСТНО: реального Spotify-аудио нет (DRM). Вставленная Spotify-ссылка резолвится в поисковый запрос
// (название трека) через публичный open.spotify.com/oembed (без авторизации), затем зеркалится на
// YouTube Music / SoundCloud. oEmbed для трека отдаёт "title" (название); исполнитель отдельно НЕ всегда
// приходит, но названия обычно достаточно для ytmsearch. Для ISRC-точного зеркала нужен Web API
// (client id/secret) — вынесено в дальнейшую фазу.
public final class SpotifyResolver {

    private static final Pattern TITLE = Pattern.compile("\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private SpotifyResolver() {}

    public static boolean isSpotify(String s) {
        if (s == null) return false;
        String l = s.toLowerCase();
        return l.contains("open.spotify.com") || l.startsWith("spotify:");
    }

    // возвращает поисковый запрос (название трека) или null
    public static String resolveQuery(String spotifyUrl) {
        try {
            // нормализуем spotify:track:ID -> https URL для oEmbed
            String url = spotifyUrl.trim();
            if (url.startsWith("spotify:")) {
                String[] parts = url.split(":");
                if (parts.length >= 3) url = "https://open.spotify.com/" + parts[1] + "/" + parts[2];
            }
            String api = "https://open.spotify.com/oembed?url=" + java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8);
            String json = MusicProxy.httpGet(api);
            if (json == null) return null;
            Matcher m = TITLE.matcher(json);
            if (m.find()) {
                String title = unescapeJson(m.group(1));
                if (title != null && !title.isBlank()) return title;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\");
    }
}
