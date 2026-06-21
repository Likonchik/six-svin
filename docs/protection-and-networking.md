# Защита, лицензирование и сеть

Этот документ описывает слой «защиты»/лицензирования клиента ExosWare (Fabric 1.21.4, группа `ru.levin`, archives name `exosware`), фактический механизм аутентификации, все обнаруженные бэкенд-адреса и эндпоинты, прокси-стек (встроенные netty-зависимости для SOCKS), а также то, какие пользовательские данные и куда передаются. Изложение фактическое и ориентированное на безопасность: ниже явно отмечены захардкоженные секреты, слабая криптография, заглушки и места, где «защита» фактически ничего не проверяет.

> ВНИМАНИЕ (краткое резюме для безопасности). В текущем исходном коде слой `ru.levin.protect` **не выполняет никакой реальной проверки лицензии**. Профиль пользователя захардкожен, AES использует опубликованный тестовый ключ FIPS в небезопасном режиме ECB, а единственная «нативная» загрузка профиля (`NativeProfile`) — это чистый Java-класс, читающий системные свойства JVM, и он не используется в текущем потоке выполнения. Реальные сетевые взаимодействия (IRC, ClientAPI) аутентифицируют пользователя только перед собственными бэкендами ExosWare и используют захардкоженные общие секреты, передаваемые открытым текстом по TCP.

---

## 1. Пакет `ru.levin.protect`

Пакет содержит четыре класса. Несмотря на название (`protect`, `NativeHelper`, `loader.NativeProfile`), ни один из них не содержит нативного кода и не обращается к серверу лицензий.

### 1.1 Точка входа и порядок инициализации

Защита инициализируется самой первой, ещё до построения остальных менеджеров.

```
ExosWare.onInitialize()
  -> setupProtection()        // ExosWare.java:60
       -> NativeHelper.setProfile()   // NativeHelper.java:8
```

`ExosWare.setupProtection()` вызывает только `NativeHelper.setProfile()`. Это единственная точка «лицензирования» во всём пути запуска.

### 1.2 `NativeHelper` — заглушка профиля

`NativeHelper.setProfile()` (`src/main/java/ru/levin/protect/NativeHelper.java`) **захардкоживает** профиль пользователя в глобальное статическое поле `Manager.USER_PROFILE`:

```java
public static void setProfile() {
    Manager.USER_PROFILE = new UserProfile(
            "levin1337",
            "Deleoper",   // sic — опечатка от "Developer"
            "09.11.2025"  // дата окончания подписки (только для отображения)
    );
}
```

| Поле | Значение | Назначение |
| --- | --- | --- |
| `name` | `levin1337` | имя/ник лицензированного пользователя |
| `role` | `Deleoper` | роль (с опечаткой), отображается в HUD/RPC |
| `expiry` | `09.11.2025` | дата окончания подписки |

Замечания по безопасности:
- Любой, кто запускает эту сборку, автоматически «авторизован» как `levin1337` с ролью `Deleoper`.
- Дата `expiry` **нигде не сверяется** с текущей датой — нет ни одного места, где истечение подписки блокировало бы запуск или функции. Дата используется исключительно косметически (см. `ClickGUI.renderExpiryText`, окно заголовка, HUD-watermark, Discord RPC).
- `NativeHelper` импортирует `ru.levin.protect.loader.NativeProfile`, но **никогда его не использует** — мёртвый импорт.

### 1.3 `UserProfile` — модель идентичности

`UserProfile` (`src/main/java/ru/levin/protect/UserProfile.java`) — неизменяемый POJO с тремя полями (`name`, `role`, `expiry`) и геттерами `getName()` / `getRole()` / `getExpiry()`. Хранится в статическом `Manager.USER_PROFILE` и читается по всему клиенту:

