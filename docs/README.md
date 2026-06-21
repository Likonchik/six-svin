# OneTap (бывш. ExosWare) — документация проекта

> ⚠️ **Проект портирован: Fabric 1.21.4 → NeoForge 1.21.1**, и переименован в **OneTap** (modId `onetap`).
> Сборка теперь — **ModDevGradle** (см. [build-and-setup.md](build-and-setup.md)); процесс и таблицы маппингов порта —
> [migration-neoforge-1.21.1.md](migration-neoforge-1.21.1.md) и [migration-mappings.md](migration-mappings.md).
> **Архитектура, модули, события и миксины при порте по смыслу не менялись** — остальные документы ниже описывают
> ту же логику; различаются лишь имена API (Yarn → Mojmap), загрузчик и точечные дельты 1.21.2. Java-пакет остался
> `ru.levin`, класс точки входа — по-прежнему `ru.levin.ExosWare` (теперь `@Mod`, не `ModInitializer`),
> namespace ассетов — `exosware`.

Изначально `ExosWare` — клиентский чит для Minecraft 1.21.4 на загрузчике **Fabric**, написанный преимущественно на Java 21 с примесью Kotlin. Группа артефакта — `ru.levin`, имя архива — `exosware`, точка входа — `ru.levin.ExosWare` (реализует `ModInitializer`). Клиент включает боевые, движенческие, визуальные, инвентарные и сервис-ориентированные модули, кастомный GUI с темами и шрифтами, систему чат-команд на Brigadier, а также сетевой слой (IRC-чат, peer-распознавание пользователей клиента, SOCKS-прокси, Discord Rich Presence).

> Метаданные мода (`fabric.mod.json`) намеренно замаскированы под ваниль: `name = "Minecraft"`, `description = "Основная игра!"`. Это часть anti-detection стратегии клиента.

## Технологический стек

| Компонент | Версия / деталь |
| --- | --- |
| Minecraft | 1.21.4 |
| Загрузчик | Fabric Loader 0.16.14 |
| Fabric API | 0.119.3+1.21.4 |
| Маппинги | Yarn 1.21.4+build.8 (v2) |
| Сборка | Gradle + Fabric Loom 1.10-SNAPSHOT |
| Язык | Java 21 + Kotlin 2.1.20 |
| Доп. инструменты | Lombok 1.18.32 (`io.freefair.lombok` 8.6) |
| Mixin | sponge-mixin 0.15.5+mixin.0.8.7 (legacy mixin AP), refmap `exosware-refmap.json` |
| Шейдинг (`include`) | `netty-handler-proxy` + `netty-codec-socks` 4.1.82.Final (SOCKS-прокси) |
| Нативные библиотеки | Discord-RPC для `win32-x86-64`, `linux-x86-64`, `darwin` (через JNA) |

## Ключевые цифры

- **~90 модулей** (`Function`), распределённых по 5 категориям: `Combat`, `Move`, `Render`, `Player`, `Misc`.
- **~70 миксинов** (по `exosware.mixins.json` зарегистрировано 62: 45+ инжекторов и ~14 accessor/invoker-интерфейсов).
- **~27 000 строк** исходного кода Java/Kotlin.
- **~17 менеджеров-сервисов**, зарегистрированных как статические поля в `Manager`.
- **19 классов событий** на собственной шине `Event` (17 активно вызываются).
- **7 типов настроек** модулей (`BooleanSetting`, `ModeSetting`, `SliderSetting`, `MultiSetting`, `TextSetting`, `BindSetting`, `BindBooleanSetting`).
- **16 чат-команд** на Brigadier с префиксом `.`.
- **13 TTF-шрифтов**, **13 WAV-звуков**, кастомные GLSL-шейдеры (blur/glass/border/rectangle/texture).

## Быстрый старт

### Требования
- JDK **21**.
- Gradle (или поставляемый `gradlew`).

### Сборка
```bash
./gradlew build
```
Готовый jar появится в `build/libs/`. Имя архива формируется из `archives_base_name = exosware`, версия (`mod_version`) равна `1.21.4`.

### Запуск клиента из dev-окружения
```bash
./gradlew runClient
```
Конфигурация запуска добавляет VM-аргумент `-Dfabric.legacyClassPath=true` и свойство `fabric.development=true`.

