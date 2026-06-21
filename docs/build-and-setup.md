# Сборка и запуск

Клиент **OneTap** (бывш. ExosWare) — модификация Minecraft **1.21.1** на платформе **NeoForge**, написанная на Java 21 и собираемая через Gradle + **ModDevGradle**.

> Проект портирован с Fabric 1.21.4. История и детали порта — в [migration-neoforge-1.21.1.md](migration-neoforge-1.21.1.md) и [migration-mappings.md](migration-mappings.md). Архитектура (`Function`/`Event`/`Mixin`, менеджеры, модули) при порте не менялась — см. [architecture.md](architecture.md).

Группа Maven — `ru.levin`; базовое имя архива — `onetap`; **modId** (`META-INF/neoforge.mods.toml`) — `onetap`; namespace ассетов остался `exosware` (`assets/exosware/...`).

---

## Требования

| Компонент | Версия / требование |
| --- | --- |
| JDK | Java **21** (обязательно; `options.release = 21`, toolchain 21) |
| Gradle | через Wrapper (`./gradlew`), `-Xmx3G` (декомпиляция NeoForm требует памяти) |
| Нативные либы Discord-RPC | Windows x86-64, Linux x86-64, macOS (darwin); ARM/aarch64 не поставляются |
| Интернет | для NeoForged Maven + Maven Central |

---

## Версии (`gradle.properties`)

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.parallel=true

# Platform
minecraft_version=1.21.1
neoforge_version=21.1.233

# Mod Properties
mod_version=1.0.0
maven_group=ru.levin
archives_base_name=onetap
```

| Свойство | Значение | Назначение |
| --- | --- | --- |
| `minecraft_version` | `1.21.1` | Целевая версия игры |
| `neoforge_version` | `21.1.233` | NeoForge (LTS-ветка под 1.21.1); официальные маппинги Mojang (Mojmap) |
| `mod_version` | `1.0.0` | Версия мода (отвязана от версии MC) |
| `archives_base_name` | `onetap` | Имя jar → `onetap-1.0.0.jar` |

---

## Сборка

```bash
# Команда, используемая в проекте (PowerShell):
$env:GRADLE_USER_HOME="D:\gradle_home"; .\gradlew.bat build --no-daemon
# или из bash:
GRADLE_USER_HOME=/d/gradle_home ./gradlew build --no-daemon
```

Готовый jar — `build/libs/onetap-1.0.0.jar` (≈18.8 МБ), плюс `onetap-1.0.0-sources.jar`.

Первый запуск любой gradle-задачи скачивает NeoForge и **декомпилирует Minecraft** в Mojmap (NeoForm) — это пара минут и заметный объём в `GRADLE_USER_HOME`.

## Запуск dev-клиента

```bash
GRADLE_USER_HOME=/d/gradle_home ./gradlew runClient --no-daemon
```

Конфигурация запуска (`build.gradle` → `neoForge { runs { client { client(); gameDirectory = file('run') } } }`) поднимает клиент из `run/`.

---

## `build.gradle` (ключевое)

```groovy
plugins {
    id 'java'
    id 'net.neoforged.moddev' version '2.0.141'
    id 'io.freefair.lombok' version '8.6'
}

neoForge {
    version = project.neoforge_version
    runs { client { client(); gameDirectory = file('run') } }
    mods { exosware { sourceSet sourceSets.main } }
}

dependencies {
    // SOCKS/proxy-кодеки для ProxyManager. На compile-класспасе...
    implementation 'io.netty:netty-handler-proxy:4.1.82.Final'
    implementation 'io.netty:netty-codec-socks:4.1.82.Final'
    // ...и вложены в jar через jarJar (META-INF/jarjar/), transitive=false —
    // netty-codec/transport/common уже есть в MC (4.1.x бинарно совместим).
    jarJar(group: 'io.netty', name: 'netty-handler-proxy', version: '[4.1.82.Final]') { transitive = false }
    jarJar(group: 'io.netty', name: 'netty-codec-socks',  version: '[4.1.82.Final]') { transitive = false }
}
```

- **Kotlin выброшен** (в исходниках 0 `.kt`-файлов). **Lombok** остаётся (`io.freefair.lombok`).
- **Mixin**: конфиг `exosware.mixins.json` (`defaultRequire: 1`) подключается через `[[mixins]]` в `neoforge.mods.toml`. На NeoForge рантайм — Mojmap, поэтому refmap фактически no-op (варнинг «`exosware-refmap.json` could not be read» при старте безвреден).
- **MixinExtras** (`@ModifyExpressionValue`, `@ModifyReturnValue`, `@Local`) — поставляется NeoForge (0.5.3), отдельная зависимость не нужна.
- `processResources` подставляет `${mod_version}` в `META-INF/neoforge.mods.toml`.

---

## Метаданные мода — `src/main/resources/META-INF/neoforge.mods.toml`

```toml
modLoader = "javafml"
loaderVersion = "[4,)"
[[mods]]
modId = "onetap"
version = "${mod_version}"
displayName = "OneTap"
[[mixins]]
config = "exosware.mixins.json"
[[dependencies.onetap]]  # на neoforge [21.1,) и minecraft [1.21.1,1.22), side = CLIENT
```

Точка входа — `@Mod("onetap")` на классе `ru.levin.ExosWare` (конструктор `(IEventBus, ModContainer)`); полная инициализация менеджеров отложена в `ExosWare.init()`, вызываемый миксином из конструктора `Minecraft`.

---

## Ресурсы (`src/main/resources/`)

| Категория | Путь | Содержимое |
| --- | --- | --- |
| Шрифты | `assets/exosware/font/` | 13 TTF (gilroy, sf, comfortaa, icomoon, icons…) |
| Шейдеры | `assets/exosware/shaders/core/` | кастомные core-шейдеры (`texture/rectangle/blur/border/glass`), грузятся через `ShaderRegistry` (`RegisterShadersEvent`). В JSON поля `vertex`/`fragment` — `exosware:<name>` (1.21.1 сам добавляет `shaders/core/`) |
| Звуки | `assets/exosware/sounds/` | 13 WAV |
| Изображения | `assets/exosware/images/`, `cape/`, `icon.png` | GUI/particles/targetesp/cape |
| Discord-RPC нативы | `win32-x86-64/`, `linux-x86-64/`, `darwin/` | `discord-rpc.{dll,so,dylib}` — грузятся JNA по пути `{os-prefix}/discord-rpc.{ext}`; **НЕ** через jarJar, лежат в корне ресурсов |

---

## Рантайм-каталоги

Клиент создаёт `<gameDir>/files` и `<gameDir>/files/modules` (конфиги, аккаунты, позиции HUD, аддоны). Анти-скриншер `UnHook` прячет папку `C:\OneTap`.

---

## Установка собранного мода

`onetap-1.0.0.jar` кладётся в `mods/` любого профиля **NeoForge 21.1.x для Minecraft 1.21.1**. Зависимостей-модов нет (netty-прокси вложен в jar; Discord-RPC нативы — внутри). Клиентский мод (`side = CLIENT`).