| Потребитель | Что читает |
| --- | --- |
| `MixinMinecraftClient` (заголовок окна) | `getName()` |
| `HUD` (watermark) | `getName()` |
| `ClickGUI.renderExpiryText` | `getExpiry()` |
| `DiscordRCP` (Rich Presence) | `getName()`, `getRole()` |
| `Config` (поле автора конфига) | `getName()` |
| `IrcManager.connect(...)` (ник для рукопожатия) | `getName()` |

Таким образом `Manager.USER_PROFILE` — центральный узел связности идентичности: записывается `NativeHelper`, читается множеством подсистем.

### 1.4 `NativeProfile` — псевдо-«нативный» загрузчик (мёртвый код)

`NativeProfile` (`src/main/java/ru/levin/protect/loader/NativeProfile.java`) аннотирован `@SuppressWarnings("All")` и расположен в пакете `loader`, что подразумевает JNI/нативную загрузку лицензии. Фактически это **чистый Java-класс**, который в статическом инициализаторе читает системные свойства JVM:

```java
static {
    username   = System.getProperty("levin.username", "N/A");
    role       = System.getProperty("levin.role",     "Unknown role");
    expiryDate = System.getProperty("levin.expiry",   "Unknown date!");
}
```

То есть идентичность могла бы инжектироваться извне через `-Dlevin.username=...`, `-Dlevin.role=...`, `-Dlevin.expiry=...`. Однако в текущей сборке класс **импортируется, но никогда не инстанцируется** (`Manager.USER_PROFILE` имеет тип `UserProfile`, а не `NativeProfile`), поэтому этот путь не задействован. Это говорит о намеченном, но не подключённом внешнем загрузчике лицензии.

### 1.5 `AES` — обфускация (не защита)

`AES` (`src/main/java/ru/levin/protect/AES.java`) — статический помощник `encrypt`/`decrypt` с Base64-обёрткой.

```java
private static final String secretKey = "2B7E151628AED2A6ABF7158809CF4F3C"; // 32 ASCII-символа
private static final String algorithm = "AES";
...
Cipher cipher = Cipher.getInstance("AES"); // => AES/ECB/PKCS5Padding (по умолчанию JCE)
```

Критические замечания по криптографии:
- Ключ `2B7E151628AED2A6ABF7158809CF4F3C` — это **канонический тестовый ключ из FIPS-197** (эталон AES). Это 32 ASCII-символа = 32 байта ключа (AES-256).
- `Cipher.getInstance("AES")` без указания режима даёт **ECB + PKCS5Padding без IV** — детерминированный и криптографически слабый режим.
- Ключ и режим публичны в байткоде, поэтому любое «зашифрованное» значение тривиально расшифровывается.
- Единственное применение AES — обфускация строки вебхука в `UrlManager` (см. ниже). Это **обфускация, а не защита**.

---

## 2. Бэкенд-адреса и эндпоинты

В коде встречаются несколько хостов. Часть из них — реальные, часть — явно некорректные/плейсхолдерные (вероятно, подставляются при релизной сборке).

| Где определён | Хост / URL | Порт | Назначение | Состояние |
| --- | --- | --- | --- | --- |
| `UrlManager.host` | `185.244.172.20` | — | поле-константа бэкенда (российский диапазон) | реально выглядящий IP; **в `webhookIdea()` не используется** |
| `UrlManager.webhookIdea()` | AES-шифртекст → Discord-вебхук | — | обфусцированный URL вебхука | расшифровывается локально; **нет вызывающего кода** |
| `IrcManager` | `11.1.1` | `3025` | внутриклиентский чат/аутентификация | **некорректный хост** (3 октета) — соединение всегда падает |
| `ClientAPI` (через `Globals`) | `1.4.3` | `13599` | распознавание других пользователей клиента | **некорректный IP** — фактически инертно |
| `DiscordRCP` | `https://exosware.ru` | — | кнопка «Купить» в Rich Presence | реальный домен |
| `DiscordRCP` | `https://t.me/exosware` | — | кнопка «Телеграмм» / `largeImageText` | реальный домен |
| `DiscordRCP` | `https://api.exosware.ru/api/loader/discord.gif` | — | картинка `largeImageKey` в RPC | реальный домен API |

