# План переноса ExosWare: Fabric 1.21.4 → NeoForge 1.21.1

> Рабочий план миграции. Составлен на основе code-grounded инвентаризации всех подсистем
> (6 параллельных проходов по реальным файлам). Справочник имён и реестр version-deltas вынесены в
> [migration-mappings.md](migration-mappings.md). Базовая архитектура описана в
> [architecture.md](architecture.md), модули — в [modules-catalog.md](modules-catalog.md), миксины — в [mixins.md](mixins.md).

## TL;DR

Это **двойная** миграция по двум ортогональным осям:

1. **Загрузчик: Fabric → NeoForge** — поверхность КРОШЕЧНАЯ. Всего **4 Fabric-символа** во всём проекте
   (`ModInitializer`, `@Environment/EnvType`, один `ClientTickEvents`). Конвертация билда и точки входа — малый, механический объём.
2. **Версия: 1.21.4 → 1.21.1 (даунгрейд)** — это и есть основная работа. И ключевой вывод:

> **Почти все «трудные» места — это откат рефакторингов, появившихся в 1.21.2.**
> 1.21.2 был крупным техническим апдейтом. Код ExosWare написан под пост-1.21.2 API; чтобы опуститься на
> 1.21.1, нужно «вернуть назад» именно эти рефакторинги. Всё остальное — массовый, но механический
> ремап имён **Yarn → Mojmap** (NeoForge работает на официальных маппингах Mojang).

Перечень того, что реально откатывается с 1.21.2 на 1.21.1 (это «костяк» риска):

| Рефакторинг 1.21.2 | Что в 1.21.1 | Где болит |
| --- | --- | --- |
| Система **EntityRenderState** (рендер по извлечённому состоянию) | рендер напрямую по сущности | `RenderAddon`, `MixinLivingEntityRenderer`, `MixinPlayerEntityRenderer`, `MixinItemEntityRenderer` |
| **ShaderProgramKey / Defines / ShaderProgramKeys** | `GameRenderer::get*Shader` (Supplier) + кастомный реестр шейдеров | `RenderUtil`, `RenderAddon`, `ResourceProvider`, ~14 рендер-файлов |
| **FrameGraph**-рендер (`LevelRenderer.renderMain`) | хуки в `renderLevel` | `MixinWorldRenderer` |
| Рекорд **`Fog`** / новый `FogRenderer` | `FogRenderer.setupFog` (void, мутирует стейт) | `MixinBackgroundRenderer`, `MixinGameRenderer` |
| **Группы кулдаунов** (`ItemCooldownManager` по `Identifier`) | кулдауны по `Item` | `ItemCooldown*Accessor`, `HUD`, `MixinItem`, `MiddleClickPearl` |
| Рекорд **`PlayerInput`** / `KeyboardInput.playerInput` | прямые поля `Input` | `MixinKeyboardInput`, `MixinClientPlayerEntity` |
| Параметр `LivingEntity` в **`Item.getMaxUseTime/finishUsing`** | без него (`getUseDuration(ItemStack)`) | `MixinItem`, `SwingAnimations`, `PerfectTime` |
| Снятие префиксов **`EntityAttributes`** (`GENERIC_…`) | с префиксами | `AuraUtil` |
| `VehicleMoveC2SPacket.fromVehicle`, рекорд `PlayerInputC2SPacket` | старые конструкторы | `KTLeave` |

Оценка объёма: **L (большой)**. Не из-за объёма печати (ремап скриптуется), а из-за «хвоста верификации»:
каждое version-delta-место нужно **сверять с реальными исходниками 1.21.1 Mojmap**, а не угадывать.

---

## 1. Цель и целевая платформа

| Параметр | Значение |
| --- | --- |
| Загрузчик | **NeoForge 21.1.x** (LTS-ветка под MC 1.21.1; пинить последний патч 21.1) |
| Minecraft | **1.21.1** |
| Маппинги | официальные **Mojang (Mojmap)**; Parchment — опционально для имён параметров |
| Сборка | **ModDevGradle** (`net.neoforged.moddev`) вместо Fabric Loom |
| Java | 21 (без изменений) |
| Объём | **фулл-порт** — все ~90 модулей, 62 миксина, кастомный GUI |

