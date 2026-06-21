# Менеджеры-сервисы и команды чата

Этот документ описывает два тесно связанных слоя клиента **ExosWare** (Fabric 1.21.4, группа `ru.levin`, archives name `exosware`):

1. **Сервисные менеджеры** (`ru.levin.manager.*`) — singleton-сервисы, зарегистрированные как статические поля класса `Manager`, отвечающие за персистентность, сеть и состояние клиента.
2. **Система команд чата** (`ru.levin.manager.commandManager`) — Brigadier-диспетчер, перехватывающий сообщения с префиксом `.` и исполняющий их на стороне клиента.

Оба слоя завязаны на глобальный service-locator `Manager` и на анти-детект флаг `ClientManager.legitMode`: пока он `true`, система команд полностью отключена (см. ниже).

---

## Часть 1. Сервисные менеджеры

Все менеджеры создаются и инициализируются в фиксированном порядке внутри `ExosWare.init()` (вызывается отложенно из `MixinMinecraftClient`). Доступ к ним идёт через статические поля `Manager.*`. Файлы персистентности располагаются под `<runDirectory>/files/` с собственным расширением `.ew` (либо `.cfg` для конфигов).

### Сводная таблица

| Менеджер | Поле в `Manager` | Назначение | Файлы персистентности | Сетевые хосты |
| --- | --- | --- | --- | --- |
| `AccountManager` | `ACCOUNT_MANAGER` | Хранилище альт-аккаунтов; авто-вход в последний альт при старте (offline/cracked сессия) | `files/alts.ew` (по имени на строку), `files/lastAlt.ew` (последний выбранный) | — (локальная подмена `Session`) |
| `ConfigManager` | `CONFIG_MANAGER` | Именованные конфиги модулей; авто-загрузка `AUTOCFG.cfg` на старте | `files/configs/*.cfg` (JSON), `files/configs/AUTOCFG.cfg` | — |
| `DragManager` / `Dragging` | `DRAG_MANAGER` | Позиции перетаскиваемых HUD-элементов, привязка к сетке, направляющие | `files/drag.ew` (Gson, только `@Expose`-поля `x`, `y`, `name`) | — |
| `FriendManager` / `Friend` | `FRIEND_MANAGER` | Список друзей (защита от урона, окраска ESP/неймтегов) | `files/friends.ew` (по имени на строку) | — |
| `IrcManager` | `IRC_MANAGER` | Встроенный TCP-чат «IRC» с HMAC-аутентификацией, градиентные строки, ignore-лист | нет персистентности | `11.1.1:3025` (хардкод, **некорректный/placeholder IP**) |
| `MacroManager` / `Macro` | `MACROS_MANAGER` | Чат-макросы, привязанные к клавишам; `/`-префикс → команда, иначе чат | `files/macros.ew` (формат `message:key` на строку) | — |
| `ChestStealerManager` | `CHESTSTEALER_MANAGER` | Белый список предметов для модуля ChestStealer | `files/modules/cheststealer.ew` (Identifier на строку) | — |
| `NotificationManager` / `Notification` | `NOTIFICATION_MANAGER` | Анимированные тосты (INFO/SUCCESS/REMOVED) в правом-нижнем углу | нет персистентности (in-memory) | — |
| `ProxyManager` / `Proxy` / `GuiProxy` | `PROXY_MANAGER` | Конфиг SOCKS4/SOCKS5 прокси; туннель внедряется в netty-pipeline миксином | `files/proxy.ew` (JSON) | пользовательский SOCKS-endpoint (вводится в GUI) |
| `StaffManager` | `STAFF_MANAGER` | Ручной список «стафф»-ников для HUD-детектора модераторов | `files/staff.ew` (по имени на строку) | — |

> Примечание: единственные реальные исходящие хосты на этом слое — `IrcManager` (хардкод `11.1.1:3025`, фактически неработоспособен в исходном виде) и пользовательский SOCKS-прокси, внедряемый через `MixinClientConnectionInitMixin`. Все остальные менеджеры работают только с локальными файлами.

### Детали по менеджерам

#### `AccountManager`

