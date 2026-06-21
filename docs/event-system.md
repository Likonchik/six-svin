# Событийная система

Событийная система ExosWare (`ru.levin.events`) — это минималистичная синхронная шина событий. Она связывает хуки движка Minecraft 1.21.4 (реализованные в виде миксинов) с модулями клиента (`Function`). Регистрации слушателей нет: концепция предельно проста — миксин конструирует объект конкретного события, вызывает статический метод `Event.call(...)`, и это событие рассылается **каждому** включённому модулю, а затем `SyncManager`. Модули фильтруют интересующие их события сами, через проверку `instanceof` в своём методе `onEvent(Event)`.

Объекты событий — это изменяемые (mutable) структуры, которые служат одновременно входными и выходными параметрами: модуль может прочитать поля, перезаписать их (например, `yaw`/`pitch` у `EventMotion`) или отменить событие через флаг `isCancel`. После возврата управления хук-миксин читает (возможно, изменённое) состояние события обратно и применяет его к движку.

## Базовый класс `Event`

Файл: `src/main/java/ru/levin/events/Event.java`

```java
public class Event implements IMinecraft {

    public boolean isCancel;

    public boolean isCancel() { return isCancel; }
    public void setCancel(boolean cancel) { this.isCancel = cancel; }

    public static void call(final Event event) {
        if (mc.player == null || mc.world == null || event.isCancel()) {
            return;
        }
        if (!ClientManager.legitMode) {
            for (final Function module : Manager.FUNCTION_MANAGER.getFunctions()) {
                if (module.isState()) {
                    module.onEvent(event);
                }
            }
            Manager.SYNC_MANAGER.onEvent(event);
        }
    }
}
```

`Event` несёт единственное состояние базового уровня — флаг `isCancel` (геттер `isCancel()`, сеттер `setCancel(boolean)`). Класс реализует интерфейс `IMinecraft` исключительно ради статической ссылки `mc` (`MinecraftClient.getInstance()`), используемой в `call()`.

Все конкретные события наследуются от `Event` и добавляют свои поля.

## Диспетчеризация `Event.call()`

`Event.call(Event event)` — это вся логика рассылки. Порядок проверок и действий:

1. **Null-проверки и ранний отказ.** Если `mc.player == null`, `mc.world == null`, или событие уже отменено (`event.isCancel()`), метод молча возвращается. Следствие: на экране меню / до загрузки мира **никакие события не диспетчеризуются**.
2. **Гейт `legitMode` (kill-switch).** Если `ClientManager.legitMode == true`, весь блок рассылки пропускается — ни один модуль и `SYNC_MANAGER` не получают событие. Это панический «анхук»-режим анти-детекта: когда он включён, клиент перестаёт реагировать на что-либо и выглядит неактивным во время проверки персонала (см. `UnHookScreen`, `ru.levin.modules.misc.UnHook`, обработку `unHookKey` в `ExosWare.keyPress`). По умолчанию `legitMode = false` (`ru/levin/manager/ClientManager.java:35`).
3. **Перебор включённых модулей.** В цикле по `Manager.FUNCTION_MANAGER.getFunctions()` для каждого модуля, у которого `isState() == true`, вызывается `module.onEvent(event)`. Порядок вызова — это порядок, в котором `FunctionManager` возвращает модули (приоритетов нет).
4. **Уведомление `SyncManager`.** После всех модулей вызывается `Manager.SYNC_MANAGER.onEvent(event)` — **всегда последним**. Поэтому `SyncManager` обновляет свои кеши (сущности, игроки, ротация) уже после того, как модули в рамках того же вызова запросили ротацию.

> **Важно.** `SyncManager.onEvent(...)` обрабатывает только часть событий через `switch` по подтипу: `EventUpdate` (обновление кешей + `ROTATION.onUpdate()`), `EventMotion` (применение перезаписи ротации, запись визуального yaw/pitch), `EventPlayerRender` (запись сглаженных визуальных углов) и `EventPacket` (вычисление TPS из `WorldTimeUpdateS2CPacket`). Остальные события до него доходят, но игнорируются.

### Особенности диспетчеризации

