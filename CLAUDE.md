# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Что это

**OneTap** (бывш. ExosWare) — клиентский мод-чит для **Minecraft 1.21.1** на загрузчике **NeoForge**. Это обычный NeoForge-мод (единственная точка входа `ru.levin.ExosWare`, помеченная `@Mod("onetap")`), который через слой миксинов вклинивается в движок и накладывает собственную систему модулей, GUI/HUD и сетевых сервисов. Только клиентская сторона (`side = "CLIENT"`).

> **Состояние:** проект уже портирован с Fabric 1.21.4 → NeoForge 1.21.1 и собирается успешно. Kotlin полностью удалён (0 `.kt`-файлов, ~315 `.java`).

### Легаси-нейминг (важно при поиске)
Ребрендинг ExosWare → OneTap прошёл лишь частично. В коде сосуществуют оба имени:
- `modId = "onetap"`, displayName `OneTap`, jar `onetap-1.0.0.jar`, `mod_version = 1.0.0`.
- Java-пакет — по-прежнему `ru.levin`, класс точки входа — `ru.levin.ExosWare`.
- Namespace ассетов и mixin-конфигов — `exosware` (`assets/exosware/`, `exosware.mixins.json`, refmap `exosware-refmap.json`).
- Anti-detection: UnHook прячет папку `C:\OneTap`, но часть документации/строк всё ещё упоминает `C:\ExosWare`.

При переименовании символов НЕ делай слепой замены `World`/`Item`/`Block` — у мода есть собственный класс `ru.levin.modules.render.World`.

## Команды

```bash
./gradlew build         # сборка → build/libs/onetap-1.0.0.jar (+ -sources.jar)
./gradlew runClient     # запуск dev-клиента NeoForge 1.21.1 (gameDirectory = run/)
./gradlew compileJava   # быстрая проверка компиляции
```

- Требуется **JDK 21** (`toolchain.languageVersion = 21`, `release = 21`).
- На Windows используется `run.bat` (ставит `GRADLE_USER_HOME=D:\gradle_home`, запускает `runClient --no-daemon`).
- **Тестов в проекте нет** — это Minecraft-мод; верификация выполняется запуском `runClient` и ручным smoke-test'ом по категориям модулей. `defaultRequire=1` в `exosware.mixins.json` делает несошедшиеся инъекции миксинов громкими ошибками старта — это основная «тест-сеть».

## Сборка и зависимости (ModDevGradle)

- Плагин `net.neoforged.moddev` (не Fabric Loom). Версии в `gradle.properties`: `minecraft_version=1.21.1`, `neoforge_version=21.1.233`. Маппинги — официальные **Mojang (Mojmap)**.
- **netty SOCKS/proxy** (`netty-handler-proxy`, `netty-codec-socks` `4.1.82.Final`) — на compile-classpath через `implementation` и вкладываются в jar через **`jarJar`** с `transitive=false` (MC уже несёт ядро netty 4.1.x). Пин на 4.1.82 — намеренный.
- **TACZ** (Timeless and Classics Zero, `libs/tacz-…jar`) — `compileOnly` + `runtimeOnly`, **runtime-опциональная** зависимость. Gun-ESP в NameTags деградирует через `try/catch(NoClassDefFoundError)`, если TACZ не установлен. Соответствующие миксины — в `exosware.tacz.mixins.json` (`required=false`, `defaultRequire=0`).
- **Discord-RPC нативы** (`win32-x86-64/`, `linux-x86-64/`, `darwin/` в resources) грузятся JNA из classpath по пути `{os-prefix}/discord-rpc.{ext}` — это ресурсы в корне jar, **НЕ jarJar**.
- `processResources` подставляет `${mod_version}` и т.п. в `META-INF/neoforge.mods.toml`.

## Архитектура

Полная документация — в `docs/` (`architecture.md`, `module-system.md`, `modules-catalog.md`, `event-system.md`, `mixins.md`, `gui-and-screens.md`, `managers-and-commands.md`, и таблицы порта `migration-*.md`). Ниже — то, что нужно понять до чтения файлов.

### Триада Function + Event + Mixin
Поведение клиента строится на трёх слоях:

1. **Function (модуль)** — каждая фича-чит это подкласс `ru.levin.modules.Function` с `@FunctionAnnotation(name, desc, key, type, keywords)` (метаданные читаются рефлексией). Все ~90 модулей создаются в одном конструкторе `FunctionManager` и лежат в статическом `CopyOnWriteArrayList`. Категория — enum `Type` (Combat/Move/Render/Player/Misc). Модуль объявляет настройки (подтипы `Setting`) и реализует один метод `onEvent(Event)`.
2. **Event (шина)** — `ru.levin.events.Event`, диспетчер — статический `Event.call(event)`: синхронный broadcast без регистрации слушателей. Каждый включённый модуль получает каждое событие и сам фильтрует через `instanceof`. `SYNC_MANAGER` получает событие **последним** (после того как модули записали повороты в `Manager.ROTATION` в рамках того же вызова).
3. **Mixin (хуки)** — ~62 миксина в `exosware.mixins.json` — это **производители** событий: конструируют `Event` в точке хука движка, вызывают `Event.call(...)`, затем читают (возможно изменённый) event обратно — применяют yaw/pitch (silent aim), отменяют ванильный вызов, подменяют пакет. Также напрямую читают флаги `Manager.FUNCTION_MANAGER.<module>.state` и `ClientManager.legitMode`.

События — мутабельные in/out-параметры. Типичный поток на тике: `EventUpdate` (модули выбирают цель, пишут поворот) → `EventMotion` (несёт исходящий move-пакет; модуль копирует ROTATION на пакет, миксин восстанавливает реальный поворот после отправки) → `EventRender2D/3D` (HUD/ESP).

### Service-locator `Manager`
Вместо DI — статический сервис-локатор `ru.levin.manager.Manager`: набор `public static`-полей, по одному на подсистему (~17 менеджеров). Доступ к `Minecraft` раздаётся через marker-интерфейс `IMinecraft` (`static final mc`), подмешиваемый наследованием. `Manager.ROTATION` — `final`-поле (`RotationController.get()`), не создаётся в bootstrap.

### Двухфазная инициализация
- **Фаза 1 — конструктор `ExosWare(IEventBus, ModContainer)`**: минимум — `setupProtection()` (`NativeHelper.setProfile()` → захардкоженный `Manager.USER_PROFILE`) и регистрация JVM shutdown-hook. Менеджеры здесь НЕ создаются.
- **Фаза 2 — `ExosWare.init()`**: отложенный bootstrap, вызывается из `MixinMinecraftClient` (конструктор, TAIL) — когда клиент и игрок уже существуют. Создаёт все менеджеры в **строго фиксированном порядке** (см. `init()`), всё обёрнуто в один `try/catch` с печатью stack trace — сбой посреди bootstrap оставит часть менеджеров `null`.
- **`shutDown()`** (shutdown-hook): сохраняет HUD-позиции, аккаунты, конфиг `autocfg`, останавливает IRC.

Рантайм-данные пишутся в `<gameDir>/files/` и `<gameDir>/files/modules/` (`.ew`/`.cfg`-файлы: alts, drag, friends, macros, themes, конфиги).

### legitMode — kill-switch / «паника»
`ClientManager.legitMode` (по умолчанию `false`) — глобальный анти-скриншер. Когда `true`: `Event.call()` коротко замыкается (модули не получают событий), `keyPress()` блокирует все тогглы/ClickGUI/макросы кроме клавиши возврата, многие миксины гейтят визуальные изменения на `!legitMode`. Модуль `UnHook` отключает функции (сохраняя в `functionsToBack`) и прячет папку `C:\OneTap` (Windows/NTFS). Логика возврата дублируется в `ExosWare.keyPress()` и `UnHook`.

### Карта пакетов `ru.levin`
- `manager/` — сервис-локатор `Manager`, `ClientManager`, `IMinecraft`, `SyncManager` + подпакеты-менеджеры (account/api/command/config/drag/font/friend/irc/macro/modules/notification/proxy/staff/theme).
- `modules/` — `Function`, `FunctionManager`, `FunctionAnnotation`, `Type`, `setting/` (7 подтипов `Setting`) и категории `combat/movement/render/player/misc`.
- `events/` — `Event` + `events.impl.*` (input/move/player/render/world).
- `mixin/` — хуки по пакетам `attack/chat/client/display/player/util/world` + `iface/` (accessor/invoker-интерфейсы) + `tacz/` (опциональные).
- `screens/` — `dropdown/` (ClickGUI), `altmanager/`, `mainmenu/`, `unhook/`.
- `protect/` — `AES`, `NativeHelper`, `UserProfile`, `loader/` (лицензирование — фактически заглушка, захардкоженный профиль).
- `com.discord/` — JNA-биндинг к нативной discord-rpc.
- `util/` — animations/color/math/move/player/render/shader/vector.