Хранит список альтов (`files/alts.ew`) и последний выбранный ник (`files/lastAlt.ew`). На `init()` метод `loadLastAlt()` автоматически вызывает `ClientManager.loginAccount(selected)`, создавая **offline/cracked**-сессию:

- UUID: `Uuid.nameUUIDFromBytes("OfflinePlayer:" + name)`
- токен: `"invalid_token"`, тип `AccountType.MOJANG`
- подмена через `MinecraftClientAccessor.setSession`

Это спуфинг альтов, а не настоящая авторизация Mojang/Microsoft.

#### `ConfigManager` / `Config`

Конфиги — это JSON-файлы `*.cfg` под `files/configs/`. Модель `Config` имеет два корневых блока:

- `Features` — результат `save()`/`load()` каждой `Function` (состояние модуля + значения настроек);
- `Others` — `author` (имя из `UserProfile`), `time` (`System.currentTimeMillis()`), `theme` (имя темы `StyleManager`).

При загрузке с `start=false` сперва принудительно выключаются все активные модули, затем применяется сохранённое состояние. Пути хардкодятся с Windows-разделителями (`\\files\\configs`). Все пользовательские сообщения — на русском.

#### `DragManager` / `Dragging`

Сериализует только `@Expose`-поля (`x`, `y`, `name`) в `files/drag.ew` через Gson (`excludeFieldsWithoutExposeAnnotation`). Перетаскивание активно только когда `currentScreen` — `ChatScreen` и зажата ЛКМ. Shift привязывает к центру экрана и центрам других элементов (`DRAG_SMOOTHNESS = 0.1`, дистанция привязки 10px). `reset()` пересобирает раскладку из `defaultX`/`defaultY` каждого элемента.

#### `FriendManager` / `Friend`

Потокобезопасный `CopyOnWriteArrayList`; имена сохраняются построчно в `files/friends.ew` (перезапись `TRUNCATE_EXISTING`). `isFriend`/`remove` регистронезависимы.

#### `IrcManager`

Кастомный TCP-чат. Константы: `cheatName = "ExosWare"`, `secretKey = "levinAntiKotopishka"`, хост `11.1.1:3025`.

Хендшейк challenge-response:
1. Сервер присылает строку-nonce.
2. Клиент отвечает `ExosWare:<nick>:<HmacSHA256(secretKey, nonce + nick)>`.

Параметры: `CONNECT_TIMEOUT_MS = 5000`, `RETRY_DELAY_MS = 15_000` (авто-реконнект каждые 15с), `MESSAGES` ограничен 200 строками. Три daemon-потока (`IRC-Reader`, `IRC-Writer`, `IRC-Scheduler`) + `BlockingQueue` исходящих сообщений. Градиентный текст (`0x808080` → `0xFFFFFF`), множество игнорируемых ников. **Хост `11.1.1` некорректен** (3 октета) — соединение в исходном виде всегда падает; реальный адрес, видимо, подставляется при сборке релиза.

#### `MacroManager` / `Macro`

Макросы привязаны к клавишам, формат строки в `files/macros.ew` — `message:key`. В `onKeyPressed` отрицательные (мышиные) коды нормализуются как `-(100 + key + 2)`; сообщение с префиксом `/` отправляется как команда, иначе как чат. Парсинг по `:` ломается, если само сообщение содержит `:`.

#### `ChestStealerManager`

Белый список предметов (`Set<Item>`) в `files/modules/cheststealer.ew` (один Identifier на строку, валидация по `Registries.ITEM`). Если файл пуст — засевается значениями по умолчанию: `minecraft:totem_of_undying`, `minecraft:player_head`.

#### `NotificationManager` / `Notification` / `NotificationType`

In-memory очередь (`CopyOnWriteArrayList`), без персистентности. Тосты рисуются в правом-нижнем углу со слайд/фейд анимацией (`DecelerateAnimation`, 500мс). Аргумент `time` конструктора — в **секундах** (умножается на 1000). Типы и иконки (`images/state/<texture>`, 16×16):

| Тип | Иконка |
| --- | --- |
| `INFO` | `info.png` |
| `SUCCESS` | `add.png` |
| `REMOVED` | `remove.png` |