> Плейсхолдерные хосты (`11.1.1`, `1.4.3`) явно неполные/невалидные. Реальные эндпоинты IRC и ClientAPI, по-видимому, подставляются при сборке релиза. Реальный бэкенд-IP `185.244.172.20` и домен `api.exosware.ru` — наиболее вероятные настоящие адреса инфраструктуры.

### 2.1 `UrlManager` (вебхук)

`UrlManager` (`src/main/java/ru/levin/manager/UrlManager.java`):

```java
public final String host = "185.244.172.20";
public final String webhookIdea() {
    String hook = "Pu11pRLGmzQ/jHzBizY9N0WIl4lkbqWLI06mVwVTlWgbqfxRTdM3R15W..."; // AES-шифртекст
    try {
        return AES.decrypt(hook);
    } catch (Exception e) { }
    return hook; // при ошибке молча возвращает сам шифртекст
}
```

Особенности:
- `webhookIdea()` AES-расшифровывает захардкоженный Base64-блоб в URL Discord-вебхука. Имя `Idea` и пустой `catch` маскируют назначение (эксфильтрация в Discord).
- `UrlManager` **не зарегистрирован** в `Manager` (нет поля `Manager.URL_MANAGER`), и `webhookIdea()` **не имеет вызывающего кода** в Java-исходниках. В текущей сборке это фактически мёртвый/неподключённый код.
- При исключении расшифровки метод молча возвращает шифртекст вместо URL — то есть отправил бы «мусор», а не ошибку.

---

## 3. Как «работает» аутентификация/лицензирование

Сводно: **реального лицензирования на старте нет.** Идентичность утверждается на стороне клиента (`NativeHelper.setProfile`), а сетевые рукопожатия лишь аутентифицируют пользователя перед собственными чат/peer-сервисами ExosWare.

Поток данных при запуске:

```
onInitialize -> setupProtection -> NativeHelper.setProfile()
                                      -> Manager.USER_PROFILE = {levin1337, Deleoper, 09.11.2025}
...
ExosWare.init() -> IRC_MANAGER.connect(USER_PROFILE.getName())   // ник = "levin1337"
              -> заголовок окна / HUD / RPC / автор конфига читают USER_PROFILE
```

Ни один поток не проверяет дату окончания подписки и не обращается к серверу лицензий для авторизации запуска.

### 3.1 IRC: challenge-response (HMAC-SHA256)

`IrcManager` (`src/main/java/ru/levin/manager/ircManager/IrcManager.java`) — внутриклиентский «IRC»-чат поверх сырого TCP, единственное место с настоящей сетевой аутентификацией пользователя.

- Хост/порт: `InetSocketAddress("11.1.1", 3025)` (хост некорректен → постоянные ретраи).
- Константы: `cheatName = "ExosWare"`, `secretKey = "levinAntiKotopishka"` (захардкожен открытым текстом).
- Таймауты: `CONNECT_TIMEOUT_MS = 5000`, `RETRY_DELAY_MS = 15000` (реконнект каждые 15 c).
- Рукопожатие:
  1. Сервер присылает строку-nonce.
  2. Клиент вычисляет `hmac = HmacSHA256(secretKey, nonce + nickname)`.
  3. Клиент отправляет `ExosWare:<nickname>:<hmac>\n`.
- Потоки: три демон-исполнителя `IRC-Reader` / `IRC-Writer` / `IRC-Scheduler`, очередь `messageQueue` (`LinkedBlockingQueue`), история `MESSAGES` (`CopyOnWriteArrayList`, ограничена 200 сообщениями).
- Доп. функции: gradient-рендер строк чата (`0x808080` → `0xFFFFFF`), список игнора ников (`ignoredNicks`).
- Запускается из `ExosWare.init()` через `IRC_MANAGER.connect(Manager.USER_PROFILE.getName())` — то есть ник аутентификации связан с захардкоженным профилем.