## Заметки по порту (если правишь рендер/version-delta)

Большинство «трудных» мест порта — это откат рефакторингов Minecraft 1.21.2 на 1.21.1 (см. `docs/migration-neoforge-1.21.1.md` и `migration-mappings.md`). Самые хрупкие зоны: core-shader реестр (через NeoForge `RegisterShadersEvent`, без `ShaderProgramKey/Defines`), render-state миксины (без `EntityRenderState`), FrameGraph/Fog миксины, подсистема кулдаунов (Item-keyed, не по группам/`Identifier`), и `@Local(ordinal=…)`/`@At`-цели. При правке таких мест сверяйся с декомпилированными исходниками 1.21.1 Mojmap, а не по памяти.

---

# Гайд разработчика (детально)

## Рецепт: как добавить новый модуль

1. Создай класс в `ru/levin/modules/<category>/Name.java`, унаследуй `Function`, навесь `@FunctionAnnotation`.
2. Объяви настройки полями, передай их в `addSettings(...)` в конструкторе.
3. Реализуй `onEvent(Event)` — фильтруй нужные события через `instanceof`.
4. Зарегистрируй экземпляр в `FunctionManager` (в `functions.addAll(...)`, в блоке своей категории). **Порядок в этом списке = порядок в UI И порядок обработки событий.** Если на модуль ссылаются другие классы/миксины — присвой его публичному полю: `name = new Name()`; иначе просто `new Name()`.
5. Импорты в `FunctionManager` — wildcard по категориям (`combat.*`/`movement.*`/`render.*`/`player.*`/`misc.*`), отдельный импорт не нужен.

```java
@FunctionAnnotation(name = "Example", keywords = {"Пример"}, desc = "...", type = Type.Misc)
public class Example extends Function {
    private final BooleanSetting flag = new BooleanSetting("Опция", true);
    public Example() { addSettings(flag); }
    @Override public void onEvent(Event event) {
        if (event instanceof EventUpdate) { if (mc.player == null) return; /* ... */ }
    }
    @Override protected void onEnable() { super.onEnable(); }
    @Override protected void onDisable() { super.onDisable(); }
}
```

- `mc` доступен (наследуется через `IMinecraft`). `state` — публичное поле включённости; `setState(b)`/`toggle()` дёргают `onEnable/onDisable` (toggle ещё со звуком+нотификацией).
- Модуль по умолчанию выключен. Чтобы был включён по умолчанию — выстави `state = true` в конструкторе (пример: `render/Cape.java`).
- **Конфликт за один ресурс** (напр. несколько модулей пишут `EventMotion.setYaw`): «выигрывает» тот, кто обработал событие ПОЗЖЕ (т.е. дальше в списке). Combat → Misc → Movement → Player → Render. Пример разрешения: `Spinbot.combatRotating()` уступает, если `gunAimbot.isLocked()` или у `attackAura` есть цель.

## Настройки (`Setting`) и сериализация

Подтипы в `ru/levin/modules/setting/`. Конструкторы (последний опц. аргумент `Supplier<Boolean> visible` — условный показ в GUI):
- `BooleanSetting(name, default[, desc][, visible])`
- `SliderSetting(name, value, min, max, increment[, visible])` — `.get()` возвращает `Number` (→ `.floatValue()/.intValue()`).
- `ModeSetting(name, selected, modes...)` или `(visible, name, selected, modes...)` — `.is("X")` это **substring**-проверка `selected.contains("X")` (избегай имён-подстрок друг друга).
- `MultiSetting(name, List<String> selected, String[] modes)` — `.get("X")` булево.
- `BindSetting`, `BindBooleanSetting` (клавиша-тоггл опции), `TextSetting`.
- Условие видимости часто ссылается на поле, объявленное **ниже** → в лямбде используй `this.field` (иначе «illegal forward reference», см. подводные камни).

Сохранение полностью реализовано в `Function.save()/load()` (Gson) и покрывает все типы выше. Менять формат не нужно — просто используй стандартные `Setting`.

## Ротации и silent-aim (важнейший механизм)

