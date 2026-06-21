# Архитектура ExosWare

Этот документ описывает общую архитектуру клиента **ExosWare** — его технологический стек, жизненный цикл, ключевые шаблоны проектирования и карту пакетов. Он предназначен для разработчиков, продолжающих проект, и служит точкой входа в более детальную документацию по подсистемам.

## Что это за проект

ExosWare — это клиентский мод-чит для **Minecraft 1.21.4** на платформе **Fabric**. Технически это обычный Fabric-мод (единственная точка входа `ru.levin.ExosWare`, реализующая `ModInitializer`), который через слой миксинов вклинивается в движок Minecraft и накладывает на него собственную систему модулей (читов), GUI, HUD и сетевых сервисов.

Координаты проекта:

| Параметр | Значение |
| --- | --- |
| `maven_group` | `ru.levin` |
| `archives_base_name` | `exosware` |
| `mod_version` | `1.21.4` (строка версии совпадает с версией MC) |
| Идентификатор мода (`fabric.mod.json`) | `exosware` |

Важная деталь анти-детекта: метаданные мода в `fabric.mod.json` **подделаны** — `name = "Minecraft"`, `description = "Основная игра!"`, чтобы в списке модов клиент маскировался под ванильную игру.

## Технологический стек

| Компонент | Версия / Деталь |
| --- | --- |
| Minecraft | 1.21.4 |
| Маппинги | Yarn `1.21.4+build.8` (v2) |
| Fabric Loader | `0.16.14` |
| Fabric API | `0.119.3+1.21.4` |
| Java | 21 (`compatibilityLevel JAVA_21`) |
| Kotlin | JVM `2.1.20` (stdlib; в проекте присутствует и stray `2.0.0` compileOnly) |
| Lombok | `1.18.32` (плагин `io.freefair.lombok 8.6`) |
| Build | Fabric Loom `1.10-SNAPSHOT` |
| Mixin | sponge-mixin `0.15.5+mixin.0.8.7` (форсирован; `useLegacyMixinAp=true`), refmap `exosware-refmap.json` |
| Шейдинг (`include`) | `netty-handler-proxy` + `netty-codec-socks` `4.1.82.Final` (поддержка SOCKS-прокси) |
| Нативные библиотеки | Discord-RPC (`darwin`, `linux-x86-64`, `win32-x86-64`) |

Подробная инвентаризация ресурсов (шрифты, шейдеры, звуки, текстуры, нативные библиотеки) и конфигурации сборки описана в `byKey 'build-resources'`.

## Жизненный цикл

Клиент использует **двухфазную инициализацию**: минимальная работа в `onInitialize()` (как требует Fabric) и отложенный полноценный bootstrap в `init()`, вызываемый из миксина уже после того, как существуют клиент и игрок.

### Фаза 1 — `onInitialize()`

Вызывается Fabric. Делает минимум:

1. `setupProtection()` → `NativeHelper.setProfile()` — заполняет `Manager.USER_PROFILE` захардкоженным `UserProfile("levin1337", "Deleoper", "09.11.2025")`. Реального лицензионного/авторизационного контроля в этом пути нет (см. `byKey 'protection'`).
2. Регистрирует JVM shutdown-hook, который вызывает `shutDown()` (исключения проглатываются).

Менеджеры здесь **НЕ** создаются.

### Фаза 2 — `init()` (отложенный bootstrap)

Вызывается из `MixinMinecraftClient` (конструктор, TAIL) — то есть только когда клиент и мир уже инициализированы. Весь метод обёрнут в один `try/catch`, который лишь печатает stack trace, поэтому сбой посреди bootstrap оставит часть менеджеров `null`.

Порядок создания менеджеров строго фиксирован:

```
ensureDirectoryExists()            // создаёт files/ и files/modules/
1.  SYNC_MANAGER          = new SyncManager()
2.  FUNCTION_MANAGER      = new FunctionManager()
3.  STYLE_MANAGER         = new StyleManager().init()
4.  ACCOUNT_MANAGER       = new AccountManager().init()   // авто-логин в последний альт
5.  FONT_MANAGER          = new FontUtils().init()
6.  COMMAND_MANAGER       = new CommandManager()
7.  DRAG_MANAGER          = new DragManager().init()
8.  MACROS_MANAGER        = new MacroManager().init()
9.  FRIEND_MANAGER        = new FriendManager().init()
10. STAFF_MANAGER         = new StaffManager().init()
11. NOTIFICATION_MANAGER  = new NotificationManager()
12. CHESTSTEALER_MANAGER  = new ChestStealerManager()
13. PROXY_MANAGER         = new ProxyManager().init()
14. IRC_MANAGER           = new IrcManager().connect(USER_PROFILE.getName())
15. CONFIG_MANAGER        = new ConfigManager().init()    // авто-загрузка AUTOCFG
    ColorUtil.loadImage(...)
    [опционально] AudioUtil.playSound("join.wav")
    initialized = true
```