Замечания по безопасности: трафик не шифруется (plaintext TCP), `secretKey` извлекается из jar, HMAC передаётся в открытом виде и воспроизводим (replayable).

### 3.2 ClientAPI: распознавание «свой/чужой» (SHA-256)

`ClientAPI` (`src/main/java/ru/levin/manager/apiManager/ClientAPI.java`) — TCP-клиент сервиса «является ли этот ник пользователем клиента».

- `SECRET_KEY = "k0tikBolomotik"` (захардкожен).
- Асинхронность: `ExecutorService` (cached thread pool) + кэш результатов `ConcurrentHashMap<String, Boolean>` по нику.
- Протокол (по одной строке на короткое TCP-соединение):
  - `check:<sha256(SECRET_KEY + nick)>:<nick>\n` → ответ `true`/`false`.
  - `add:<sha256(SECRET_KEY + nick)>:<nick>\n` (fire-and-forget).
  - `remove:<sha256(SECRET_KEY + nick)>:<nick>\n` (fire-and-forget).
- Подпись запроса — `SHA-256(SECRET_KEY + nick)`, hex-строка.

Потребитель — модуль `Globals` (`ru.levin.modules.misc.Globals`):
- Создаёт `new ClientAPI("1.4.3", 13599)` — **некорректный хост `1.4.3`**, поэтому сокет фактически не подключается, и проверка инертна в этой сборке. (Настоящий бэкенд-IP `185.244.172.20` живёт отдельно в `UrlManager`.)
- При `onEnable` отправляет `addPlayer(<своё имя>)`; при `onDisable` — `removePlayer(...)`.
- Каждые 30 тиков (`mc.player.age % 30 == 0`) опрашивает всех игроков из `Manager.SYNC_MANAGER.getPlayers()` через `isClientUserAsync(name, ...)` и заполняет `isClientUserCache` (UUID → Boolean). Этот кэш используется ESP/неймтегами для пометки «пользователей клиента».

### 3.3 Роль `SyncManager` в потоке данных

`SyncManager` (`ru.levin.manager.SyncManager`) сам по себе не относится к защите, но является источником данных для `Globals`: он кэширует снимки игроков/сущностей мира (`getPlayers()`, `getEntities()` — `ImmutableList`-копии), которые `Globals` перебирает для опроса `ClientAPI`. То есть имена игроков с сервера передаются (в плейсхолдерной сборке — на `1.4.3:13599`) для построения карты «свой/чужой».

---

## 4. Прокси-стек (netty SOCKS)

Клиент умеет туннелировать своё соединение через SOCKS4/SOCKS5-прокси. Для этого в jar встроены (`include(...)`) две netty-зависимости.

### 4.1 Встроенные зависимости (`build.gradle`)

```gradle
include(modImplementation('io.netty:netty-handler-proxy:4.1.82.Final'))
include(modImplementation('io.netty:netty-codec-socks:4.1.82.Final'))
```

| Зависимость | Версия | Роль |
| --- | --- | --- |
| `io.netty:netty-handler-proxy` | `4.1.82.Final` | обработчики прокси (`Socks4ProxyHandler`, `Socks5ProxyHandler`) |
| `io.netty:netty-codec-socks` | `4.1.82.Final` | кодек протокола SOCKS |

Обе ремаппятся Loom и шейдятся в финальный jar, так что прокси-поддержка не требует внешних библиотек.

### 4.2 `ProxyManager` и модель `Proxy`