**Предположения (подтвердить при необходимости):** целимся именно в NeoForge (не Forge), фулл-порт (не MVP-подмножество),
допустимо сменить `mod_version` со строки `1.21.4` на нормальный semver (`1.0.0`).

---

## 2. Ключевые стратегические решения

1. **Сначала достать исходники 1.21.1 Mojmap.** Это решение №1: практически каждый сложный пункт — это «verify
   exact Mojmap name/signature against 1.21.1». ModDevGradle отдаёт decompiled sources NeoForge/MC — открыть их в IDE и
   превратить догадки в lookup'ы. Без этого порт превратится в угадайку.
2. **Загрузчик меняем первым, отдельным малым шагом.** Build + `@Mod` + `mods.toml` + регистрация миксин-конфига.
3. **Yarn → Mojmap — отдельный механический проход** (сначала типы/импорты, потом члены), но с ручным QA коллизий.
   ⚠️ В моде есть СВОЙ класс `ru.levin.modules.render.World` — слепой `World→Level` его сломает.
4. **Version-delta-файлы выносим в карантин** — их НЕ трогает скрипт, переписываем руками против 1.21.1.
5. **Kotlin выбрасываем.** В исходниках 0 `.kt`-файлов; `kotlin.jvm` + `kotlin-stdlib` — мёртвый груз. Lombok остаётся.
6. **`@Environment` удаляем** (клиентский мод) или меняем на `@OnlyIn(Dist.CLIENT)`.
7. **Единственный `ClientTickEvents`** в `LittleSnickers` — лучше свернуть в уже существующий хук `EventUpdate`
   (он и так шлётся из `MixinClientPlayerEntity#tick`), чтобы вообще не зависеть от NeoForge-tick-API.
8. **Миксины переносятся почти как есть** — sponge-mixin и MixinExtras работают на NeoForge; меняются только имена
   целей (Mojmap), регистрация конфига и удаление `@Environment`. `defaultRequire=1` — наш друг: несошедшиеся
   инъекции упадут громко, а не молча.
9. **Discord-RPC нативы НЕ через jarJar** — JNA грузит их из classpath по пути `{os-prefix}/discord-rpc.{ext}`;
   оставить как ресурсы в корне. **netty proxy/socks — через jarJar**, но проверить конфликт версий с netty,
   который уже тащит MC/NeoForge.

---

## 3. Оценка объёма и зоны риска (где сосредоточен труд)

| Ось | Объём | Характер |
| --- | --- | --- |
| Loader + Build | **S–M** | механический, несколько файлов + 3 Fabric-точки |
| Yarn→Mojmap (типы+члены) | **L по площади / M по сложности** | ~1204 ссылки в 146 файлах, скриптуется |
| Пакеты | **M** | ~139 ссылок/37 файлов, в основном переименование; 3 version-delta пакета |
| Миксины | **L** | 62 класса: ~20 чисто механических, ~10 с настоящими version-deltas |
| **Рендер** | **L (главный)** | core-shader реестр, render-state, FrameGraph/Fog — реальные переписывания |
| **Кулдауны** | **M–L** | целиком на 1.21.2-API групп, переписать на Item-keyed |
| Поведенческие/реестры | **M** | в основном rename; точечно — атрибуты, getMaxUseTime, AutoTool-энчанты |

**Самые горячие файлы по объёму ремапа:** `AttackAura` (36), `HUD` (33), `LittleSnickers` (26), `KTLeave` (26),
`InventoryUtil` (25), `AutoPotion` (23), `ClientManager` (22), `RenderAddon` (21), `ElytraTarget` (21).

**Самые рискованные узлы:** core-shader подсистема, render-state миксины, FrameGraph/Fog миксины, подсистема кулдаунов,
хрупкие `@Local(ordinal=…)`/`@At` цели.

---

## 4. Поэтапный план

Порядок учитывает зависимость: NeoForge компилируется только на Mojmap, поэтому код не соберётся, пока не пройдёт ремап.