- `Manager.ROTATION` (`RotationController`) хранит серверный поворот `{yaw,pitch}`; `set/setSmooth/smoothReturn/getYaw/getPitch`.
- `MixinClientPlayerEntity`:
  - `tick` HEAD → `Event.call(new EventUpdate())`, затем кэш `preYaw/prePitch` (реальный взгляд).
  - `sendPosition` HEAD → создаёт `EventMotion(x,y,z,yaw,pitch,onGround)`, `Event.call`; затем применяет к игроку `setYRot/ setXRot/ setOnGround` из события → именно эти значения уходят в move-пакет.
  - после `sendPosition` → восстанавливает `preYaw/prePitch/preOnGround` → **камера остаётся на месте** (silent).
- Итог: **`EventMotion.setYaw/setPitch/setOnGround` меняют то, что уходит на сервер, не трогая визуал.** На этом построены: GunAimbot (silent-аим), Spinbot (серверное вращение), NoFall (спуф onGround). Чтобы крутить ВИДИМО — это другой механизм (ставить реальный yaw, см. историю Spinbot), но silent проще и не дёргает камеру.

## Конфиги

- `ConfigManager`: `CONFIG_DIR = <gameDir>/files/configs` (используй прямые `/`, НЕ `\\` — это ломает Linux), автоконфиг `AUTOCFG.cfg`.
- Автозагрузка `AUTOCFG` в `init()`, автосейв в `shutDown()` (shutdown-hook + `MixinMinecraftClient/Server`). Имя регистра должно совпадать (`AUTOCFG`, не `autocfg`) — на регистрозависимой ФС это разные файлы.
- Команда `.cfg save/load/remove/list/...`. `Config.save/load` сериализует все модули (`module.save()`), тему и метаданные.

## Интеграция с TACZ (всё через `try/catch(Throwable)` — graceful без мода)

Подтверждённый API (по `libs/tacz-…jar`, через `javap`):
- `IGun.getIGunOrNull(stack)` — null если не оружие; `getCurrentAmmoCount`, `getFireMode` (`AUTO/SEMI/BURST/UNKNOWN`), `getRPM`, `hasBulletInBarrel`, `hasInventoryAmmo(entity, stack, useInv)`, `getGunId`.
- Клиент: `IClientPlayerGunOperator.fromLocalPlayer(mc.player)` → `shoot()` (→ `ShootResult.SUCCESS/COOL_DOWN/NO_AMMO/NEED_BOLT/IS_RELOADING/...`), `reload()`, `bolt()`, `aim(b)`, `getClientAimingProgress(partial)`.
- Сущность: `IGunOperator.fromLivingEntity(entity)` → `getSynShootCoolDown`, `getSynReloadState().getStateType().isReloading()`, `getSynIsBolting`, `getCacheProperty()`.
- Баллистика (см. `GunAimbot.gunBallistics()`): эффективная скорость = `cache(AMMO_SPEED) * GLOBAL_BULLET_SPEED_MODIFIER(=2.0) / 20` блоков/тик; gravity/friction из `BulletData`. Пуля — летящий снаряд `com.tacz.guns.entity.EntityKineticBullet` (трассеры детектят его по имени класса).
- `shoot()` сам гейтит кулдаун/патроны/перезарядку → для автоогня зовём каждый тик; **не** ставим гейт «один выстрел на наведение» (иначе SEMI/DMR молчат после первого).
- TACZ-миксины — в `exosware.tacz.mixins.json` (`required=false`). Скоупь правки по UUID локального игрока (`MixinInaccuracyType` как образец, `remap=false`).
- Модули вокруг TACZ: `GunAimbot` (silent-аим: сортировка/залипание/хитчанс/человечность/бэктрек/видимая точка), `GunNoSpread`, `NoRecoil`, `FastAds`, `NoVisualAds`, `AutoReload`, `BulletTracers`, gun-ESP в `NameTags`.

## Рендер

- События: `EventRender3D` (мир, из `MixinGameRenderer.renderLevel`), `EventRender2D` (GUI, из `MixinInGameHud.render` → `Gui.render` HEAD).
- 3D-примитивы: `RenderUtil.render3D` (`Render3DUtil`): `drawBox(AABB,...)`, `drawLine(Vec3 start, Vec3 end, int argb, float width, boolean depth)` — **мир-координаты**, трансформ в вид делается внутри (буферы `LINE/LINE_DEPTH` флашатся в `onWorldRender`). `depth=true` — с тестом глубины (перекрывается блоками), `false` — сквозь стены.
- Цвет темы: `ColorUtil.getColorStyle(hueOffset[, alpha])` (ARGB). Образцы: `ESP`, `TargetESP`, `Breadcrumbs`, `BulletTracers`.