`ProxyManager` (`src/main/java/ru/levin/manager/proxyManager/ProxyManager.java`):
- Хранит конфигурацию в `<runDirectory>/files/proxy.ew` (JSON через Gson + Apache `commons-io` `FileUtils`).
- Формат: `{ "proxy-enabled": <bool>, "accounts": { "<имя>": <Proxy> } }`. Прокси по умолчанию хранится под пустым ключом `""` (`setDefaultProxy`).
- Статические поля: `proxyEnabled`, `proxy`, `lastUsedProxy`, `proxyMenuButton`.
- `getLastUsedProxyIp()` возвращает `"none"`, если IP не задан.

Модель `Proxy` (`src/main/java/ru/levin/manager/proxyManager/Proxy.java`):

| Поле | Сериализация | По умолчанию |
| --- | --- | --- |
| `ipPort` | `@SerializedName("IP:PORT")` | `""` |
| `type` | `ProxyType` (`SOCKS4`/`SOCKS5`) | `SOCKS5` |
| `username` | — | `""` |
| `password` | — | `""` |

`getIp()` и `getPort()` разбивают `ipPort` по `:`. **Валидации нет** — `getPort()` бросит `NumberFormatException` при некорректной строке; корректность ввода проверяется только в редакторе `GuiProxy.isValidIpPort` (порт 0..65535). Повреждённый `files/proxy.ew` может уронить путь подключения.

### 4.3 Инжекция в netty-pipeline (`MixinClientConnectionInitMixin`)

`MixinClientConnectionInitMixin` (`src/main/java/ru/levin/mixin/player/MixinClientConnectionInitMixin.java`) перехватывает `net.minecraft.network.ClientConnection$1#initChannel` в `@At("HEAD")` и при `proxyEnabled` добавляет обработчик в начало pipeline:

- `SOCKS5`: `Socks5ProxyHandler(addr, username|null, password|null)` (логин/пароль опциональны).
- `SOCKS4`: `Socks4ProxyHandler(addr, username|null)` (**пароль игнорируется**).
- Запоминает `lastUsedProxy` и обновляет подпись кнопки `proxyMenuButton` → `"Proxy: <ip>"`.

Замечание: в конце метода безусловно вызывается `proxyManager.proxyMenuButton.setMessage(...)` — это даст **NPE**, если кнопка ещё не создана (т.е. экран мультиплеера ещё не открыт). Кнопка `proxyMenuButton` создаётся на экранах списка серверов (`MultiplayerScreenMixin`/`MultiplayerScreen`), редактор — `GuiProxy`.

### 4.4 Что передаётся через прокси

Через SOCKS-прокси туннелируется **всё игровое TCP-соединение** к Minecraft-серверу (через `ClientConnection`), включая логин/мультиплеер-трафик. Это используется для сокрытия реального IP при заходе на сервер. Учётные данные прокси (логин/пароль для SOCKS5) хранятся **в открытом виде** в `files/proxy.ew`.

---

## 5. Куда и какие данные пользователя уходят

| Данные | Источник | Назначение | Транспорт | Примечания по безопасности |
| --- | --- | --- | --- | --- |
| Имя/роль/expiry пользователя | `Manager.USER_PROFILE` (захардкожено) | Discord Rich Presence (`User: <name>`, `Role: <role>`) | Discord IPC через нативную `discord-rpc` (JNA) | косметика; имя/роль видны в Discord |
| Ник пользователя | `USER_PROFILE.getName()` | IRC-сервер (`11.1.1:3025`, плейсхолдер) | plaintext TCP + HMAC-SHA256 | `secretKey` захардкожен; replay-уязвимо |
| Сообщения чата IRC | ввод пользователя | IRC-сервер | plaintext TCP | без шифрования; история 200 строк локально |
| Своё имя игрока | `mc.player.getGameProfile().getName()` | ClientAPI (`1.4.3:13599`, плейсхолдер) — `add`/`remove` | plaintext TCP + SHA-256 подпись | `SECRET_KEY` захардкожен; в этой сборке инертно |
| Имена окружающих игроков | `SYNC_MANAGER.getPlayers()` | ClientAPI — `check` (опрос «свой/чужой») | plaintext TCP + SHA-256 подпись | каждые 30 тиков; для ESP-пометки |
| Весь игровой трафик к MC-серверу | `ClientConnection` | пользовательский SOCKS4/5-прокси | netty SOCKS | сокрытие IP; креды прокси хранятся открыто |
| (мёртвый код) обфусцированный URL вебхука | `UrlManager.webhookIdea()` | Discord-вебхук (AES-блоб) | — | без вызывающего кода в этой сборке |