> Примечание: `Manager.ROTATION` не создаётся здесь — это `final`-поле `Manager`, инициализируемое сразу как `RotationController.get()`.

### Завершение работы — `shutDown()`

Выполняется shutdown-hook'ом при выходе из JVM: сохраняет позиции HUD (`DRAG_MANAGER.save`), аккаунты (`ACCOUNT_MANAGER.saveAccounts` + `saveLastAlt`), конфиг (`CONFIG_MANAGER.saveConfiguration("autocfg")`), останавливает IRC, очищает `FUNCTION_MANAGER.globals`, печатает `[-] Client shutdown`.

Детали этой подсистемы — в `byKey 'core'`.

## Service-locator: статический `Manager`

Вместо dependency injection ExosWare использует **статический сервис-локатор** — класс `ru.levin.manager.Manager`. Это чистое глобальное состояние без логики: набор `public static`-полей, по одному на каждую подсистему.

```java
public class Manager {
    public static FunctionManager      FUNCTION_MANAGER;
    public static SyncManager          SYNC_MANAGER;
    public static StyleManager         STYLE_MANAGER;
    public static ConfigManager        CONFIG_MANAGER;
    public static IrcManager           IRC_MANAGER;
    public static AccountManager       ACCOUNT_MANAGER;
    public static ProxyManager         PROXY_MANAGER;
    // ... command, drag, friend, macro, notification, staff, font, cheststealer
    public static UserProfile          USER_PROFILE;
    public static final RotationController ROTATION = RotationController.get();
}
```

Практически каждая подсистема обращается к остальным через `Manager.*`. Доступ к самому `MinecraftClient` раздаётся через marker-интерфейс `IMinecraft`, который держит `static final mc = MinecraftClient.getInstance()` и подмешивается во множество классов через наследование.

## Триада Function + Event + Mixin

Поведение клиента строится на взаимодействии трёх слоёв.

### 1. Function (модуль)

Каждая фича-чит — это подкласс абстрактного `ru.levin.modules.Function`, помеченный аннотацией `@FunctionAnnotation(name, desc, key, type, keywords)`. Метаданные читаются рефлексией в `initializeProperties()`. Все ~90 модулей создаются в одном конструкторе `FunctionManager` и хранятся в статическом `CopyOnWriteArrayList<Function>`. Категоризация — через enum `Type` (Combat / Move / Render / Player / Misc). Каждый модуль объявляет свои настройки (`Setting`-подтипы) и реализует единственный метод `onEvent(Event)`. Подробности — `byKey 'module-framework'`.

### 2. Event (шина событий)

`ru.levin.events.Event` — базовый класс всех событий; несёт флаг `isCancel`. Вся диспетчеризация — это статический `Event.call(event)`:

```java
public static void call(final Event event) {
    if (mc.player == null || mc.world == null || event.isCancel()) return;
    if (!ClientManager.legitMode) {
        for (final Function module : Manager.FUNCTION_MANAGER.getFunctions())
            if (module.isState()) module.onEvent(event);
        Manager.SYNC_MANAGER.onEvent(event);
    }
}
```

Это синхронная broadcast-шина без регистрации слушателей: каждый включённый модуль получает каждое событие и сам фильтрует его через `instanceof`. `SYNC_MANAGER` всегда получает событие **последним** (после того как модули запросили повороты в рамках того же вызова). Конкретные классы событий (`EventUpdate`, `EventMotion`, `EventPacket`, `EventRender2D/3D` и т.д.) описаны в `byKey 'events'`.

### 3. Mixin (слой хуков)

56 миксинов (45 инжекторов + 11 accessor-интерфейсов), зарегистрированных в `exosware.mixins.json`, вклиниваются в методы Minecraft и являются **производителями** событий: они конструируют конкретный `Event` в точке хука движка и вызывают `Event.call(...)`. После диспетчеризации хук читает (возможно, изменённое) событие обратно — применяет новые yaw/pitch, отменяет ванильный вызов (`ci.cancel()`), подменяет пакет или булево значение. Также миксины напрямую читают флаги `Manager.FUNCTION_MANAGER.<module>.state` и `ClientManager.legitMode`. Полный каталог — `byKey 'mixins'`.

### Как это работает вместе

События — мутабельные in/out-параметры. Например, при тике движка:

