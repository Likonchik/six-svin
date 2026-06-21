# Discord Rich Presence

Подсистема Discord Rich Presence отвечает за отображение «активности» пользователя ExosWare в профиле Discord: имя, роль из лицензионного профиля и две рекламные кнопки клиента. Реализация состоит из двух частей:

- пакет `ru.levin.com.discord` — рукописная JNA-обвязка над нативной библиотекой `discord-rpc` (C-ABI классической библиотеки [discord-rpc](https://github.com/discord/discord-rpc));
- модуль `ru.levin.modules.misc.DiscordRCP` — потребитель этой обвязки, регистрируемый в каталоге модулей категории `Type.Misc`.

Вся подсистема чисто косметическая: она не содержит анти-чит / анти-детект логики и лишь рекламирует ExosWare через Rich Presence.

## Архитектура

```
EventUpdate
    |
    v
DiscordRCP (модуль, Type.Misc)
    |  rpc = DiscordRPC.INSTANCE
    |  presence = new DiscordRichPresence()
    v
DiscordRPC (JNA Library)  --->  нативная библиотека "discord-rpc"
    ^                              (win32-x86-64/discord-rpc.dll,
    |                               linux-x86-64/libdiscord-rpc.so,
DiscordEventHandlers,               darwin/libdiscord-rpc.dylib)
DiscordRichPresence,
DiscordUser (JNA Structure)
```

## Нативная библиотека

Обвязка загружается через JNA: `Native.load("discord-rpc", DiscordRPC.class)`. Имя `discord-rpc` означает, что в classpath / на пути загрузки JNA должна присутствовать соответствующая платформенная библиотека. Они поставляются в ресурсах сборки по стандартному раскладу JNA:

| Платформа | Файл | Каталог в ресурсах |
| --- | --- | --- |
| Windows x64 | `discord-rpc.dll` | `win32-x86-64/` |
| Linux x64 | `libdiscord-rpc.so` | `linux-x86-64/` |
| macOS | `libdiscord-rpc.dylib` | `darwin/` |

Бинарники только x86-64 (и `.dylib` для macOS); ARM/aarch64 не покрыты. Это легитимная библиотека Discord RPC, а не нативный модуль защиты.

## API: пакет `ru.levin.com.discord`

Это рукописный «клон» стандартной JNA-обёртки discord-rpc, обрезанный до полей, которые нужны ExosWare. Имена полей структур (например `button_label_1`, `partyPrivacy`, `matchSecret`) совпадают с апстримом.

### `DiscordRPC` — интерфейс библиотеки

`interface DiscordRPC extends com.sun.jna.Library`. Синглтон-хэндл: `DiscordRPC INSTANCE = Native.load("discord-rpc", DiscordRPC.class)`. Объявляет четыре нативные функции (отображение 1:1 на C-API):

| Метод | Сигнатура C-API | Назначение |
| --- | --- | --- |
| `Discord_Initialize` | `(String appId, DiscordEventHandlers handlers, boolean autoRegister, String steamId)` | Подключение к Discord IPC для заданного `appId`. |
| `Discord_UpdatePresence` | `(DiscordRichPresence p)` | Отправка структуры присутствия в Discord. |
| `Discord_RunCallbacks` | `()` | Прокачка очереди нативных колбэков (вызвало бы методы `DiscordEventHandlers`). |
| `Discord_Shutdown` | `()` | Закрытие RPC-соединения. |

### `DiscordRichPresence` — структура присутствия

`class DiscordRichPresence extends com.sun.jna.Structure`. Конструктор задаёт `setStringEncoding("UTF-8")` — это нужно для кириллических подписей кнопок («Купить», «Телеграмм»).

Важная деталь: поля объявлены в произвольном порядке, но фактический разметку памяти под нативный ABI задаёт `getFieldOrder()`. Этот список — load-bearing: его порядок должен точно соответствовать нативной структуре. Менять объявления полей безопасно, менять список `getFieldOrder()` — нет.

Порядок полей по `getFieldOrder()`:

```
state, details, startTimestamp, endTimestamp,
largeImageKey, largeImageText, smallImageKey, smallImageText,
partyId, partySize, partyMax, partyPrivacy,
matchSecret, joinSecret, spectateSecret,
button_label_1, button_url_1, button_label_2, button_url_2,
instance
```

| Поле | Тип | Используется DiscordRCP |
| --- | --- | --- |
| `state` | `String` | да — `"Role: <role>"` |
| `details` | `String` | да — `"User: <name>"` |
| `startTimestamp` | `long` | да — epoch-секунды |
| `endTimestamp` | `long` | нет |
| `largeImageKey` | `String` | да — URL гифки |
| `largeImageText` | `String` | да — `https://t.me/exosware` |
| `smallImageKey` / `smallImageText` | `String` | нет |
| `partyId` / `partySize` / `partyMax` / `partyPrivacy` | `String`/`int` | нет |
| `matchSecret` / `joinSecret` / `spectateSecret` | `String` | нет |
| `button_label_1` / `button_url_1` | `String` | да — «Купить» / `https://exosware.ru` |
| `button_label_2` / `button_url_2` | `String` | да — «Телеграмм» / `https://t.me/exosware` |
| `instance` | `int` | нет |

### `DiscordEventHandlers` — указатели на колбэки

`class DiscordEventHandlers extends com.sun.jna.Structure`. Содержит шесть указателей на функции-колбэки. Порядок полей пинится `getFieldOrder()`: `ready, disconnected, errored, joinGame, spectateGame, joinRequest`.

Все колбэки — интерфейсы `com.sun.jna.Callback` из подпакета `ru.levin.com.discord.callbacks`:

| Колбэк | Метод | Назначение |
| --- | --- | --- |
| `ReadyCallback` | `apply(DiscordUser)` | RPC-соединение готово. |
| `DisconnectedCallback` | `apply(int errorCode, String message)` | Отключение. |
| `ErroredCallback` | `apply(int errorCode, String message)` | Ошибка. |
| `JoinGameCallback` | `apply(String joinSecret)` | Запрос на join. |
| `SpectateGameCallback` | `apply(String spectateSecret)` | Запрос на spectate. |
| `JoinRequestCallback` | `apply(DiscordUser requester)` | Запрос пользователя на присоединение. |

### `DiscordUser`

`class DiscordUser extends com.sun.jna.Structure`, передаётся в `ReadyCallback` и `JoinRequestCallback`. Поля (`getFieldOrder()`): `userId`, `username`, `discriminator`, `avatar`.

### `RPCButton` (helper)

`ru.levin.com.discord.helpers.RPCButton` — `Serializable`-объект, хранящий `label` + `url`. Фабрика `create(label, url)` обрезает подпись до 31 символа (`Math.min(label.length(), 31)`) — лимит подписи кнопки Discord:

```java
public static RPCButton create(String substring, final String s) {
    substring = substring.substring(0, Math.min(substring.length(), 31));
    return new RPCButton(substring, s);
}
```

Важно: `RPCButton` **не используется** модулем `DiscordRCP`. Модуль пишет `button_label_1/url_1/label_2/url_2` напрямую в структуру, поэтому ограничение в 31 символ в реальном пути не применяется. Это фактически мёртвый код.

## Интеграция: модуль `DiscordRCP`

```java
@FunctionAnnotation(name = "DiscordRPC", desc = "Активность в дискорде", type = Type.Misc)
public class DiscordRCP extends Function { ... }
```

Модуль держит:

- `rpc = DiscordRPC.INSTANCE` — нативный хэндл;
- `presence = new DiscordRichPresence()` — переиспользуемая структура присутствия;
- `volatile boolean started` — защита от повторной инициализации;
- `Thread thread` — фоновый демон-поток обновления.

### Запуск

`onEvent(Event)` реагирует только на `EventUpdate` и вызывает `startRpc()`. Метод идемпотентен: `synchronized` + проверка `volatile started`, поэтому инициализация выполняется один раз, несмотря на то что `EventUpdate` приходит каждый тик.

`startRpc()`:

1. создаёт **пустой** `DiscordEventHandlers` (ни один указатель колбэка не присваивается — см. ниже);
2. `Discord_Initialize("1384873696375603281", handlers, true, "")` — фиксированный app id, `autoRegister=true`, пустой `steamId`;
3. задаёт `presence.startTimestamp = System.currentTimeMillis() / 1000L` (epoch-секунды) и `largeImageText = "https://t.me/exosware"`;
4. вызывает `updatePresenceFields()` и `Discord_UpdatePresence(presence)`;
5. запускает демон-поток `"TH-RPC-Handler"`.

### Поток обновления

Поток `TH-RPC-Handler` в цикле каждые `2000` мс вызывает:

```java
rpc.Discord_RunCallbacks();
updatePresenceFields();
rpc.Discord_UpdatePresence(presence);
Thread.sleep(2000L);
```

Цикл работает до `Thread.interrupt()`; `InterruptedException` молча проглатывается.

### Заполнение присутствия

`updatePresenceFields()` подтягивает живые значения из лицензионного профиля и задаёт рекламные поля:

```java
presence.details = "User: " + Manager.USER_PROFILE.getName();
presence.state   = "Role: " + Manager.USER_PROFILE.getRole();

presence.button_label_1 = "Купить";      presence.button_url_1 = "https://exosware.ru";
presence.button_label_2 = "Телеграмм";   presence.button_url_2 = "https://t.me/exosware";

presence.largeImageKey  = "https://api.exosware.ru/api/loader/discord.gif";
```

Это связывает подсистему с профилем `Manager.USER_PROFILE` (подсистема auth/profile) — каждое обновление перечитывает `getName()` / `getRole()`.

### Остановка

`onDisable()` сбрасывает `started`, прерывает поток (`thread.interrupt()`, если жив) и вызывает `rpc.Discord_Shutdown()`. После этого следующий `EventUpdate` способен снова запустить RPC.

## Особенности и подводные камни

- **Захардкоженный Discord application id** `1384873696375603281` в `Discord_Initialize`.
- **Колбэки не подключены.** `DiscordEventHandlers` создаётся пустым (все указатели `null`), поэтому `Discord_RunCallbacks()` ничего полезного не делает, а все шесть интерфейсов колбэков и `DiscordUser` в рамках этого модуля — мёртвый код.
- **`RPCButton` не используется** — модуль пишет поля кнопок прямо в структуру, обход 31-символьного ограничения подписи.
- **Image keys — это полные URL, а не зарегистрированные asset key Discord:** `largeImageKey = "https://api.exosware.ru/api/loader/discord.gif"`, `largeImageText = "https://t.me/exosware"`. Опирается на поддержку Discord URL-как-asset.
- **Магические числа:** интервал опроса `Thread.sleep(2000L)`; `startTimestamp = System.currentTimeMillis() / 1000L` (секунды); `RPCButton` лимит 31 символ.
- **UTF-8 в структуре** (`setStringEncoding("UTF-8")`) — необходимо для кириллических подписей кнопок.
- **Поток-демон** `"TH-RPC-Handler"`; на disable прерывается и вызывается `Discord_Shutdown`, но при следующем `EventUpdate` может перезапуститься.
- **Лишний импорт.** `DiscordRCP` импортирует `EventRender2D`, но не использует его.
- **`getFieldOrder()` — критичен.** Для `DiscordRichPresence`, `DiscordEventHandlers` и `DiscordUser` именно этот список задаёт нативную раскладку, и он должен точно совпадать с C-ABI.