## Команды и автодополнение

- На **Brigadier**: `CommandManager` (`dispatcher`, источник `ClientSuggestionProvider`), префикс `.`. Базовый класс `Command` (`literal/arg`).
- Подсказки чата: `MixinChatInputSuggestor` хукает `CommandSuggestions.updateCommandInfo`, парсит ввод нашим диспатчером (пропуская префикс) и подставляет `getCompletionSuggestions`. Литералы-подкоманды подсказываются автоматически; для `word()/string()`-аргументов нужен `.suggests(...)` ИЛИ кастомный `ArgumentType` с `listSuggestions` (`PlayerArgumentType` — онлайн-игроки, `FriendArgumentType` — друзья).

## Каталог модулей (категории = `Type`)

- **Combat:** GunAimbot (#1), Criticals, TargetStrafe, NoFriendDamage, AutoExplosion, AttackExtend, AttackAura, CrystalAura, SelfTrap, AutoPotion, AutoTotem, AutoSwap, SuperBow, HitBox, AntiBot, Velocity, NoRecoil, GunNoSpread, FastAds, NoVisualAds, **AutoReload**.
- **Movement:** Blink, Phase, AutoSprint, HighJump, Flight, ElytraTarget/Recast/Motion, SuperFirework, FreeLook, Speed, Strafe, Spider, AirStuck, NoSlow, Timer, NoWeb, **Spinbot** (silent), **NoFall**.
- **Player:** GuiWalk, NoDelay, AutoLeave, AutoMessage, ClickAction, ItemScroller, ItemFixSwap, PerfectTime, NoRayTrace, NoPush, AutoRespawn, AutoTool, FreeCamera, CustomCoolDown, MiddleClickFriend/Pearl, NoInteract, ChestStealer, EnderChest/Invsee/Region-Exploit.
- **Render:** ClickGUI, HUD, SwingAnimations, ViewModel, AspectRatio, CrossHair, FullBright, World, NoRender, BlockESP, ItemPhysic, ExtraTab, Arrows, ESP, NameTags, Prediction, BlockHighLight, AutoAccept, JumpCircles, Breadcrumbs, Trails, **BulletTracers**, Particles, TargetESP, TPLoot, LittleSnickers, **Cape**.
- **Misc:** UnHook, Optimizer, ClientSounds, DeathCoords, ServerRPSpoff, Xray, ElytraHelper, FTHelper, HWHelper, RWHelper, AutoDuel(+Bot), DiscordRCP, Globals, IRC, NameProtect, NoCommands, **AntiScreenshot**.

## Подводные камни

- **`gradlew` без бита исполнения** → `./gradlew` даёт «Отказано в доступе». Фикс: `chmod +x gradlew` (а не `sh gradlew`).
- **Illegal forward reference**: лямбда видимости настройки, ссылающаяся на поле объявленное ниже, — квалифицируй `this.field` или объяви поле выше.
- **Windows-пути в коде** (`"\\a\\b"`) ломаются на Linux (бэкслеш — литерал). Используй `/` или `new File(parent, child)`.
- **Mixin `defaultRequire=1`** (`exosware.mixins.json`): несошедшийся инжект = краш на старте. Это и есть проверка валидности точки хука — чистый старт = инжект применился.
- **EventMotion и порядок**: серверный поворот перетирается модулем, который обработал событие позже (порядок списка `functions`).
- **Spinbot/наблюдатели**: быстрый silent-спин на 20 тиках/с для других выглядит ступенчато (снапы тела) — это предел протокола, а не баг.

## Верификация изменений (без юнит-тестов)

1. `./gradlew compileJava` — компиляция.
2. `./gradlew runClient` (фоном) → лог. Маркеры успешного старта: `Setting user:` → `Sound engine started` → `joined the game`. Маркеры провала: `Critical injection`, `was not applied`, `Exception in thread`, `BUILD FAILED`. Конфиг: `Загружаю AutoCfg` / `Конфиг AUTOCFG сохранён`.
3. Геймплейные фичи (аим/автоогонь/трассеры/спин) проверяются вручную в игре.