- **Нет регистрации и приоритетов.** Каждый модуль получает каждое событие и фильтрует его сам через `instanceof`. Порядок зависит только от `FunctionManager`.
- **Свежий объект на каждый хук.** События аллоцируются заново при каждом срабатывании хука (`EventRender2D`/`EventRender3D` — каждый кадр, `EventMotion`/`EventUpdate` — каждый тик, `EventPlayerRender` — на каждую сущность за кадр). Пулинга нет — это создаёт нагрузку аллокатором.
- **Две идиомы отмены.** (а) хук проверяет `event.isCancel()` и затем делает `ci.cancel()` (например, `EventMotion`); (б) «пустое» событие, чей флаг `isCancel` — единственный сигнал, возвращаемый в `@Redirect`/`@ModifyExpressionValue` миксина (например, `EventNoSlow`, `EventSprint`).
- **Мутация как API.** Контракт (какие поля можно писать, учитывается ли `setCancel`) нигде не формализован и определяется только тем, какие поля производящий миксин читает обратно.

## Каталог конкретных событий

Всего 19 классов событий. **17 активно вызываются**, а `EventUpdatePlayer` и `EventCooldown` определены, но **никогда не вызываются** (мёртвый/зарезервированный код). Источник — почти всегда миксин из `ru.levin.mixin.*`; исключения отмечены отдельно.