### Фаза 0 — Подготовка инструментария и эталона *(полдня)*
- Поднять NeoForge 21.1 проект-скелет на ModDevGradle (см. Фаза 1), получить **decompiled sources 1.21.1 Mojmap** в IDE.
- Зафиксировать ветку/копию исходного Fabric-проекта для сверки поведения.
- Подготовить машинный справочник маппингов (см. [migration-mappings.md](migration-mappings.md)) для скриптовых проходов.
- **Критерий готовности:** пустой `@Mod`-скелет собирается и запускает клиент 1.21.1; sources Mojmap доступны для lookup.

### Фаза 1 — Конвертация загрузчика и сборки *(S–M)*
Файлы: `settings.gradle`, `build.gradle`, `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`
(новый, вместо `fabric.mod.json`), `ExosWare.java`, `MixinMinecraftClient.java`, `LittleSnickers.java`, `exosware.mixins.json`.

1. `settings.gradle` → репозиторий `maven.neoforged.net/releases`, плагин `net.neoforged.moddev`.
2. `build.gradle` → ModDevGradle: `neoForge { version = neoforge_version }`, убрать loom/kotlin, оставить lombok,
   netty через `jarJar`+`implementation`, убрать `force sponge-mixin`.
3. `gradle.properties` → `neoforge_version=21.1.x`, `minecraft_version=1.21.1`, `mod_version=1.0.0`, опц. `parchment_version`.
4. `fabric.mod.json` → `neoforge.mods.toml` (`modId=exosware`, `[[mixins]] config="exosware.mixins.json"`, зависимости на
   `neoforge` + `minecraft ~1.21.1`); `processResources` expand нацелить на `neoforge.mods.toml`.
5. `ExosWare`: `implements ModInitializer` → `@Mod("exosware")` + конструктор `(IEventBus modBus, ModContainer)`; тело
   `onInitialize()` (setupProtection + shutdown hook) переносится в конструктор. Отложенный `init()` остаётся как есть
   (его по-прежнему дёргает миксин в конструктор `Minecraft`).
6. `MixinMinecraftClient`: убрать `@Environment`; `@Mixin(Minecraft.class)`; имена целевых методов
   (`isMultiplayerEnabled/getWindowTitle/stop`) — сверить с Mojmap 1.21.1.
7. `LittleSnickers`: убрать `ClientTickEvents`, свернуть логику в `EventUpdate`.
8. Discord-нативы оставить ресурсами; проверить, что `processResources` кладёт их в jar по тем же путям.
- **Критерий готовности:** структура — NeoForge; проект ещё НЕ компилируется (ждёт ремап), но билд-конфиг валиден.

### Фаза 2 — Массовый ремап Yarn → Mojmap *(L по площади)*
По таблицам из [migration-mappings.md](migration-mappings.md). Порядок важен:
1. **Сначала импорты с полным именем** (там, где меняется пакет: `util.math.Vec3d→world.phys.Vec3`,
   `text.Text→network.chat.Component`, `util.Identifier→resources.ResourceLocation` и т.д.).
2. **Потом простые имена типов** (`MinecraftClient→Minecraft`, `DrawContext→GuiGraphics`, `MatrixStack→PoseStack`,
   `Box→AABB`, `MathHelper→Mth`, `Formatting→ChatFormatting`…). Типы, у которых меняется только пакет
   (`Entity/LivingEntity/BlockPos/BlockState/ItemStack/Item/Camera/HitResult/Screen`), — НЕ трогать по короткому имени.
3. **Затем члены** (отдельный word-boundary проход ПОСЛЕ типов): `.world→.level` (145), `getYaw→getYRot` (65),
   `getPitch→getXRot` (56), `networkHandler/getNetworkHandler→getConnection` (107), `sendPacket→send` (52),
   `interactionManager→gameMode` (41), `getPos()→position()` (69), `selectedSlot→selected` (29) и др.
4. **Пакеты** — отдельным проходом по таблице (`PlayerMoveC2SPacket→ServerboundMovePlayerPacket`, …; пакеты
   `network.packet.c2s/s2c.play → network.protocol.game`).
- ⚠️ Исключить из скрипта карантинные файлы (Фаза 3). ⚠️ Никогда не заменять неоднозначные короткие имена
  (`World`, `Item`, `Block`) слепым токеном — только по типизированным/квалифицированным ссылкам.