#### `ProxyManager` / `Proxy` / `GuiProxy`

Конфиг прокси в `files/proxy.ew` (JSON: `{proxy-enabled, accounts:{name -> Proxy}}`). `Proxy` хранит `ipPort` (`@SerializedName "IP:PORT"`), `type` (`SOCKS4`/`SOCKS5`, по умолчанию `SOCKS5`), `username`, `password`. Редактор — экран `GuiProxy`. Само туннелирование внедряется миксином `MixinClientConnectionInitMixin`, который добавляет `Socks5ProxyHandler`/`Socks4ProxyHandler` в начало netty-pipeline при `proxyEnabled`. Валидация IP:PORT только в `GuiProxy.isValidIpPort` (порт 0..65535) — повреждённый `proxy.ew` может уронить путь подключения.

#### `StaffManager`

Ручной список ников в `files/staff.ew`, читается HUD-детектором стаффа (вместе с regex по командным префиксам). Асимметрия: `isStaff` регистрозависим (`equals`), `removeStaff` регистронезависим; `addStaff` допускает дубликаты.

---

## Часть 2. Система команд чата

### Перехват и диспетчеризация

Система реализована на собственном экземпляре Mojang Brigadier `CommandDispatcher<CommandSource>`, а не через Fabric Client Command API.

- **Префикс:** хардкод `"."` (`CommandManager.prefix`, доступен только через `getPrefix()`, сеттера нет).
- **Источник команды:** `new ClientCommandSource(null, mc)`.
- **Точка входа:** `MixinClientPlayNetworkHandler` инжектится в `HEAD` метода `ClientPlayNetworkHandler#sendChatMessage` (cancellable). Если `!ClientManager.legitMode` и сообщение начинается с префикса — префикс срезается, остаток передаётся в `dispatcher.execute(...)`, а отправка пакета отменяется (текст команды на сервер не уходит). `CommandSyntaxException` молча проглатывается.
- **Автодополнение:** `MixinChatInputSuggestor` инжектится в `ChatInputSuggestor#refresh`; при префиксе и `!legitMode` курсор сдвигается на `+1`, строка парсится клиентским диспетчером, и `getCompletionSuggestions` подаётся в ванильное окно подсказок.

> **Анти-детект:** при `ClientManager.legitMode == true` оба миксина не срабатывают — клиент ведёт себя как ванильный чат, команды недоступны.

Регистрация всех 16 команд выполняется в конструкторе `CommandManager`. Каждая команда — подкласс абстрактного `Command` (хранит литерал-имя, строит `LiteralArgumentBuilder`, реализует `execute(builder)`); все исполнители возвращают `com.mojang.brigadier.Command.SINGLE_SUCCESS`.

### Типы аргументов

| Тип | Источник | Поведение |
| --- | --- | --- |
| `StringArgumentType.word` / `greedyString` | Brigadier | Одно слово / весь остаток строки |
| `IntegerArgumentType.integer` | Brigadier | Целое (координаты GPS/Way) |
| `DoubleArgumentType.doubleArg` | Brigadier | Дробное (смещения vclip/hclip) |
| `PlayerArgumentType` | `impl/args` | `parse()` без валидации; подсказки — онлайн-игроки из `networkHandler.getPlayerList()`; примеры `Steve/Alex/Notch/levinPaster` |
| `FriendArgumentType` | `impl/args` | `parse()` бросает `DynamicCommandExceptionType` («У тебя нет друга `<name>`»), если ник не в `FRIEND_MANAGER`; подсказки/примеры из списка друзей |

### Таблица команд