1. `MixinClientPlayerEntity` фиксирует `EventUpdate` → модули (AttackAura и др.) выбирают цель и пишут поворот в `Manager.ROTATION`.
2. `MixinClientPlayerEntity` фиксирует `EventMotion` (несёт исходящий move-пакет) → модуль копирует `Manager.ROTATION.getYaw()/getPitch()` на пакет (silent aim), миксин затем восстанавливает реальный yaw/pitch игрока после отправки.
3. Рендер-миксины фиксируют `EventRender2D/3D` → модули рисуют HUD/ESP.

## legitMode / UnHook — анти-скриншер

`ClientManager.legitMode` (по умолчанию `false`) — это глобальный **kill-switch / «паника»**. Когда он `true`:

- `Event.call()` коротко замыкается: ни один модуль не получает событий, `SYNC_MANAGER` не вызывается.
- `keyPress()` блокирует все переключения модулей, открытие ClickGUI и макросы — кроме клавиши возврата UnHook.
- Многие миксины дополнительно гейтят визуальные изменения (брендинг окна, кастомное меню, плащ/элитры, скрытие оверлея эффектов, парсинг команд) на `!legitMode`, чтобы клиент выглядел «чистым» во время проверки модерацией.

Модуль `UnHook` (`onUnhook()`) отключает все активные функции, сохраняя их в статический `functionsToBack`, и **прячет** папку `C:\ExosWare` через `DosFileAttributeView.setHidden(true)` (только Windows/NTFS). Клавиша возврата (по умолчанию `GLFW_KEY_INSERT`, настройка «Кнопка возврата») разворачивает процесс: восстанавливает функции, снимает скрытие папки и сбрасывает `legitMode = false`. Логика дублируется в `ExosWare.keyPress()` и `UnHook.onUnhook()`.

## Поток данных (ASCII-диаграмма)

```
                         ┌─────────────────────────────┐
                         │       Minecraft 1.21.4       │
                         │  (tick / render / network /  │
                         │   input — ванильные методы)  │
                         └──────────────┬──────────────┘
                                        │ вызов ванильного метода
                                        ▼
                         ┌─────────────────────────────┐
                         │      Mixin (ru.levin.mixin)  │
                         │  @Inject / @Redirect / ...   │
                         │  конструирует Event-объект   │
                         └──────────────┬──────────────┘
                                        │ Event.call(event)
                                        ▼
                  ┌──────────────────────────────────────────┐
                  │  Event.call()                            │
                  │  guard: player/world != null,            │
                  │         !isCancel, !legitMode  ◄── ClientManager.legitMode
                  └──────────────┬───────────────────────────┘
                                 │ для каждого включённого Function
                                 ▼
        ┌────────────────────────────────────────────────────────┐
        │   enabled Functions  (FUNCTION_MANAGER.getFunctions())  │
        │   module.onEvent(event):                                │
        │     • читают Manager.* (SYNC_MANAGER, FRIEND_MANAGER…)  │
        │     • пишут поворот в Manager.ROTATION                  │
        │     • мутируют event (поля, setCancel)                  │
        └──────────────┬─────────────────────────────────────────┘
                       │ затем последним:
                       ▼
              ┌─────────────────────────┐
              │   SYNC_MANAGER.onEvent  │  кэширует сущности/игроков,
              │                         │  драйвит ROTATION, считает TPS
              └────────────┬────────────┘
                           │ (event, возможно изменённый)
                           ▼
        ┌────────────────────────────────────────────────────────┐
        │  Mixin читает event обратно:                            │
        │   • применить yaw/pitch (silent aim)                    │
        │   • ci.cancel() / подменить пакет / булево              │
        │   • восстановить реальный поворот игрока                │
        └──────────────┬─────────────────────────────────────────┘
                       │
                       ▼
              действие в мире / на экране / в сети
```

## Карта пакетов

