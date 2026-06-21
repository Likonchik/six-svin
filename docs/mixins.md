# Миксины (хуки Minecraft)

Слой миксинов (`ru.levin.mixin`) — это мост между ванильным Minecraft 1.21.4 и клиентом ExosWare. Именно здесь происходит внедрение байт-кода (bytecode injection) через [SpongePowered Mixin](https://github.com/SpongePowered/Mixin), который позволяет:

1. **Вызывать события** клиентской шины `ru.levin.events.Event`, потребляемые модулями (`Function`).
2. **Читать флаги функций** из `Manager.FUNCTION_MANAGER.<module>.state` и применять поведение (combat-аура, движение, удаление рендера, туман, хитбокс).
3. **Перехватывать чат/сеть** для системы команд и SOCKS-прокси.
4. **Применять анти-детект** через `ClientManager.legitMode` (скрытие брендинга, кастомного меню, скинов, статус-эффектов при «legit-режиме»).

Миксины — это тонкие переходники: реальная логика живёт в модулях, менеджерах и событиях, а миксины лишь делегируют им управление в нужных точках движка.

---

## Конфигурация Loom / Mixin

Миксины подключаются через файл `src/main/resources/exosware.mixins.json` и обрабатываются плагином Fabric Loom во время сборки.

### `exosware.mixins.json`

```json
{
    "required": true,
    "package": "ru.levin.mixin",
    "compatibilityLevel": "JAVA_21",
    "refmap": "exosware-refmap.json",
    "mixins": [ "world.MixinBlock", "client.MixinMinecraftClient", "..." ],
    "injectors": {
        "defaultRequire": 1
    }
}
```

| Параметр | Значение | Назначение |
|---|---|---|
| `required` | `true` | Конфигурация миксинов обязательна — при ошибке применения клиент не запустится. |
| `package` | `ru.levin.mixin` | Корневой пакет; все записи в `mixins` указываются относительно него (`world.MixinBlock`, `iface.ScreenAccessor` и т.д.). |
| `compatibilityLevel` | `JAVA_21` | Целевой байт-код Java 21 (соответствует `release = 21` в `build.gradle`). |
| `refmap` | `exosware-refmap.json` | Имя refmap-файла, генерируемого Mixin AP. Содержит сопоставление обфусцированных/yarn-имён MC, чтобы инъекции находили целевые методы в продакшен-маппингах. |
| `injectors.defaultRequire` | `1` | Каждый инъектор по умолчанию обязан найти минимум одну точку внедрения, иначе сборка/запуск падает. |

Всего в конфиге перечислено **56 миксинов**: **45 классов-инъекторов** (`@Inject` / `@Redirect` / `@ModifyArgs` / `@ModifyArg` / `@ModifyVariable` плюс MixinExtras `@ModifyExpressionValue` / `@ModifyReturnValue` / `@Local`) и **11 интерфейсов-аксессоров** (`@Accessor` / `@Invoker`).

### Настройки сборки (`build.gradle` / `gradle.properties`)

| Настройка | Значение | Назначение |
|---|---|---|
| `loom.mixin.defaultRefmapName` | `exosware-refmap.json` | Имя refmap, согласованное с конфигом миксинов. |
| `loom.mixin.useLegacyMixinAp` | `true` | Использовать **устаревший Annotation Processor** миксинов (а не новый Loom-процессор). Требуется для совместимости с принудительно зафиксированной версией sponge-mixin. |
| `resolutionStrategy` (force) | `net.fabricmc:sponge-mixin:0.15.5+mixin.0.8.7` | Жёсткая фиксация версии Mixin, на которую завязан `useLegacyMixinAp`. |

> **Важно:** `useLegacyMixinAp=true` + принудительная версия `sponge-mixin 0.15.5` — взаимозависимая связка. Изменение одного без другого, скорее всего, сломает генерацию refmap или применение миксинов.

---

## Инъекторы (`@Mixin`) против аксессоров (`iface`)

Слой миксинов использует два принципиально разных стиля:

- **Инъекторы** — классы с `@Mixin(Target.class)`, которые **вставляют новый код** в существующие методы MC (через `@Inject`, `@Redirect`, `@ModifyExpressionValue` и т.д.). Они меняют поведение игры: отменяют ванильный вызов, подменяют возвращаемое значение, вызывают событие.
- **Аксессоры (`iface`)** — это **интерфейсы** с `@Mixin(Target.class)`, использующие `@Accessor` (доступ к приватным полям) и `@Invoker` (вызов приватных методов). Они **не меняют логику**, а лишь открывают приватные члены MC остальному коду клиента. Все 11 аксессоров собраны в подпакете `iface`.

Ниже миксины разбиты по подпакетам.

---

## `attack` — боевые хуки

| Миксин | Целевой класс MC | Что делает / какое событие вызывает |
|---|---|---|
| `attack.MixinAttackPlayer` | `ClientPlayerInteractionManager#attackEntity` | На `HEAD` отменяет атаку, если включён `noFriendDamage` и цель — друг (`FRIEND_MANAGER`). После отправки пакета атаки вызывает `EventAttack` и `RayTraceUtil.markHit(target)` (вспышка попадания). |

---

## `chat` — чат, команды, автодополнение

| Миксин | Целевой класс MC | Что делает / какое событие вызывает |
|---|---|---|
| `chat.MixinClientPlayNetworkHandler` | `ClientPlayNetworkHandler#sendChatMessage` | Точка входа системы команд. Если не `legitMode` и сообщение начинается с префикса `COMMAND_MANAGER`, выполняет команду через диспетчер Brigadier и отменяет отправку в чат. |
| `chat.MixinChatInputSuggestor` | `ChatInputSuggestor#refresh` | Захват локальных переменных (`CAPTURE_FAILHARD`). При вводе строки с префиксом команды парсит её через клиентский диспетчер и показывает подсказки автодополнения команд, отменяя ванильный refresh. |
| `chat.MixinChatScreen` | `ChatScreen` | На рендере рисует все включённые перетаскиваемые элементы из `DRAG_MANAGER` (drag HUD-элементов) и направляющие; перенаправляет `mouseClicked` (после suggestor'а) на `onClick` перетаскиваемых элементов. |

---

## `client` — жизненный цикл клиента, экраны, таймер

| Миксин | Целевой класс MC | Что делает / какое событие вызывает |
|---|---|---|
| `client.MixinMinecraftClient` | `MinecraftClient` | Принудительно `isMultiplayerEnabled=true`; переписывает `getWindowTitle` на `ExosWare 1.21.4 Fabric | <name>` (кроме `legitMode`); вызывает `ExosWare.init()` в `TAIL` конструктора и `shutDown()` при остановке. |
| `client.MixinMinecraftServer` | `MinecraftServer#shutdown` | Вызывает `ExosWare.getInstance().shutDown()` при остановке встроенного сервера (очистка). |
| `client.MixinTitleScreen` | `TitleScreen#init` | Кроме `legitMode`, заменяет ванильный титульный экран на кастомный `ru.levin.screens.mainmenu.MainMenu` и отменяет `init`. |
| `client.MultiplayerScreenMixin` | `MultiplayerScreen#init` | Добавляет кнопку `Прокси: <ip>` (через `ScreenAccessor`), открывающую `GuiProxy`; привязывает прокси аккаунта из `PROXY_MANAGER`. Координаты кнопки захардкожены (`width-320`, `y=479`, `100x20`). |
| `client.RenderTickCounterDynamicAccessor` | `RenderTickCounter$Dynamic#beginRenderTick` | Несмотря на имя — **инъектор** (не аксессор). При `ClientManager.TICK_TIMER != 1` пересчитывает `lastFrameDuration`/`tickDelta` с масштабом `TICK_TIMER` (чит Timer — ускорение/замедление тиков). |

---

## `display` — рендер, HUD, камера, инвентарь

| Миксин | Целевой класс MC | Что делает / какое событие вызывает |
|---|---|---|
| `display.MixinInGameHud` | `InGameHud#render` | Вызывает `EventRender2D` (шина 2D-рендера HUD); рендерит GPS/WayPoint и уведомления; убирает виньетку; заменяет прицел модулем `CrossHair`; скрывает оверлей статус-эффектов кроме `legitMode`. |
| `display.MixinGameRenderer` | `GameRenderer` | Вызывает `EventRender3D` в `renderWorld` (сохраняет матрицы для проекции); переопределяет `findCrosshairTarget` для рейкаста по углам `RotationController`/`AttackAura`/`CrystalAura`/`SelfTrap` (silent aim); переопределяет FOV под aspect-ratio; `NoRayTrace` (redirect → null); отменяет `tiltViewWhenHurt`. |
| `display.MixinBackgroundRenderer` | `BackgroundRenderer` | `getFogModifier` возвращает `null` при `noRender` «Плохие эффекты»; `@ModifyReturnValue` на `applyFog` вызывает `EventFog` и пересобирает `Fog` из значений события (кастомизация тумана). |
| `display.MixinCamera` | `Camera#update` | `FreeLook`: применяет углы из `CameraOverriddenEntity` после `setRotation`; `freeCamera` форсит third-person и подменяет аргументы `setRotation`/`setPos` фейковыми координатами камеры. |
| `display.MixinHandledScreen` | `HandledScreen#drawMouseoverTooltip` | `ItemScroller`: при зажатых ЛКМ+Shift выполняет `QUICK_MOVE` сфокусированного слота по интервалу `TimerUtil` (быстрый перенос предметов). |
| `display.MixinHeldItemRenderer` | `HeldItemRenderer#renderFirstPersonItem` | Отменяет ванильный рендер для непустых не-map предметов и делегирует `swingAnimations.renderFirstPersonItem` (кастомные анимации руки/свинга). |
| `display.MixinInGameOverlayRenderer` | `InGameOverlayRenderer` | Отменяет `renderFireOverlay`/`renderUnderwaterOverlay`/`renderInWallOverlay` по сабтоглам `noRender` («Огонь на экране»/«Вода на экране»/«Удушье»). |
| `display.MixinPlayerListHud` | `PlayerListHud#collectPlayerEntries` | Заменяет сбор записей таба; поднимает лимит таб-листа до 200 при включённом `extraTab` (иначе 80), пересортировка по spectator/team/name (ExtraTab). |
| `display.MixinWorldRenderer` | `WorldRenderer#render` | Перенаправляет `renderMain`, передавая `renderBlockOutline = !blockHighLight.isState()` (вкл/выкл ванильной обводки выделенного блока). |

---

## `player` — игрок, движение, сеть, ввод, рендер сущностей

| Миксин | Целевой класс MC | Что делает / какое событие вызывает |
|---|---|---|
| `player.MixinClientPlayerEntity` | `ClientPlayerEntity` | Вызывает `EventUpdate` (HEAD тика); вызывает `EventMotion` в `sendMovementPackets` и подменяет реальный/пакетный yaw/pitch вокруг него (rotation spoofing); `NoPush` «Блоки»; `NoSlow` через redirect `movementSideways`/`movementForward`/`setSprinting`; вызывает `EventSprint` через `@ModifyExpressionValue`. |
| `player.MixinClientConnection` | `ClientConnection` (`channelRead0`/`send`) | Вызывает `EventPacket` (RECEIVE/SEND, отменяемый) для каждого пакета; пропускает событие отправки при `NetworkUtils.isSendingSilent()` (поддержка silent-пакетов). Сетевой костяк всего клиента. |
| `player.MixinClientConnectionInitMixin` | `ClientConnection$1#initChannel` | Вставляет `Socks5ProxyHandler`/`Socks4ProxyHandler` (с опциональными user/pass) в голову netty-пайплайна при `PROXY_MANAGER.proxyEnabled`; обновляет подпись кнопки прокси (SOCKS-прокси на соединение). |
| `player.MixinClientPlayerInteractionManager` | `ClientPlayerInteractionManager#clickSlot` | Перенаправляет `sendPacket`: при `guiWalk` с обходом FunTime и движении в `HandledScreen` (`syncId 0`) ставит `ClickSlotC2SPacket` в очередь вместо отправки (задержка пакетов GUI-walk). |
| `player.MixinEntity` | `Entity` | Реализует `IEntity`/`CameraOverriddenEntity`/`IMinecraft`. `updateVelocity` — кастомный strafe для `AttackAura`/`TargetStrafe`/`AutoExplosion`/`CrystalAura`; `getBoundingBox` — расширение хитбокса (`HitBox`); хранит `Trails`; `FreeLook` `changeLookDirection`; `NoPush` для игроков; `fixFallDistance`; обнуление `pushAwayFrom`. |
| `player.MixinPlayerEntity` | `PlayerEntity#travel` | Перенаправляет `getRotationVector` на углы `RotationController` во время ауры (коррекция движения); вызывает `EventPlayerTravel` (pre и post), переезжая с `SELF`-velocity и отменяя при отмене события. |
| `player.MixinLivingEntity` | `LivingEntity` | `getHandSwingDuration` — медленная анимация свинга; `jump` — внедряет sprint-jump velocity по yaw ауры/explosion/crystal; `calcGlidingVelocity` пересчитывается к yaw/pitch `RotationController` (управление элитрой во время ауры). |
| `player.MixinLivingEntityRenderer` | `LivingEntityRenderer#updateRenderState` | Для локального игрока (не в `InventoryScreen`) вызывает `EventPlayerRender`, временно подменяя head/body yaw/pitch и восстанавливая на `TAIL` (модуль Rotations — визуальный поворот тела). |
| `player.MixinPlayerEntityRenderer` | `PlayerEntityRenderer#renderLabelIfPresent` | Отменяет рендер ванильного ника, когда включён `nameTags` с сабтоглом «Игроки» (кастомные NameTags ESP заменяют ванильные подписи). |
| `player.MixinFireworkRocketEntity` | `FireworkRocketEntity#tick` | Перенаправляет `shooter.setVelocity` для расчёта скоростей `SuperFirework` элитры по yaw/диагонали в режимах ReallyWorld/BravoHvH/PulseHVH/Custom (захардкоженные скорости, напр. диагональ 1.963), прицеливаясь через `RotationController`/`AttackAura`. |
| `player.MixinItem` | `Item` (`use`/`finishUsing`/`getMaxUseTime`/`isUsedOnRelease`) | Реализует `CustomCoolDown`: блокирует использование предмета (`ActionResult.FAIL` / time 0 / false) во время кастомного кулдауна; в `finishUsing` ставит тики `ItemCooldownManager` (cooldown*20); ограничено PVP. |
| `player.MixinFixRw` | `Scoreboard#removeScoreHolderFromTeam` | **Безусловно** отменяет метод (`ci.cancel()`) независимо от каких-либо тоглов — не даёт серверу удалять игроков из команд (фикс NameTag/RW-десинка). |
| `player.MixinKeyBoard` | `Keyboard#onKey` | При нажатии (`action==1`) без открытого экрана создаёт `new ExosWare()` и вызывает `keyPress(key)` — глобальная диспетчеризация нажатий модулей/биндов. |
| `player.MixinKeyboardInput` | `KeyboardInput#tick` | Полностью заменяет тик движения: считает forward/strafe, вызывает `EventKeyBoard` (модули могут переопределить движение/прыжок/сник/спринт), пересобирает `PlayerInput`, отменяет ванильное (шина ввода движения). |
| `player.MixinMouse` | `Mouse#onMouseButton` | При нажатии вызывает `ExosWare.keyPress(-100+button)` для мышиных биндов и `EventMouse`; на `ChatScreen` направляет отпускание drag на перетаскиваемые элементы `DRAG_MANAGER`. |

---

## `util` — служебные хуки и анти-детект

| Миксин | Целевой класс MC | Что делает / какое событие вызывает |
|---|---|---|
| `util.MixinClientPlayerInteractionManager` | `ClientPlayerInteractionManager` (`interactItem`/`interactBlock`) | `CustomCoolDown` блокирует взаимодействие с предметом; `NoInteract` возвращает `PASS` для контейнерных/утилитарных блоков (сундуки, печь, наковальня, шалкер, заборы, люки) при активной ауре или выключенном `onlyAura` (анти-мисклик в бою). |
| `util.MixinFlowableFluid` | `FlowableFluid#getVelocity` | При `noPush` «Вода» обнуляет/нормализует векторную силу потока жидкости (с `-6` вниз при заблокированном спадающем потоке), чтобы течение не толкало игрока. |
| `util.MixinTextVisitFactory` | `TextVisitFactory#visitFormatted` (`@ModifyArg` индекс 0) | `NameProtect`: заменяет локальный ник (и опционально друзей) во всём отрендеренном тексте на кастомное имя — скрытие имени на скриншотах/стримах. |
| `util.MixinServerList` | `ServerList` (`loadFile`/`saveFile`) | Внедряет захардкоженный промо-сервер `ServerInfo('Лучший HvH сервер!','mc.furryhvh.ru')` в список кроме `legitMode` (который его удаляет); дедуп по адресу; не сохраняет промо-запись на диск (реклама в списке серверов). |
| `util.MixinPackScreen` | `PackScreen#init` | В `legitMode` читает `UnHookCommand.CUSTOM_PATH_FILE` и перенаправляет `Path` папки ресурс-паков на его содержимое (перенаправление пути ресурс-паков). |

---

## `world` — мир, блоки, коллизии, рендер предметов, скины

| Миксин | Целевой класс MC | Что делает / какое событие вызывает |
|---|---|---|
| `world.MixinBlock` | `Block#onPlaced` | При установке блока `OBSIDIAN` вызывает `EventObsidianPlace(block, pos)` — питает трекинг установки для anti-crystal/`AutoExplosion`/`CrystalAura`. |
| `world.MixinClientWorld` | `ClientWorld#addEntity` | Вызывает `EventEntitySpawn(entity)` на `TAIL` при добавлении сущности в клиентский мир (модули реагируют на спавн — аура/ESP). |
| `world.MixinAbstractBlockState` | `AbstractBlock$AbstractBlockState#getCollisionShape` | При включённом модуле `phase` схлопывает `VoxelShape` коллизии в вырожденную линию по Y, убирая XZ-коллизию (Phase/NoClip по горизонтали). |
| `world.MixinAbstractClientPlayerEntity` | `AbstractClientPlayerEntity#getSkinTextures` | Кроме `legitMode`, для себя и друзей подменяет текстуры `ResourceProvider.CUSTOM_CAPE` и `CUSTOM_ELYTRA` (кастомные плащ/элитра). |
| `world.MixinItemEntityRenderer` | `ItemEntityRenderer` (`updateRenderState`/`render`) | Модуль `ItemPhysic`: в режиме «Обычная» вращает выпавшие предметы плоско/крутящимися по состоянию на земле; в режиме «2D» рендерит билбордом плашмя к камере. |

---

## `iface` — аксессоры и инвокеры (доступ к приватным членам MC)

В отличие от инъекторов выше, это **интерфейсы** с `@Accessor`/`@Invoker`. Они не меняют поведение игры, а открывают приватные поля и методы Minecraft остальному коду клиента.

| Миксин (интерфейс) | Целевой класс MC | Что открывает |
|---|---|---|
| `iface.ScreenAccessor` | `Screen` | `@Accessor` к приватным спискам виджетов `drawables`/`children`/`selectables` (используется `MultiplayerScreenMixin` для регистрации кнопки прокси). |
| `iface.HandledScreenAccessor` | `HandledScreen` | `@Accessor` к `focusedSlot` (логика инвентаря / item-scroller). |
| `iface.GameRendererAccessor` | `GameRenderer` | `@Invoker` к `getFov` (модули читают вычисленный FOV — для зума/aspect-математики). |
| `iface.ClientPlayerInteractionManagerAccessor` | `ClientPlayerInteractionManager` | `@Invoker` к `syncSelectedSlot` (принудительная синхронизация слота хотбара, авто-модули инвентаря). |
| `iface.MixinLivingEntityAccessor` | `LivingEntity` | `@Accessor` (сеттер) к `jumpingCooldown` — `setLastJumpCooldown(int)` (сброс кулдауна прыжка для bunny-hop/jump-читов). |
| `iface.ClientPlayerEntityAccessor` | `ClientPlayerEntity` | `@Accessor`/`@Invoker`: поля `lastYaw`/`lastPitch`/`lastSprinting` и инвокеры `isBlind`/`isWalking`/`canSprint` (логика движения/спринта). |
| `iface.MixinEntityAccessor` | `Entity` | `@Invoker` к `getRotationVector(pitch, yaw)` — используется в `MixinPlayerEntity` для расчёта кастомных векторов поворота. |
| `iface.ClientWorldAccessor` | `ClientWorld` | `@Accessor` к `pendingUpdateManager` (доступ к предсказанию/откату блоков для модулей взаимодействия с миром). |
| `iface.BossBarHudAccessor` | `BossBarHud` | `@Accessor` к `Map<UUID, ClientBossBar> bossBars` (чтение боссбаров — для HUD/ESP/эффектов). |
| `iface.MinecraftClientAccessor` | `MinecraftClient` | `@Accessor`: мутабельный сеттер `session` (смена альта/аккаунта) и get/set `itemUseCooldown` (манипуляция кулдауном использования). |
| `iface.ItemCooldownManagerAccessor` | `ItemCooldownManager` | `@Accessor` к карте `entries` и полю `tick` (HUD кулдаунов / логика быстрого использования). |
| `iface.ItemCooldownEntryAccessor` | `ItemCooldownManager$Entry` (внутренний) | `@Accessor` к `endTick` (чтение оставшихся тиков кулдауна). |

---

## Ключевые сквозные механики

- **Rotation spoofing (silent aim).** `MixinClientPlayerEntity` сохраняет `preYaw`/`prePitch`, применяет yaw/pitch из `EventMotion` к пакету движения, затем восстанавливает видимый клиенту поворот после `sendMovementPackets`. Дополнительно silent aim реализован на уровне `findCrosshairTarget` (`MixinGameRenderer`) — повторный рейкаст под углами модуля вместо реального поворота игрока.
- **Шина событий — главный выход инъекторов.** Большинство миксинов конструируют подкласс `Event` и вызывают `Event.call(...)`, развязывая хуки и фичи: `EventPacket`, `EventMotion`, `EventUpdate`, `EventRender2D/3D`, `EventAttack`, `EventFog`, `EventSprint`, `EventKeyBoard`, `EventMouse`, `EventPlayerTravel`, `EventPlayerRender`, `EventEntitySpawn`, `EventObsidianPlace`, `EventNoSlow`.
- **Анти-детект через `legitMode`.** `ClientManager.legitMode` коротко замыкает брендинг, парсинг команд, кастомное меню, оверлей статус-эффектов, кастомные плащ/элитра и подмену ника — клиент маскируется под ванилу при «legit-режиме».
- **NoSlow** реализован через redirect `PUTFIELD` на `Input.movementSideways`/`movementForward` и `setSprinting`, под защитой `EventNoSlow`.
- **Phase/NoClip** реализован схлопыванием формы коллизии в линию нулевой ширины по Y в `MixinAbstractBlockState`.

## Замечания и подводные камни

- `MixinKeyBoard` и `MixinMouse` создают **новый** экземпляр `new ExosWare().keyPress(...)` на каждое событие вместо `ExosWare.getInstance()`.
- Мышиные бинды кодируются смещённым keycode: `keyPress(-100 + button)`.
- `client.RenderTickCounterDynamicAccessor` назван как аксессор, но фактически является инъектором Timer-чита.
- `MixinFixRw` отменяет `removeScoreHolderFromTeam` безусловно, без тоглов.
- Захардкоженный промо-сервер `mc.furryhvh.ru` внедряется в список серверов и не сохраняется на диск.
- Множество сабтоглов `noRender`/`noPush` ключуются по русским строкам через `mods.get("...")` — опечатка в строке молча отключит фичу.
- В `MixinGameRenderer#onFindCrosshairTarget` в ветке `SelfTrap` pitch читается из `crystalAura.rotate.y` (копипаст-баг, а не `selfTrap.rotate.y`).