| Событие | Категория | Когда вызывается / источник | Что несёт (поля) |
|---|---|---|---|
| `EventUpdate` | tick | HEAD `ClientPlayerEntity.tick` — `MixinClientPlayerEntity` (`onTickHead`, ~стр. 41). Потактовый «пульс». | — (пустой класс) |
| `EventUpdatePlayer` | tick | **Не вызывается.** Пустой класс-маркер, мёртвый код. | — |
| `EventMotion` | move | HEAD `ClientPlayerEntity.sendMovementPackets` — `MixinClientPlayerEntity` (~стр. 49–50, cancellable). После рассылки миксин перезаписывает yaw/pitch исходящего пакета значениями события (спуф ротации), затем восстанавливает реальные углы (silent rotations). | `x, y, z` (double), `yaw, pitch` (float), `onGround` (boolean) — все изменяемые; есть конструктор только для (yaw, pitch) |
| `EventNoSlow` | move | Внутри `tickMovement` через `checkNoSlowCancel` в `@Redirect` на `Input.movementSideways`/`movementForward` и `@Redirect setSprinting` — `MixinClientPlayerEntity` (~стр. 76–77). Если отменено — vanilla-замедление / `setSprinting` пропускается (реализация NoSlow). | — (полезная нагрузка — только флаг `isCancel`) |
| `EventEntitySpawn` | move/world | TAIL `ClientWorld.addEntity` — `MixinClientWorld` (~стр. 16). Реакция на появление новой сущности. | `entity` (final `Entity`) |
| `EventPacket` | network | `MixinClientConnection` (`player`-пакет): тип `RECEIVE` на входящем пакете (~стр. 20), тип `SEND` на исходящем (~стр. 30). Пропускает SEND-событие, если `NetworkUtils.isSendingSilent()`. Сетевой «хребет» клиента. | `packet` (изменяемый `Packet`, можно подменить через `setPacket`), `packetType` (enum `SEND`/`RECEIVE`); хелперы `isSendPacket()`/`isReceivePacket()` |
| `EventAttack` | combat | `MixinAttackPlayer` (`ClientPlayerInteractionManager.attackEntity`, ~стр. 28), после отправки пакета атаки. | `attacker` (final `PlayerEntity`), `target` (final `Entity`) — только чтение |
| `EventCooldown` | combat | **Не вызывается.** `@Data`, поля `itemStack`/`cooldown`, мёртвый/зарезервированный код. | `Item itemStack`, `float cooldown` |
| `EventSprint` | move | Конструируется в двух `@ModifyExpressionValue`-хуках `tickMovement` — `MixinClientPlayerEntity` (~стр. 103, 110). **`hookSprintStart` строит событие, но НЕ вызывает `Event.call()`** (просто эхо). `hookSprintStop` (~стр. 108–111) вызывает `Event.call()` и позволяет модулям переопределить `sprinting`. Диспетчеризация непоследовательна. | `sprinting` (boolean) |
| `EventPlayerTravel` | move | `MixinPlayerEntity.travel`, дважды: `pre = true` перед travel (~стр. 40), `pre = false` после (~стр. 52). | `mVec` (`Vec3d`), `pre` (boolean — фаза); геттеры `getmVec()`, `isPre()` |
| `EventKey` | input | `ExosWare.java` (~стр. 118) при нажатии клавиши (`processedKey`). Управляет ClickGUI и биндами модулей. Рядом (~стр. 120) — обработка `unHookKey` + `legitMode`. | `key` (int) |
| `EventKeyBoard` | input | `MixinKeyboardInput.tick` (~стр. 34). Миксин полностью заменяет тик движения: считает forward/strafe, вызывает событие, даёт модулям переопределить движение, затем пересобирает `PlayerInput` и отменяет vanilla. | `movementForward, movementStrafe` (float), `jump, sneak, sprint` (boolean) — все изменяемые |
| `EventMouse` | input | `MixinMouse.onMouseButton` (~стр. 24) при событии кнопки мыши. | `button` (int, изменяемый) |
| `EventRender2D` | render | `MixinInGameHud.render` (~стр. 32), во время отрисовки HUD (в окружающем миксине — только при `!legitMode`). Для рисования HUD/2D-ESP. | `DrawContext`, `MatrixStack`, `RenderTickCounter deltatick` |
| `EventRender3D` | render | `MixinGameRenderer` (конструируется ~стр. 59, `Event.call` ~стр. 73), в `renderWorld`. Отрисовка в мировом пространстве (ESP/трейсеры/нейметеги). | `MatrixStack`, `RenderTickCounter deltatick` |
| `EventPlayerRender` | render | `MixinLivingEntityRenderer` (~стр. 30), на отрисовку каждой живой сущности. Конструктор снимает снимок `headYaw`/`prevHeadYaw`/`pitch`/`prevPitch`/`bodyYaw`/`prevBodyYaw`; модули переписывают их (спуф поворота тела/головы на рендер-модели). | `livingEntity` (final); изменяемые `prevYaw, yaw, prevPitch, pitch, prevBodyYaw, bodyYaw` (`@Data`) |
| `EventHeldItemRenderer` | render | **Не из миксина** — вызывается самим модулем `SwingAnimations` (~стр. 173 и 268). Кастомизация анимации предмета в руке. | `hand` (`Hand`), `item` (`ItemStack`), `ep` (float — equipProgress), `stack` (`MatrixStack`) — все final |
| `EventFog` | world/render | `MixinBackgroundRenderer` (~стр. 32). Модули ставят `modified = true` и переопределяют значения, чтобы перекрасить/отключить туман. | `modified` (boolean), `r, g, b, alpha` (float), `start, end` (float), `shape` (`FogShape`) — все public |
| `EventObsidianPlace` | world | `MixinBlock.onPlaced` (~стр. 23, под условием) при установке блока обсидиана. Используется логикой анти-краста / hole-ESP (`AutoExplosion`, `CrystalAura`). | `block` (final `Block`), `pos` (final `BlockPos`) |

## Поток данных (типичный кадр/тик)

1. **Ввод.** Миксины `MixinKeyBoard`/`MixinMouse` ловят ввод → `ExosWare.keyPress` / `EventKeyBoard` / `EventMouse`.
2. **Тик.** `MixinClientPlayerEntity` вызывает `EventUpdate`, затем `EventMotion`; `MixinPlayerEntity` вызывает `EventPlayerTravel`.
3. **Реакция модулей.** Боевые модули (`AttackAura`, `CrystalAura` и др.) выставляют состояние `Manager.ROTATION` (`RotationController`); рендер- и движение-миксины читают его обратно, реализуя silent aim / strafe.
4. **Сеть.** Сетевые миксины вызывают `EventPacket` на каждый отправляемый/принимаемый пакет (отменяемый, с учётом silent-флага).
5. **Рендер.** Рендер-миксины вызывают `EventRender2D`/`EventRender3D`, чтобы модули рисовали HUD/ESP.

Всё это срабатывает только при `!legitMode` и при наличии загруженного мира/игрока (см. гейты в `Event.call()`).