| Команда | Назначение | Подкоманды / форма | Бэкенд |
| --- | --- | --- | --- |
| `friend` | Управление списком друзей | `add <player>`, `remove <player>`, `clear`, `list` | `FRIEND_MANAGER` (`PlayerArgumentType`/`FriendArgumentType`) |
| `gps` | 2D GPS-вейпоинт + HUD-стрелка с дистанцией | `.gps <x> <z>`, `.gps set <x> <z>`, `.gps off` | static `render(MatrixStack)`, `images/triangle2.png`, сглаживание yaw (4.0°/кадр) |
| `way` | Менеджер вейпоинтов с подписями | `add <name>` (текущая поз.) / `add <name> <x> <y> <z>`, `remove <name>`, `list` | `files/modules/waypoints.ew` (Gson `Map<String,BlockPos>`) |
| `unhook` | Задать кастомную папку назначения UnHook | `path <folder>` (greedyString, подсказки по соседним каталогам) | `files/modules/UnHook.ew` (`CUSTOM_PATH_FILE`) |
| `cfg` | Менеджер конфигов | `save/load/remove <name>`, `list`, `reset`, `dir`, `clear` | `CONFIG_MANAGER`; имена приводятся к верхнему регистру; `dir` → `explorer` |
| `cheststealer` | Белый список предметов ChestStealer | `add <item>`, `remove <item>`, `list` | `CHESTSTEALER_MANAGER`; префикс `minecraft:` если нет namespace |
| `staff` | Список наблюдения за стаффом | `add <name>`, `remove <name>`, `clear`, `list`, `reload` | `STAFF_MANAGER` |
| `blockesp` | Кастомные блоки для BlockESP | `add <block> <color>`, `remove <block>` | `FUNCTION_MANAGER.blockESP`; цвет из карты EN/RU имён или HEX, alpha=150 |
| `bind` | Менеджер биндов модулей | `add <module> <key>`, `remove <module> <key>`, `list`, `clear` | `FUNCTION_MANAGER.get`, `KeyMappings.keyCode`; `clear` не трогает `clickGUI` |
| `drag` | Позиции HUD-элементов | `reset`, `save` | `DRAG_MANAGER` |
| `irc` | Встроенный IRC-чат | `ignore <nick>`, `unignore <nick>`, `ignorelist`, `.irc <message>` | `IRC_MANAGER`; блок ссылок regex, требует включённого модуля `irc` |
| `macro` | Чат-макросы по клавишам | `add <key> <message>`, `remove <key>`, `list`, `clear` | `MACROS_MANAGER`, `KeyMappings.keyCode` |
| `panic` | Мгновенно выключить все активные модули | (без аргументов) | `FUNCTION_MANAGER.getFunctions()` → `setState(false)` (kill-switch) |
| `parse` | Скрейп таб-листа по префиксам привилегий в файл | `start`, `dir` | `files/parser/<serverIp>.txt`; карта `PRIVILEGE_REPLACEMENTS` (server-specific) |
| `vclip` | Вертикальный клип-телепорт | `up`, `down`, `.vclip <offset>` | 19+19 спуф-пакетов `PlayerMoveC2SPacket.PositionAndOnGround` |
| `hclip` | Горизонтальный клип-телепорт по взгляду | `.hclip <offset>` | тот же паттерн 19+19 пакетов; offset `0` отклоняется |

### Особенности и подводные камни

- Префикс жёстко равен `.`; `MixinChatInputSuggestor` сдвигает курсор ровно на `+1`, поэтому многосимвольный префикс сломал бы автодополнение, хотя исполнитель использует `prefix.length()`.
- `CommandSyntaxException` при `execute` глотается без обратной связи (сообщение чата всё равно отменяется).
- `legitMode` короткозамыкает всю систему в обоих миксинах.
- `getDispatcher()` возвращает «сырой» (не параметризованный) `CommandDispatcher` — unchecked-использование.
- `vclip`/`hclip` шлют ровно **19** дублирующих пакетов позиции до и после перемещения (магическое число) — телепорт-эксплойт, вероятно палится анти-читом.
- `parse` хардкодит §-цветовые + private-use глифы привилегий конкретного (русского) сервера; всё прочее пишется как есть.
- Часть путей открывается через `Runtime.getRuntime().exec("explorer " + path)` — только Windows; `cfg dir` не кавычит путь (в отличие от `parse`).
- Все пользовательские строки — на русском; названия ролей/привилегий ориентированы на русский сервер.
- `irc` фильтрует ссылки regex `(https?://|www\.)\S+` через `matches()` (совпадение всей строки), поэтому ссылка в середине сообщения не отлавливается.
- `blockesp` принудительно ставит alpha цвета в 150; `gps` принудительно берёт Y игрока (режим 2D).