- **Критерий готовности:** компилируется всё, КРОМЕ карантинных version-delta файлов.

### Фаза 3 — Откат version-delta API 1.21.2 → 1.21.1 *(основная сложная работа)*
Карантинные work-units (переписывать руками против 1.21.1 sources):

- **3a. Core-shader подсистема.** Реестр кастомных шейдеров (texture/rectangle/blur/border/glass) через NeoForge
  `RegisterShadersEvent` (`Map<String, ShaderInstance>`). Убрать `ShaderProgramKey/Defines`. В `RenderUtil`/`RenderAddon`
  заменить идиому `ShaderProgram s = RenderSystem.setShader(key)` (в 1.21.1 `setShader` принимает `Supplier` и возвращает
  void). `ShaderProgramKeys.*` → `GameRenderer::get*Shader`. GLSL `.vsh/.fsh` и JSON — почти как есть.
- **3b. Удаление EntityRenderState.** `RenderAddon` (фейк-игрок/голова) и 3 рендер-миксина переписать на 1.21.1
  `LivingEntityRenderer.render(entity, yaw, partialTick, PoseStack, MultiBufferSource, light)` и `getTextureLocation(entity)`;
  `ItemEntityRenderer.render` читает age/bobOffset с сущности напрямую.
- **3c. FrameGraph/Fog/Gui миксины.** `MixinWorldRenderer` → хук блок-аутлайна в `LevelRenderer.renderLevel`
  (нет `renderMain(FrameGraphBuilder…)`); `MixinBackgroundRenderer` → `FogRenderer.setupFog` (void, без рекорда `Fog`);
  `MixinInGameHud` — сверить сигнатуры `Gui.render(GuiGraphics, DeltaTracker)`/`renderCrosshair`/`renderEffects` и
  `@At FIELD CROSSHAIR_TEXTURE`; `MixinGameRenderer` — перепроверить хрупкий `@Local(ordinal=2) Matrix4f`.
- **3d. Подсистема кулдаунов → Item-keyed.** Переписать оба аксессора (`entries` → `Map<Item, Entry>`, поле `cooldowns`,
  тик-поле — сверить `tickCount`; `Entry` — `startTime/endTime` вместо `endTick`). В `HUD` убрать `getGroup(stack)` и
  индексировать по `stack.getItem()`. В `MixinItem` `set(stack,…)` → Item-overload; в `MiddleClickPearl` `isCoolingDown` → Item.
- **3e. Input + Item-сигнатуры + атрибуты.** `MixinKeyboardInput` — выкинуть рекорд `PlayerInput`, писать поля `Input`
  напрямую (`forwardImpulse/leftImpulse/jumping/shiftKeyDown`). `MixinItem.use` → возвращает `InteractionResultHolder<ItemStack>`;
  `getMaxUseTime(ItemStack)` без `LivingEntity`; снять `mc.player` в `SwingAnimations`/`PerfectTime`. `AuraUtil` —
  вернуть префикс `GENERIC_` атрибута скорости. `getAttackCooldownProgress` — сверить Mojmap-имя
  (вероятно `getAttackStrengthScale`) в `AttackAura`/`AutoExplosion`/`CrossHair`. `AutoTool` — переделать цепочку
  Holder<Enchantment> на `registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY)`.
- **3f. Version-delta пакеты.** `KTLeave`: `VehicleMoveC2SPacket.fromVehicle(e)` → конструктор `ServerboundMoveVehiclePacket(e)`;
  убедиться, что `PlayerInputC2SPacket`/`PlayerPositionLookS2CPacket` используются только в `instanceof`. `AutoPotion` —
  сверить sequenced-prediction (`PendingUpdateManager`) с 1.21.1.
- **3g. DeltaTracker + фреймбуфер/текстуры.** `RenderTickCounter→DeltaTracker` (сверить `getTickDelta(true)`-эквивалент),
  поля `RenderTickCounterDynamicAccessor`. `SimpleFramebuffer→TextureTarget`, `Framebuffer→RenderTarget`,
  `NativeImageBackedTexture→DynamicTexture` в `RenderUtil.drawBlur`/`RenderFonts`.