Дополнительно по идентичности/спуфингу (смежно с защитой, но за пределами `protect/`):
- `AccountManager` выполняет **офлайн/«пиратский» вход**: `ClientManager.loginAccount(name)` создаёт `Session` с `token = "invalid_token"` и `UUID = nameUUIDFromBytes("OfflinePlayer:" + name)` через `MinecraftClientAccessor.setSession` — это спуфинг альта, а не реальная аутентификация Mojang/Microsoft. Последний выбранный альт автологинится из `files/lastAlt.ew` при старте.
- `MixinTextVisitFactory` (NameProtect) переписывает реальный ник в отображаемом тексте — анти-скриншот/анти-стример.
- Сокрытие папки и kill-switch: `ExosWare.keyPress` скрывает `C:\ExosWare` через `DosFileAttributeView` (UnHook/legitMode); при `legitMode` подмена скинов и парсинг команд подавляются. Это маскировка клиента, а не лицензирование.

---

## 6. Захардкоженные секреты и магические значения (сводка)

| Секрет / значение | Где | Назначение |
| --- | --- | --- |
| AES-ключ `2B7E151628AED2A6ABF7158809CF4F3C` | `protect/AES.java` | обфускация вебхука (тестовый ключ FIPS-197, ECB, без IV) |
| IRC `secretKey = "levinAntiKotopishka"` | `IrcManager.java` | HMAC-SHA256 рукопожатие |
| ClientAPI `SECRET_KEY = "k0tikBolomotik"` | `ClientAPI.java` | SHA-256 подпись запросов |
| Профиль `levin1337` / `Deleoper` / `09.11.2025` | `NativeHelper.java` | захардкоженная «лицензия» |
| Discord app id `1384873696375603281` | `DiscordRCP.java` | приложение Rich Presence |
| IRC порт `3025`, таймаут `5000` мс, ретрай `15000` мс | `IrcManager.java` | параметры соединения |
| ClientAPI порт `13599`, опрос каждые `30` тиков | `Globals.java` | параметры peer-проверки |
| Бэкенд-IP `185.244.172.20` | `UrlManager.java` | константа хоста (не используется в `webhookIdea`) |
| Домен `api.exosware.ru`, `exosware.ru`, `t.me/exosware` | `DiscordRCP.java` | RPC-картинка/кнопки |

Все секреты тривиально извлекаются из jar; вся сетевая I/O работает на демон-потоках и «падает молча» (пустые `catch`), поэтому сбои не доходят до пользователя.

---

## 7. Итоговая оценка

- «Защита» (`ru.levin.protect`) **не принуждает к лицензии**: профиль захардкожен, истечение не проверяется, «нативный» загрузчик — мёртвый Java-код.
- Криптография слабая и/или только для обфускации (AES-ECB на публичном тестовом ключе).
- Настоящая сетевая аутентификация существует только перед собственными бэкендами ExosWare (IRC, ClientAPI) и использует захардкоженные общие секреты по нешифрованному TCP.
- Бэкенд-хосты IRC (`11.1.1`) и ClientAPI (`1.4.3`) — плейсхолдеры; реальные адреса предположительно подставляются при релизе. Настоящая инфраструктура — `185.244.172.20` и `api.exosware.ru`.
- Прокси-стек (netty SOCKS4/5) функционален и туннелирует весь игровой трафик; креды прокси хранятся открыто в `files/proxy.ew`.