```
ru.levin
├── ExosWare                  Точка входа ModInitializer; bootstrap, keyPress, shutDown, createDrag
│
├── manager                   Подсистемы-сервисы (сервис-локатор Manager)
│   ├── Manager               Статический реестр всех менеджеров + USER_PROFILE + ROTATION
│   ├── ClientManager         Хелперы клиента (FPS/ping/TPS/HP), флаг legitMode, gradient-чат
│   ├── IMinecraft            Marker-интерфейс с общим static mc
│   ├── SyncManager           Центральный приёмник событий: кэш сущностей, ротация, TPS
│   ├── UrlManager            Backend-хост + AES-расшифровка Discord-webhook
│   ├── accountManager        Альт-аккаунты (offline-логин), персист в files/alts.ew
│   ├── apiManager            ClientAPI — TCP-проверка «этот ник — юзер клиента?»
│   ├── commandManager        Brigadier-команды по префиксу «.» (16 команд)
│   ├── configManager         Именованные .cfg-конфиги, авто-загрузка AUTOCFG
│   ├── dragManager           Перетаскиваемые HUD-якоря (files/drag.ew)
│   ├── fontManager           FontUtils/RenderFonts — рантайм-рендер глифов из TTF
│   ├── friendManager         Список друзей (files/friends.ew)
│   ├── ircManager            Кастомный TCP-«IRC»-чат с HMAC-аутентификацией
│   ├── macroManager          Клавиша → чат/команда (files/macros.ew)
│   ├── modulesManager        ChestStealerManager — whitelist предметов
│   ├── notificationManager   Анимированные тосты (без персиста)
│   ├── proxyManager          SOCKS4/5-прокси, инжект в netty-pipeline
│   ├── staffManager          Список стаффа для детектора в HUD
│   └── themeManager          StyleManager/Style — градиентные темы (files/themes.ew)
│
├── modules                   Фреймворк модулей + сами модули
│   ├── Function              Абстрактная база модуля (онэвент, настройки, save/load)
│   ├── FunctionManager       Реестр-конструктор всех ~90 модулей
│   ├── FunctionAnnotation    @FunctionAnnotation (метаданные модуля)
│   ├── Type                  Enum категорий: Combat/Move/Render/Player/Misc
│   ├── setting               7 подтипов Setting (Boolean/Mode/Slider/Multi/Text/Bind/BindBoolean)
│   ├── combat                17 боевых модулей + RotationController (Manager.ROTATION)
│   ├── movement              18 модулей перемещения (Speed/Flight/Blink/Elytra…)
│   ├── render                25 визуальных модулей (ESP/NameTags/HUD/TargetESP…)
│   ├── player                24 модуля инвентаря/эксплойтов/QoL
│   └── misc                  ~19 утилитарных модулей (UnHook/NameProtect/Xray/RPC…)
│
├── events                    Шина событий
│   ├── Event                 База + static call() (диспетчер)
│   └── impl                  Конкретные события (input/move/player/render/world/…)
│
├── mixin                     Слой хуков (56 миксинов, exosware.mixins.json)
│   ├── attack / chat / client / display / player / util / world
│   └── iface                 11 accessor/invoker-интерфейсов к приватным полям MC
│
├── screens                   GUI
│   ├── dropdown              ClickGUI + per-setting рендереры
│   ├── altmanager            AltManager (менеджер аккаунтов)
│   ├── mainmenu              Кастомное главное меню
│   └── unhook                UnHookScreen («паника»)
│
├── protect                   Лицензирование/обфускация (фактически заглушка)
│   ├── AES                   AES-хелпер (захардкоженный ключ, ECB)
│   ├── NativeHelper          setProfile() — захардкоженный USER_PROFILE
│   ├── UserProfile           POJO: имя/роль/срок подписки
│   └── loader.NativeProfile  Псевдо-«нативный» загрузчик (мёртвый код)
│
├── com.discord               JNA-биндинг к нативной discord-rpc (Rich Presence)
│
└── util                      Низкоуровневые хелперы
    ├── animations            Easing/Animation (анимации UI)
    ├── color                 ColorUtil — ARGB-математика, градиенты, темы
    ├── math                  MathUtil, RayTraceUtil
    ├── move                  MoveUtil, NetworkUtils (silent-пакеты)
    ├── player                AuraUtil, GCDUtil, InventoryUtil, TimerUtil, AudioUtil…
    ├── render                RenderUtil/Render3DUtil/RenderAddon/Scissor (2D/3D рендер)
    ├── shader                ShaderManager (vertex-эмиттеры)
    └── vector                VectorUtil — world→screen проекция
```

## Связанная документация

- **`core`** — точка входа, жизненный цикл, менеджеры `Manager`/`ClientManager`/`SyncManager`, `legitMode`, `ClientAPI`, webhook.
- **`module-framework`** — `Function`, `@FunctionAnnotation`, `Type`, подтипы `Setting`, персистентность модулей.
- **`events`** — шина `Event`, все конкретные события и их точки запуска.
- **`mixins`** — полный каталог миксинов и accessor-интерфейсов.
- **`build-resources`** — система сборки (Loom/Kotlin/Java 21), зависимости, инвентарь ресурсов и нативных библиотек.
- Дополнительно по подсистемам: `combat-modules`, `movement-modules`, `render-modules`, `player-modules`, `misc-modules`, `managers`, `commands`, `protection`, `screens`, `utilities`, `discord`.