### Особенности bootstrap
- `ExosWare.onInitialize()` (Fabric) делает минимум: вызывает `setupProtection()` (`NativeHelper.setProfile`) и регистрирует JVM shutdown-hook.
- Полная инициализация менеджеров (`ExosWare.init()`) выполняется **отложенно** из `MixinMinecraftClient` (когда уже существуют клиент и игрок), а не из `onInitialize()`.
- Рантайм-данные создаются в каталогах `<runDirectory>/files` и `<runDirectory>/files/modules`.

## Структура пакетов `ru.levin.*`

```
ru.levin
├── ExosWare                 // точка входа, lifecycle-корень
├── manager                  // сервис-локатор Manager + ~17 менеджеров
│   ├── accountManager       // альт-аккаунты (offline-вход)
│   ├── apiManager           // ClientAPI (TCP peer-распознавание)
│   ├── commandManager       // Brigadier чат-команды (.префикс) + impl/args
│   ├── configManager        // .cfg JSON-конфиги
│   ├── dragManager          // позиции HUD-элементов
│   ├── fontManager          // FontUtils / RenderFonts (рендер шрифтов)
│   ├── friendManager        // список друзей
│   ├── ircManager           // кастомный IRC-чат (HMAC-аутентификация)
│   ├── macroManager         // чат-макросы по биндам
│   ├── modulesManager       // ChestStealerManager (whitelist предметов)
│   ├── notificationManager  // экранные toast-уведомления
│   ├── proxyManager         // SOCKS4/5 + GuiProxy
│   ├── staffManager         // список стаффа
│   └── themeManager         // StyleManager / Style (градиентные темы)
├── modules                  // фреймворк Function + 5 категорий
│   ├── combat               // AttackAura, CrystalAura, AutoTotem ... + rotation
│   ├── movement             // Speed, Flight, Blink, Elytra* ...
│   ├── render               // ESP, NameTags, HUD, TargetESP ...
│   ├── player               // ChestStealer, AutoTool, эксплойты ...
│   ├── misc                 // UnHook, NameProtect, Xray, DiscordRCP ...
│   └── setting              // 7 подтипов Setting
├── events                   // шина Event + events.impl.* (input/move/player/render/world)
├── mixin                    // 62 миксина: world/client/display/player/attack/chat/util/iface
├── screens                  // ClickGUI (dropdown), AltManager, MainMenu, UnHookScreen
├── protect                  // AES, NativeHelper, UserProfile, loader.NativeProfile
├── com.discord              // JNA-биндинг к libdiscord-rpc
└── util                     // animations, color, math, move, player, render, shader, vector
```

## Навигация по документации

| Документ | Описание |
| --- | --- |
| [architecture.md](architecture.md) | Точка входа, lifecycle, сервис-локатор `Manager`, `SyncManager`, `legitMode` и общая инфраструктура ядра. |
| [module-system.md](module-system.md) | Фреймворк модулей: `Function`, `@FunctionAnnotation`, `Type`, 7 типов `Setting`, тогглы и JSON-персистентность. |
| [modules-catalog.md](modules-catalog.md) | Каталог всех ~90 модулей по категориям Combat/Move/Render/Player/Misc с настройками и поведением. |
| [event-system.md](event-system.md) | Шина событий `Event`: диспетчеризация, 19 классов событий, отмена/мутация, kill-switch `legitMode`. |
| [mixins.md](mixins.md) | Слой миксинов: инжекторы и accessor-интерфейсы, привязка ваниль → шина событий и модули. |
| [gui-and-screens.md](gui-and-screens.md) | ClickGUI и рендереры настроек, AltManager, MainMenu, UnHookScreen, система тем и шрифтов. |
| [managers-and-commands.md](managers-and-commands.md) | Сервис-менеджеры (аккаунты, конфиги, друзья, IRC, прокси и др.) и система чат-команд Brigadier. |
| [protection-and-networking.md](protection-and-networking.md) | Лицензионный/защитный слой (`protect`), AES, `ClientAPI`, IRC, webhook и сетевые хосты. |
| [utilities-and-rendering.md](utilities-and-rendering.md) | Библиотеки `ru.levin.util`: анимации, цвет, математика, движение, рендер 2D/3D, шейдеры, векторы. |
| [discord-rpc.md](discord-rpc.md) | JNA-биндинг к discord-rpc и модуль `DiscordRCP` (Rich Presence). |
| [build-and-setup.md](build-and-setup.md) | Система сборки, зависимости, инвентаризация ресурсов/ассетов и нативных библиотек. |