- **Критерий готовности:** весь проект компилируется на 1.21.1 Mojmap.

### Фаза 4 — Верификация точек инъекции миксинов *(итеративно)*
`defaultRequire=1` заставит каждый миксин примениться. Запускать с mixin debug/verbose и чинить нерезолвящиеся
`@At`/`@Local`/ординалы и `@Accessor/@Invoker`-имена против декомпиляции 1.21.1. Здесь же финально прибиваются все
`verify=true`-имена полей (`jumpingCooldown`, `lastSprinting`, `pendingUpdateManager`, `bossBars`, `selected` и т.д.).
- **Критерий готовности:** клиент стартует, все миксины применяются без отказов.

### Фаза 5 — Прогон в рантайме и QA по модулям *(итеративно)*
- Запуск клиента, прогон ClickGUI, проверка категориями: Combat → Movement → Render → Player → Misc.
- Проверить поведенческие узлы: кулдаун-HUD, AttackAura-тайминг, элитра, AutoTool dig-speed, прокси-подключение,
  Discord RPC, IRC.
- Сверить «тихие» баги (компилируется, но ведёт себя иначе): атрибуты, getAttackCooldownProgress, AutoTool-уровень энчанта.
- **Критерий готовности:** базовый набор модулей каждой категории работает в игре.

---

## 5. Реестр главных рисков

| Риск | Воздействие | Митигация |
| --- | --- | --- |
| Core-shader API (ShaderProgramKey/Defines) не существует в 1.21.1 | Весь UI/HUD (скруглённые прямоугольники, бордеры, блюр, головы) не рендерится | Реестр через `RegisterShadersEvent` сделать рано; обернуть `getUniform/setShader` тонким compat-хелпером |
| FrameGraph/Fog/EntityRenderState — пост-1.21.1 | 7+ миксинов и `RenderAddon` не резолвятся | Переписать против декомпиляции 1.21.1, не ремапить; бюджетировать как отдельные задачи с рантайм-проверкой |
| Кулдауны на группах (Identifier) | HUD-кулдаун + `CustomCoolDown` (MixinItem) ломаются полностью | Переписать аксессоры/вызовы на Item-keyed; сверить имена полей с реальными исходниками |
| `getMaxUseTime`/`finishUsing` +param `LivingEntity` (1.21.2) | Миксин не применяется / ~6 call-site не компилируются | Снять параметр на 1.21.1 |
| Хрупкость `@Local(ordinal)`/`@At` между версиями | Тихая мис-инъекция или отказ `defaultRequire=1` | После ремапа — mixin verbose + сверка ординалов по байткоду 1.21.1 |
| Mojmap-имена аксессоров — догадки | Молчаливый отказ резолва аксессора | Сверить каждое `verify=true`-имя по официальному mapping-файлу 1.21.1 |
| netty proxy/socks vs netty из MC | Рантайм `LinkageError` или сломанный прокси | Согласовать версию netty с MC 1.21.1; нести только реально отсутствующие модули; тест с прокси |
| Discord-нативы попали в jarJar | `UnsatisfiedLinkError` на старте | Держать как ресурсы в корне `{os-prefix}/`, никогда не в jarJar |
| Слепой ремап коллизит с `ru.levin…World` и др. | Порча собственных типов мода | Только квалифицированные ссылки; пер-файловая проверка компиляции |

---

## 6. Стратегия верификации

1. **Sources-driven:** каждый `verify=true` пункт — это lookup в decompiled 1.21.1, а не память.
2. **`defaultRequire=1`** делает отказы миксинов громкими — использовать это как тест-сеть на Фазе 4.
3. **Покомпонентная компиляция:** после скриптовых проходов чинить горячие файлы первыми (`AttackAura`, `HUD`, …).
4. **Пер-модульный smoke-test** в реальной игре по категориям (Фаза 5).
5. **Параллельная сверка поведения** со старым Fabric-билдом для «тихих» регрессий (тайминги, атрибуты).

---

## 7. Приложения

- **[migration-mappings.md](migration-mappings.md)** — полные таблицы Yarn → Mojmap (типы, члены, пакеты),
  реестр version-deltas с флагами `verify` и списком файлов, и список карантинных (ручных) файлов.