## Связи и кросс-ссылки

- **`Event.call()` → `Manager.FUNCTION_MANAGER`** (перебор `getFunctions()`) и **`Manager.SYNC_MANAGER.onEvent(event)`**.
- **`ClientManager.legitMode`** — глобальный kill-switch, читаемый `Event.call`; выставляется в `true` из `UnHookScreen` / по `unHookKey`. Многие миксины дополнительно гейтят визуальные изменения по `!legitMode`, чтобы скрыть клиент при анхуке.
- **`ru.levin.modules.Function.onEvent(Event)`** — приёмник на стороне модуля; модули делают `instanceof`-проверку конкретного типа.
- **Производители событий (миксины `ru.levin.mixin.*`):** `MixinClientPlayerEntity`, `MixinClientConnection`, `MixinMouse`, `MixinKeyboardInput`, `MixinLivingEntityRenderer`, `MixinGameRenderer`, `MixinInGameHud`, `MixinBackgroundRenderer`, `MixinAttackPlayer`, `MixinClientWorld`, `MixinBlock`, `MixinPlayerEntity`.
- **Производители вне миксинов:** `ExosWare.java` вызывает `EventKey` из колбэка клавиатуры; модуль `SwingAnimations` сам вызывает `EventHeldItemRenderer` (связь «модуль-как-производитель»).

## Структура пакета

```
ru.levin.events
├── Event.java                         // базовый класс + статический call()
└── impl
    ├── EventPacket.java               // network (SEND/RECEIVE)
    ├── EventUpdate.java               // tick
    ├── EventUpdatePlayer.java         // НЕ вызывается (мёртвый код)
    ├── input
    │   ├── EventKey.java
    │   ├── EventKeyBoard.java
    │   └── EventMouse.java
    ├── move
    │   ├── EventEntitySpawn.java
    │   ├── EventMotion.java
    │   └── EventNoSlow.java
    ├── player
    │   ├── EventAttack.java
    │   ├── EventCooldown.java          // НЕ вызывается (мёртвый код)
    │   ├── EventPlayerTravel.java
    │   └── EventSprint.java
    ├── render
    │   ├── EventHeldItemRenderer.java  // вызывается из модуля SwingAnimations
    │   ├── EventPlayerRender.java
    │   ├── EventRender2D.java
    │   └── EventRender3D.java
    └── world
        ├── EventFog.java
        └── EventObsidianPlace.java
```

## Подводные камни

- `Event.call` молча выходит при `mc.player == null` **или** `mc.world == null` — события не идут на титульном экране / до загрузки мира.
- `ClientManager.legitMode` — жёсткий kill-switch: при `true` пропускается и цикл по модулям, и `SYNC_MANAGER`, поэтому **ни один чит не реагирует ни на что**. Это механизм анти-детекта («паника»/анхук).
- Нет регистрации слушателей и приоритетов: порядок — это порядок `FUNCTION_MANAGER.getFunctions()`; событие получают только модули с `isState() == true`; `SYNC_MANAGER` всегда последний.
- `EventUpdatePlayer` и `EventCooldown` полностью определены, но **не имеют ни одной точки вызова** — мёртвый/зарезервированный код.
- `EventSprint` непоследователен: хук старта спринта строит событие, но не вызывает `Event.call()` (модули не влияют на старт спринта); диспетчеризуется только хук остановки спринта.
- Мутация-как-API: у многих событий нет поведения, кроме как быть изменяемой структурой; контракт (какие поля писать, учитывается ли `setCancel`) неявный и обеспечивается только тем, что читает обратно производящий миксин.
- Тихая ротация (`EventNoSlow`/`EventMotion`) опирается на восстановление миксином `preYaw`/`prePitch` после `sendMovementPackets` (`MixinClientPlayerEntity` ~стр. 60–66) — классический приём анти-детекта (углы только на сервер).
- Объекты событий аллоцируются на каждом хуке без пулинга — заметная нагрузка аллокатором при высокой частоте (рендер/тик/на сущность).
- `EventHeldItemRenderer` вызывается **не из миксина**, а из самого модуля `SwingAnimations` — нетипичная связь, которую легко упустить при поиске источников событий.
