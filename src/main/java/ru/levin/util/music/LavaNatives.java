package ru.levin.util.music;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// Чинит загрузку нативных декодеров lavaplayer (mp3/opus "connector") в NeoForge.
//
// Проблема: модульный classloader NeoForge прячет ресурсы по пути `natives/...` из jar lavaplayer-natives
// (это не валидный package-путь) -> внутренний getResourceAsStream возвращает null ->
// java.lang.UnsatisfiedLinkError: "Required library was not found" -> ни один трек не декодируется (0/0).
//
// Решение: нативы лежат в РЕСУРСАХ нашего мода под валидным package-путём (виден нашему classloader'у),
// распаковываем нужный под текущую ОС во временную папку и говорим lavaplayer явный путь через системные
// свойства lava.connector.path/.dir (это минует сломанную распаковку из ресурсов). connector зависит от
// libmpg123-0.dll на Windows -> кладём обе в одну папку.
public final class LavaNatives {
    private static boolean done = false;

    private LavaNatives() {}

    public static synchronized void setup() {
        if (done) return;
        done = true;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            String arch = System.getProperty("os.arch", "").toLowerCase();
            String plat;
            String[] files;
            if (os.contains("win")) {
                plat = "win_x86_64";
                files = new String[]{"connector.dll", "libmpg123-0.dll"};
            } else if (os.contains("mac") || os.contains("darwin")) {
                plat = "darwin";
                files = new String[]{"libconnector.dylib"};
            } else if (os.contains("nux") || os.contains("nix")) {
                if (!(arch.contains("64") && (arch.contains("amd") || arch.contains("x86")))) return; // в комплекте только linux x86-64
                plat = "linux_x86_64";
                files = new String[]{"libconnector.so"};
            } else {
                return; // неизвестная ОС — пусть lavaplayer пробует сам
            }

            Path dir = Files.createTempDirectory("onetap-lava-natives");
            dir.toFile().deleteOnExit();
            Path connector = null;
            for (String f : files) {
                Path out = dir.resolve(f);
                try (InputStream in = LavaNatives.class.getResourceAsStream("/ru/levin/lava/" + plat + "/" + f)) {
                    if (in == null) {
                        System.err.println("[MediaPlayer] bundled native missing: /ru/levin/lava/" + plat + "/" + f);
                        return;
                    }
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
                out.toFile().deleteOnExit();
                if (f.contains("connector")) connector = out;
            }

            if (connector != null) {
                String d = dir.toAbsolutePath().toString();
                // lava-common: prefix = "lava.native.", ключ = lava.native.<lib>.<prop> с фолбэком lava.native.<prop>.
                // Один общий каталог покрывает ВСЕ либы (на Windows lavaplayer грузит libmpg123-0 И connector)
                // через фолбэк lava.native.dir -> грузит <dir>/<имя-либы> вместо распаковки из ресурсов.
                System.setProperty("lava.native.dir", d);
                System.out.println("[MediaPlayer] lavaplayer natives dir set: " + d);
            }
        } catch (Throwable t) {
            System.err.println("[MediaPlayer] native setup failed: " + t);
        }
    }
}
