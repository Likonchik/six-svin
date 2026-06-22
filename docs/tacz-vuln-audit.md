# ИТОГОВЫЙ ОТЧЁТ: Аудит client-trust уязвимостей TACZ (1.21.1 / NeoForge) → тест-векторы анти-чита OneTap-MC

> Сгенерировано многоагентным аудитом (7 охотников → состязательная верификация каждой находки → синтез). Кандидатов: 50, подтверждено: 11, отклонено: 39.

## 1. Резюме

Проведён состязательно-верифицированный аудит сетевого и боевого среза TACZ (Timeless and Classics Zero). Подтверждено **10 реальных уязвимостей** класса «сервер доверяет клиенту»; отклонено **39 кандидатов** как не эксплуатируемых при дефолтном конфиге (штатная механика, корректная серверная валидация, либо S2C-направление).

**По классам уязвимостей:**
- **fire-rate-bypass** (3 finding-а, по сути один корневой механизм): кулдаун стрельбы считается по дельте двух client-controlled `timestamp`, ограничен только окном `NETWORK_V`.
- **spoof-aim-state** (1): bool `isAim` спуфит серверную точность ADS без визуала.
- **hit-anywhere / desync** (1): лаг-компенсация хитбокса отматывается по пингу самого стрелка.
- **no-ownership-check** (4): destroyGlass без BreakEvent; remote-craft; instant-refit; laser-NBT spoof.
- **packet-flood** (1): reload/bolt/cancel без rate-limit (амплификация бродкастов).
- **info-disclosure** (1, S2C): точное боевое состояние всех сущностей раздаётся клиенту (`SyncMode.ALL`).

**Топ-риски (по влиянию на честную игру):**
1. **`GunNoSpread` через spoof-aim** (`severity=high`) — серверно-авторитетная near-zero точность без скоупа. **Уже частично реализован** в OneTap (`GunNoSpread.java:128` шлёт `ClientMessagePlayerAim(true)`).
2. **Fire-rate burst-bypass через timestamp** (`severity=high→medium`) — обход RPM в пределах окна ~700 мс; при `ServerShootCooldownCheck=false` (non-default) — полный RapidFire.
3. **Лаг-комп desync хитбокса** (`severity=medium`) — fake-lag + ping-aware backtrack по реальной истории жертвы.

**Важная архитектурная константа:** направление выстрела сервер берёт из **реального серверного `entity.getXRot()/getYRot()`**, НЕ из пакета (`ClientMessagePlayerShoot` несёт только `{VAR_LONG timestamp, FLOAT chargeProgress}`). Поэтому все эти векторы — про **rate / accuracy / hitreg-timing**, а НЕ про aim-injection. Aim делается ванильным move-пакетом через `EventMotion`/`Manager.ROTATION` (silent-aim), как уже устроено в OneTap.

---

## 2. Таблица уязвимостей

| # | Название | Класс | Severity | Confidence | Файл:строка | Гейт конфигом (дефолт) |
|---|----------|-------|----------|-----------|-------------|------------------------|
| 1 | Spoof `isAim` → серверная ADS-точность без визуала | spoof-aim-state | **high** | high | `ClientMessagePlayerAim.java:37`; `LivingEntityAim.java:30`; `InaccuracyType.java:49` | нет |
| 2 | Cooldown по client-timestamp → burst fire-rate bypass | fire-rate-bypass | **high→medium** | high | `LivingEntityShoot.java:248,152,80-81` | да: `SERVER_SHOOT_COOLDOWN_V`+`NETWORK_V` (оба true) |
| 3 | Лаг-комп хитбокса по пингу стрелка → desync/backtrack | hit-anywhere | **high→medium** | medium | `HitboxHelper.java:87-90,109`; `EntityUtil.java:27` | да: `SERVER_HITBOX_LATENCY_FIX=true`, `OFFSET=3` |
| 4 | DestroyGlass без прав/BreakEvent | no-ownership-check | **high→medium** | high | `DestroyGlassBlock.java:24-30` | да: `AmmoConfig.DESTROY_GLASS=true` |
| 5 | `data.shootTimestamp` пишется raw из пакета (root-cause #2) | fire-rate-bypass | **high→low** | high | `LivingEntityShoot.java:152`; `ShooterDataHolder.java:21` | нет (запись); проверки гейтятся как в #2 |
| 6 | Reload/Bolt/Cancel без rate-limit | packet-flood | **medium→low** | high | `ClientMessagePlayerReloadGun.java:18`; `NetworkHandler.java:36` | нет |
| 7 | RemoteCraft: `stillValid()=isAlive()`, без дистанции | no-ownership-check | **medium→low** | high | `GunSmithTableMenu.java:61-63`; `ClientMessageCraft.java:40` | нет (craft); фильтр — `ENABLE_TABLE_FILTER=true` |
| 8 | InstantRefit по произвольным slot-индексам без меню | no-ownership-check | **low** | high | `ClientMessageRefitGun.java:46-78` | нет |
| 9 | LaserColor: NBT-запись цвета без серверного `canEdit()` | no-ownership-check | **low** | high | `ClientMessageLaserColor.java:75-99` | нет |
| 10 | `ServerMessageUpdateEntityData` раскрывает боевое состояние врагов (S2C) | info-disclosure | **low** | high | `ModSyncedEntityData.java:14-66`; `ServerMessageUpdateEntityData.java:60` | нет |

---

## 3. Детальные блоки (по убыванию severity)

### #1 — Spoof `isAim` → серверно-авторитетная точность ADS без визуала `[HIGH]`

**Что доверяет сервер.** Bool `isAim` из `ClientMessagePlayerAim` пишется в `data.isAiming` без какой-либо валидации (нет проверки «держит ли ствол», rate-limit, состояния, дистанции). Через серверный тик `aimingProgress` рампится до `1.0f`, после чего `InaccuracyType.getInaccuracyType()` возвращает `AIM`, и `shootOnce()` кормит near-zero spread (`AIM=0.15` против `STAND=5.0`/`MOVE=5.75`) в реальный raytrace `EntityKineticBullet`.

**Доказательство.**
- `ClientMessagePlayerAim.java:18-21` — STREAM_CODEC = `composite(ByteBufCodecs.BOOL, msg->msg.isAim)` — единственный client-bool.
- `ClientMessagePlayerAim.java:34-39` — `handle()`: `IGunOperator.fromLivingEntity(entity).aim(message.isAim)` — ноль проверок.
- `LivingEntityAim.java:29-31` — `data.isAiming = isAim;` (чистое присваивание).
- `LivingEntityAim.java:77-89` — `tickAimingProgress`: `aimingProgress` рампится по `aimTime` и клампится к `1.0`.
- `InaccuracyType.java:47-50` — `AIM` только при `aimingProgress == 1.0f`.
- Дефолты `InaccuracyType.getDefaultInaccuracy:65-72`: AIM=0.15, STAND=5.0, MOVE=5.75 (~33-38× редукция спреда).

**Пошаговый эксплойт.**
1. Периодически слать `ClientMessagePlayerAim(true)` (`playToServer`), НЕ активируя клиентский визуальный ADS (без зума/FOV из `LocalPlayerAim`).
2. Подождать `aimTime` (~0.2-0.5 c) — `aimingProgress` достигает ровно `1.0f` (кламп гарантирует точное float-равенство).
3. Каждый последующий выстрел получает AIM-spread на серверном raytrace.
- **Side-effect:** серверный `isAiming=true` форсит `setSprinting(false)` (`LivingEntityAim.java:96`) и aim-slowdown (`LivingEntitySpeedModifier.java:69`). Для стелса — слать `aim=true` только в окне ~`aimTime` перед залпом, потом `false`.

**Привязка к OneTap-MC.** **Расширение существующего `GunNoSpread`** — уже реализовано: `GunNoSpread.java:128` шлёт `new ClientMessagePlayerAim(aim)`. Режимы в коде («Легит» = реальный пакет → серверный `aimingProgress=1.0`; «Инстант» = mixin `MixinInaccuracyType.java:29-30` форсит `AIM` локально). Для теста AC ключевой — режим «Легит»: один легит-shaped ванильный пакет вместо мода-mixin. Обратить внимание: при включённом «Легит» не дёргать `LocalPlayerAim` (визуал), иначе теряется стелс-эффект; уже учтено в `MixinMouseHandlerNoSens` (`suppressAimSens`).

**Сигнатура для АЧ.** Инвариант: `isAiming==true` должен коррелировать с клиентским ADS-вводом (нажатие ПКМ/прицеливание). Признаки: (а) `aim=true` держится, но игрок не входит в визуальный aim-стейт (нет соответствующего поведения сенса/движения); (б) `aimingProgress==1.0` при движении/прыжке с полной hipfire-скоростью передвижения (нет aim-slowdown паттерна); (в) частые `aim true→false` тоггл-всплески синхронно с выстрелами (toggle строго перед каждым залпом).

---

### #2 — Cooldown по client-controlled timestamp → burst fire-rate bypass `[HIGH→MEDIUM]`

**Что доверяет сервер.** Сервер не хранит независимый shoot-clock. `interval = timestamp - data.shootTimestamp` (обе величины клиентские: `data.shootTimestamp` = предыдущий присланный timestamp). Реальное время сверяется ТОЛЬКО окном `NETWORK_V`: `alpha = System.currentTimeMillis() - data.baseTimestamp - timestamp ∈ [-300, 300+2·tickTime]` (~700 мс ширины).

**Доказательство** (подтверждено чтением `LivingEntityShoot.java`):
- `:248` `long interval = timestamp - data.shootTimestamp;`
- `:258-260` `coolDown = getShootInterval(...) - interval; coolDown -= 5; return Math.max(coolDown, 0)` — проходит при `interval >= shootInterval-5`.
- `:152` `data.shootTimestamp = timestamp;` — пишется raw, только при SUCCESS (после обеих проверок).
- `:80-81` единственный реал-тайм-якорь: `alpha` в окне `[-300, 300+tickTime·2]`.
- `:65,76` обе проверки гейтятся `SyncConfig.SERVER_SHOOT_COOLDOWN_V` и `SERVER_SHOOT_NETWORK_V` — **оба дефолт `true`** (`SyncConfig.java:101,95`).
- Дрейф: каждый «сжатый» выстрел уводит `alpha` к -300; бюджет ~700 мс одноразовый, восстанавливается реальным временем. `data.baseTimestamp` ставится сервером на resync (`ClientMessageSyncBaseTimestamp.handle` → `System.currentTimeMillis()`), сам resync клиент инициировать может, но он НЕ сбрасывает `data.shootTimestamp` → для атакующего скорее вредит.

**Пошаговый эксплойт (при дефолте — bounded burst).**
1. Вести собственный счётчик «серверного» относительного `timestamp`.
2. Каждый fake-shot слать `ClientMessagePlayerShoot{timestamp = lastSent + (shootInterval - 5), chargeProgress = 0f}` независимо от wall-clock, держа `alpha` в окне.
3. Front-load: первый выстрел — с `timestamp ≈ serverRelativeNow - 300`, чтобы максимизировать буфер. Дамп ~`700мс / shootInterval` выстрелов (~7 для 600 RPM / 100 мс), затем дроп до легит-RPM или пауза на восстановление `alpha`.
4. **Только при `ServerShootCooldownCheck=false` (non-default, в коде прямое предупреждение `SyncConfig.java:97-100)`** — полностью неограниченный RapidFire.
- Патроны списываются за каждый выстрел (`:138-142`) → это RapidFire/DPS-burst, НЕ infinite-ammo. Гейтится reload/draw/bolt/sprint/overheat.

**Привязка к OneTap-MC.** **Новый модуль `GunRapidFire` (Combat)** либо расширение `GunNoSpread`/`NoRecoil`. Перехватывать отправку `ClientMessagePlayerShoot`, подменяя `timestamp`. Нужен трекинг «серверного now»: парсить периодический `ServerMessageSyncBaseTimestamp`/оценивать дрейф. Направление приходит из реального серверного поворота → комбинировать с `GunAimbot` (silent-aim через `EventMotion`/`Manager.ROTATION` уже наводит на цель на синхронном тике). Вариант B (self-resync spam через `ClientMessageSyncBaseTimestamp`) — НЕ работает для устойчивого RapidFire (см. отклонённые), но полезен как отдельный тест-вектор аномального resync.

**Сигнатура для АЧ.** Инвариант: средний интервал между принятыми shoot-пакетами должен ≈ реальному прошедшему времени. Признаки: (а) устойчивый отрицательный дрейф `alpha` к нижней границе -300; (б) повторяющиеся `NETWORK_FAIL` + форс-resync (`ServerMessageSyncBaseTimestamp` рассылается часто); (в) серия выстрелов, где дельта присланных timestamp << реальной дельты прихода пакетов (`timestamp` обгоняет server-receive-time).

---

### #3 — Лаг-комп хитбокса по пингу стрелка → desync/backtrack `[HIGH→MEDIUM]`

**Что доверяет сервер.** Сервер использует `serverPlayerOwner.connection.latency()` стрелка как меру, насколько отмотать **реальную историю** позиции жертвы (`ping = floor(latency/1000·20+0.5)`, clamp `[0, boxes.size-1]`, макс ~20 тиков / 1 c). Дополнительно сдвигает AABB на `velocity·-5` и `·OFFSET(=3)` (масштабируется скоростью ЖЕРТВЫ — для стоящей цели ≈ 0). `.inflate(1.0)` — только broad-phase кандидатов, НЕ clip-тест.

**Доказательство.**
- `HitboxHelper.java:87-90` `int ping = Mth.floor((serverPlayerOwner.connection.latency()/1000.0)·20.0+0.5); boundingBox = getBoundingBox(player, ping);`
- `HitboxHelper.java:109` `boundingBox = boundingBox.move(velocity.multiply(-5,-5,-5));`
- `HitboxHelper.java:68` `index = Mth.clamp(ping, 0, boxes.size()-1)` — cap ≤20 тиков; `:23` `SAVE_TICK=20`.
- `EntityUtil.java:27,59` `.inflate(1.0)` — только `getEntities()` broad-phase; реальный hit-тест — точный `boundingBox.clip()` (`EntityUtil.java:80-82`).
- `OtherConfig.java:35` `SERVER_HITBOX_LATENCY_FIX=true`; `:32` `OFFSET=3`; `:38` `MAX_SAVE_MS=1000`.

**Опровергнутая часть (фиксирую для честности):** `.inflate(1.0)` НЕ расширяет хиттабельную зону; `-5·velocity`/`OFFSET·3` ≈ 0 для стоящей цели. Это НЕ hit-anywhere и НЕ статически раздутый хитбокс. Реальный остаток — fake-lag + ping-aware backtrack по движущейся цели.

**Пошаговый эксплойт.**
1. Искусственно раздуть собственный пинг (задержка keep-alive/move-пакетов; `latency()` меряется сервером, но смещается троттлингом исходящих).
2. Сервер отматывает хитбокс движущейся жертвы на `floor(latency/1000·20+0.5)` тиков в её реальное прошлое.
3. Silent-aim должен наводиться РОВНО в эту историческую позицию (+ velocity-shifted точка при больших `OFFSET`/`-5·velocity`).
4. Обычный `ClientMessagePlayerShoot` (направление = серверный yaw/pitch, уже выставленный silent-аимом).
- Ограничения: cap ~1 c / 20 тиков, только движущиеся цели, отмотка в ИСТИННЫЙ AABB (нельзя сквозь стены / по стоящей цели мимо тела).

**Привязка к OneTap-MC.** **Расширить `GunAimbot` backtrack** (уже имеет backtrack-логику) — синхронизировать выбор исторической позиции цели с ping-окном: целиться в позицию `floor(latency/1000·20+0.5)` тиков назад + velocity-shift, не в центр модели. **Новый Misc-модуль `FakeLag`** для управления окном отмотки (троттлинг исходящих move/keep-alive). Через `EventMotion`: silent-аим пишет `Manager.ROTATION` на тик, где предсказанная серверная rewind-позиция совпадает с историей.

**Сигнатура для АЧ.** Инвариант: попадание должно лежать в текущем (или near-текущем) хитбоксе жертвы. Признаки: (а) высокий/искусственно нестабильный пинг стрелка (bursty keep-alive RTT) при стабильном движении; (б) попадания по движущимся целям систематически в позицию-в-прошлом, коррелирующую с `ping·tick`; (в) клиент троттлит исходящие пакеты пачками (нерегулярный интервал move-пакетов).

---

### #4 — DestroyGlass без проверки прав / без BreakEvent `[HIGH→MEDIUM]`

**Что доверяет сервер.** По факту попадания серверной пули в стекло безусловно (только гейт `AmmoConfig.DESTROY_GLASS`) зовётся `level.destroyBlock(pos, false, ammo.getOwner())`. Нет проверки claim/region/permission, **не постится `BlockEvent.BreakEvent`** (vanilla `Level.destroyBlock` не идёт через player-break pipeline) → grief-protection, слушающий только `BreakEvent`, обходится.

**Доказательство.**
- `DestroyGlassBlock.java:24-30` (подтверждено дословно): `if (AmmoConfig.DESTROY_GLASS.get() && (StainedGlassBlock || TintedGlassBlock || StainedGlassPaneBlock || (TransparentBlock && instrument==HAT) || (IronBarsBlock && instrument==HAT))) { level.destroyBlock(pos, false, ammo.getOwner()); }`
- `AmmoConfig.java:38` `DESTROY_GLASS = builder.define("DestroyGlass", true)` — дефолт true (импорт `config.common.AmmoConfig`).
- Событие серверное: `EntityKineticBullet.java:486` постит `AmmoHitBlockEvent` (server-only), `:295` `onBulletTick` гейтит `!isClientSide()`.
- Только два consumer-а `AmmoHitBlockEvent` (`BellRing`, `DestroyGlassBlock`) — ни один не валидирует владение. Grep по `event/`: ноль `BreakEvent|canHarvest|mayInteract|isProtected|claim|region`.

**Покрытые блоки:** StainedGlass, TintedGlass, StainedGlassPane, любой `TransparentBlock`/`IronBarsBlock` с note-instrument `HAT` (чистое стекло и панели — `TransparentBlock`+HAT, тоже сносятся; железные прутья — да). Только стекло, не произвольные блоки → severity capped medium.

**Пошаговый эксплойт.** Навестись на стеклянную постройку (через `GunAimbot` visible-part aim), вести автоогонь. Каждое попадание легит-пули сносит блок без drop и без `BreakEvent`. Требуется один легитимно-валидированный выстрел (cooldown/ammo/reload проверяются в `LivingEntityShoot.shoot`).

**Привязка к OneTap-MC.** **Новый модуль `GlassBreaker`/`GriefGlass` (Combat/Misc)** — последовательное автонаведение `GunAimbot` по ближайшим destroyable-glass блокам в радиусе + `AutoReload` + автоогонь. Использует honest visible-part aim (направление = серверный поворот). Опционально привязать к `EventUpdate` для перебора целей-блоков.

**Сигнатура для АЧ.** Это нормальный server-side block update от реальной пули → НЕ детектится валидацией полей пакета. Только rate/pattern-эвристики: (а) серия `destroyBlock` стекла без соответствующих `BreakEvent`; (б) высокий темп уничтожения стекла по сетке, коррелирующий с автоогнём; (в) разрушение в чужих claim-зонах от пуль.

---

### #5 — `data.shootTimestamp` raw из пакета (корневая причина #2) `[HIGH→LOW]`

**Что доверяет сервер.** Не пересчитывает `shootTimestamp` от server-clock — сохраняет ровно клиентское значение, делая следующий `interval` чисто client-управляемым. **Это root-cause #2, а не отдельный эксплойт.**

**Доказательство.** `LivingEntityShoot.java:152` `data.shootTimestamp = timestamp;` (нет нормализации `= serverClock`); `ShooterDataHolder.java:21` `public long shootTimestamp = -1L;`. Другие записи — только init/reset и script-only `adjustShootInterval` (`ModernKineticGunScriptAPI.java:356`). При дефолте достижимый выигрыш маргинален: одноразовый burst ~300 мс + персистентный shave ~5 мс/выстрел (`coolDown - 5`, ~5% RPM на 600 RPM).

**Привязка к OneTap-MC.** Часть генератора корректных timestamp-пар внутри `GunRapidFire` (см. #2): вести собственный «серверный» timestamp, инкрементировать на `(shootInterval - 5)`, не давая упасть ниже `serverRelativeNow - 300`.

**Сигнатура для АЧ.** Та же, что #2 (дрейф `alpha`, NETWORK_FAIL). Дополнительно: серверу СТОИТ нормализовать `shootTimestamp` от собственных часов (фикс root-cause), тогда timestamp-spoof закрывается полностью — полезный инвариант для тестового детекта «server-clock vs client-delta».

---

### #6 — Reload/Bolt/Cancel без rate-limit `[MEDIUM→LOW]`

**Что доверяет сервер.** Принимает пустые (`StreamCodec.unit`) пакеты в любой момент без дебаунса/min-interval. Тяжёлый бродкаст `ServerMessageGunReload` гейтится внутренними стейт-проверками (НЕ state-thrash на каждый вызов), но цикл `reload→cancel→reload` даёт ограниченную S2C-амплификацию.

**Доказательство.**
- `ClientMessagePlayerReloadGun.java:18` / `BoltGun:18` / `CancelReload:18` — `STREAM_CODEC = StreamCodec.unit(INSTANCE)` (ноль полей).
- `NetworkHandler.java:36,37,50` — `playToServer(...::handle)` без throttle.
- Гейты: `LivingEntityReload.java:45` `if (reloadStateType.isReloading()) return;` (ДО бродкаста `:68`); `:49` shootCoolDown, `:53` drawCoolDown, `:57` isBolting, `:61` ammo, `:96` cancelReload no-op если не reloading. `LivingEntityBolt.java:50,65,69,73`.
- Grep по `network/`: ноль rate/throttle/debounce. Конфиг-гейта для этих хендлеров нет.
- Тайминги перезарядки серверо-авторитетны (`reloadTimestamp = System.currentTimeMillis()`, `tickReload`) → НЕТ instant-reload / infinite-ammo.

**Пошаговый эксплойт.** Слать цикл `ClientMessagePlayerReloadGun → ClientMessagePlayerCancelReload → reload` десятки раз/тик (с оружием в руке, вне shoot/draw-кулдауна) — каждый старт рассылает `ServerMessageGunReload` всем трекающим + гоняет Lua `start_reload`/`interrupt_reload` + работа на main-thread. Геймплейного преимущества нет.

**Привязка к OneTap-MC.** **Новый модуль `ReloadSpam`/`PacketFlood` (Misc)** или опция в `AutoReload`: «flood reload/cancel N циклов/тик» — чистый стресс-тест netty/AC. НЕ unfair-advantage.

**Сигнатура для АЧ.** Инвариант: reload/cancel-частота должна быть человеческой (≤ единиц/сек). Признаки: (а) >N reload/cancel-пакетов за тик от одного игрока; (б) повторяющийся паттерн `reload→cancel` без завершения перезарядки; (в) всплеск `ServerMessageGunReload`-бродкастов по наблюдателям одной сущности.

---

### #7 — RemoteCraft: `stillValid()=isAlive()`, без дистанции `[MEDIUM→LOW]`

**Что доверяет сервер.** Что `containerMenu` всё ещё валиден. `stillValid()` проверяет ТОЛЬКО `player.isAlive()` — нет проверки дистанции до блока станка (`openMenu` без `ContainerLevelAccess`).

**Доказательство.**
- `GunSmithTableMenu.java:61-63` `public boolean stillValid(Player player){ return player.isAlive(); }`
- `ClientMessageCraft.java:40` `if (containerMenu.containerId == message.menuId && containerMenu instanceof GunSmithTableMenu menu) { menu.doCraft(message.recipeId, entity); }` — STREAM_CODEC `{ResourceLocation recipeId, int menuId}`.
- `AbstractGunSmithTableBlock.java:46` `serverPlayer.openMenu(gunSmithTable, ...)` (overload без proximity-валидации).
- **Защита, ограничивающая impact:** `doCraft` (`:95-127`) реально списывает материалы (не creative); `getRecipe` (`:66-86`) фильтрует через `RecipeFilter` (`ENABLE_TABLE_FILTER=true`, `SyncConfig.java:92`) и проверяет принадлежность табам. → НЕ дюп, НЕ бесплатный крафт.

**Пошаговый эксплойт.** Открыть станок 1 раз → сохранить `menuId` → отойти/убежать → слать `ClientMessageCraft(recipeId, savedMenuId)` с любого расстояния/в движении. Ломается на смерти (`isAlive=false`) или закрытии контейнера (не слать ServerboundContainerClose — держать экран открытым/фоновым).

**Привязка к OneTap-MC.** **Новый модуль `RemoteCraft`/`AutoCraft` (Misc/Player):** запомнить `containerId` при открытии `GunSmithTableScreen`, слать `ClientMessageCraft` по списку `recipeId` с заданной частотой. QoL-эксплойт, не боевой/экономический.

**Сигнатура для АЧ.** Инвариант: craft возможен только при физической близости к станку. Признаки: (а) `ClientMessageCraft` при дистанции игрок↔block-станок > радиуса взаимодействия (~8 блоков); (б) craft без открытого экрана (нет соответствующего `ContainerOpen`); (в) craft в движении на большой скорости.

---

### #8 — InstantRefit по произвольным slot-индексам без меню `[LOW]`

**Что доверяет сервер.** Что игрок открыл refit-экран и `gunSlotIndex==selected`. Никакой проверки открытого меню/дистанции. НО тип навесного берётся из самого предмета (не из пакета) → спуф типа невозможен.

**Доказательство.**
- `ClientMessageRefitGun.java:50-51` `attachmentItem = inventory.getItem(message.attachmentSlotIndex); gunItem = inventory.getItem(message.gunSlotIndex);` — raw индексы.
- `:64` `AttachmentType realType = iAttachment.getType(attachmentItem);` (игнорирует `message.attachmentType`).
- `:52-53` gun обязан быть IGun; `:55` `hasAttachmentLock`; `:58/:328` `allowAttachment` re-check (`AbstractGunItem.java:285-294`, `AllowAttachmentTagMatcher.match`). `:69` старое навесное возвращается в слот (нет дюпа). `getItem` bounds-safe.

**Пошаговый эксплойт.** Без открытия `GunRefitScreen` слать `ClientMessageRefitGun(attachmentSlot, gunSlot, anyType)` для любой пары слотов своего инвентаря, в бою/на бегу — мгновенный refit. Ограничения: только свой инвентарь, нельзя подделать тип/дюпнуть, ноль влияния на hitreg/урон/патроны/кулдаун.

**Привязка к OneTap-MC.** **Новый модуль `InstantRefit` (Player)**, синергия с `FastAds`/`NoVisualAds`: свап прицела/глушителя по хоткею без UI.

**Сигнатура для АЧ.** Инвариант: refit только при открытом refit-меню. Признаки: `ClientMessageRefitGun` без предшествующего open-menu, или с `gunSlotIndex != inventory.selected`.

---

### #9 — LaserColor: NBT-запись цвета без серверного `canEdit()` `[LOW]`

**Что доверяет сервер.** Что `laserConfig.canEdit()` соблюдён (проверяется ТОЛЬКО на клиенте в `GunRefitScreen`). Сервер пишет цвет в NBT навесного/оружия без проверки. Косвенная защита владения — индексация своего инвентаря.

**Доказательство.**
- `ClientMessageLaserColor.java:78-79` `if (message.gunSlotIndex == -1) return;`; `:81-82` `player.getInventory().getItem(message.gunSlotIndex)`; `:85-96` цикл записи `setLaserColorToTag`/`setLaserColor` — нет серверного `canEdit()`.
- `LaserConfig.canEdit()` (`LaserConfig.java:46`) — в пакете `client.resource.pojo.display`, физически недоступен серверу; клиентские чеки лишь `GunRefitScreen.java:206,259`.
- `setAttachmentTag` пишет только если навесное реально установлено (`GunItemDataAccessor.java:259-263`). Эффект чисто косметический.
- **Побочно:** отрицательный `gunSlotIndex` (например -2, проходит мимо `==-1`) → `Inventory.getItem(-2)` → `IndexOutOfBoundsException` на серверном потоке (мелкий DoS/спам в лог).

**Пошаговый эксплойт.** Слать `ClientMessageLaserColor(colorMap, applyGunColor=true, gunColor=ARGB, gunSlotIndex=свой слот)` без открытия refit-экрана — записать произвольный цвет лазера, обходя клиентский `canEdit=false`.

**Привязка к OneTap-MC.** **Мелкая фича `LaserSpoof` (Render/Misc)** — кастомный цвет лазера на оружии, где сервер-пак запретил редактирование. Низкая ценность для AC.

**Сигнатура для АЧ.** Инвариант: laser-color только при открытом refit и `canEdit=true`. Признаки: `ClientMessageLaserColor` без open-menu; отрицательный `gunSlotIndex` (≠ -1) → паттерн краш-инъекции.

---

### #10 — `ServerMessageUpdateEntityData`: раскрытие боевого состояния врагов (S2C) `[LOW]`

**Что доверяет сервер.** N/a (это S2C, клиент не отправляет, спуфить нечего). Риск — **утечка**: `shoot_cool_down/reload_state/is_aiming/is_bolting/aiming_progress` (+ melee/draw cooldown, sprint_time) ВСЕХ трекаемых сущностей раздаются клиенту с `SyncMode.ALL`.

**Доказательство.**
- `ModSyncedEntityData.java:14-60` — 8 боевых ключей объявлены `SyncMode.ALL`; только `THROWABLE_USE_TICK` — `SELF_ONLY` (`:65`).
- `SyncedDataKey.java:73` `ALL=(tracking=true, self=true)`; `:95-97` `isTracking()`.
- `SyncedEntityDataEvent.java:100-102` безусловно рассылает dirty-записи `sendToPlayersTrackingEntity`; `:35-37` полный снапшот при `onStartTracking`.
- Аксессоры читаются для ЛЮБОЙ сущности: `LivingEntityMixin.java:48-93` (`getSynShootCoolDown`/`getSynReloadState`/`getSynIsBolting`). Конфиг-гейта на пути рассылки нет.

**Пошаговый эксплойт (пассивный).** У цели читать `IGunOperator.fromLivingEntity(target).getSynShootCoolDown()>0` / `.getSynReloadState().getStateType().isReloading()` / `.getSynIsBolting()` / `getSynIsAiming()` — бить/приоритизировать атаку, когда враг перезаряжается/в bolt/на кулдауне и не может ответить. Слать пакеты не нужно (данные приходят сами).

**Привязка к OneTap-MC.** **Расширение `GunAimbot`/`TargetESP`/`NameTags` (gun-ESP):** приоритизация/тайминг по серверному состоянию цели (частично уже используется). Бинарные состояния (reloading/bolting/aiming) интерпретируются тривиально; cooldown-мс требуют сопоставления с client-baseTimestamp.

**Сигнатура для АЧ.** Server-side: пометить эти ключи `SELF_ONLY` или не раздавать врагам. Клиентский детект невозможен (пакет легитимен). AC-инвариант: подозрительная корреляция выбора момента атаки с reload/bolt-окнами целей (нечеловеческий тайминг по скрытому состоянию).

---

## 4. Проверено — НЕ эксплуатируемо (отклонённые)

**Fire-rate / timestamp:**
- **NETWORK_V-окно как самостоятельный вектор** — окно `±300мс` СУЖАЕТ timestamp-spoof, а не расширяет; resync `baseTimestamp` НЕ продлевает rapid-fire (ломает его, т.к. `shootTimestamp` не сбрасывается).
- **BURST через timestamp-дельту** — один пакет = одна серверная пуля; `minInterval` пинится к реал-тайму через NETWORK_V; нет burst-специфичной амплификации.
- **Направление из серверного поворота** — корректное поведение: пакет shoot не несёт yaw/pitch; aim делается ванильным move-пакетом.
- **chargeProgress damage-amp** — clamp к `maxCharge`, в дефолтном пути не влияет на урон/скорость; эффект только на кастомном Lua-пакете; стоковые пушки гейтят к 0.
- **Pure packet-flood shoot** — cooldown+network гейты (оба true) ограничивают темп реал-RPM; чистый флуд не даёт fire-rate.
- **ClientMessageSyncBaseTimestamp как fire-rate-bypass** — `baseTimestamp` в формуле кулдауна не участвует; resync не уменьшает интервал.

**Hitreg / damage:**
- **Headshot по eye±0.25 AABB** — штатная серверная геометрия, не client-trust; нельзя подделать.
- **invulnerableTime=0** — load-bearing механика armor-ignore (один снаряд, два hurt) + shotgun damage/N; не усилитель.
- **Damage/armorIgnore/headShot/множитель** — всё серверно (gun data + Lua), пакет не несёт; clamp.
- **Distance-falloff от startPos** — `startPos` серверный, distance геометрически связан с реальным полётом; Blink/Phase desync самопротиворечив.

**Reload / bolt / melee / ammo:**
- **cancelReload thrash / chip-load** — пустые пакеты, `isReloading()` блок, серверный тайминг, штатная chip-load механика, без дюпа.
- **Reload/bolt тайминг на System.currentTimeMillis** — instant невозможен (нет полей в пакете, серверные часы, `adjustReloadTime`/`adjustBoltTime` только Lua).
- **Расход патронов при reload** — серверо-авторитетен, infinite-ammo через пакет недостижим.
- **Bolt bypass / race bolt+shoot** — все гейты серверные, `enqueueWork` последовательный, без race.

**Draw / aim-state / mode / zoom / crawl / melee / fireSelect:**
- **DrawGun reset через initialData** — спам self-harming (отменяет reload, ставит draw-кулдаун ~800мс, блокирует стрельбу), нет преимущества.
- **Crawl pose** — `tickCrawling` re-validates серверно (на земле, gun-разрешён), штатная prone-механика.
- **FireSelect → AUTO RapidFire** — переключение НЕ меняет темп; SEMI уже стреляет на полном RPM-кулдауне (триггер не трекается); bounded to set.
- **Melee reach/damage** — `StreamCodec.unit`, серверный кулдаун (`System.currentTimeMillis`), серверный cone/LoS; draw-combo self-defeating.
- **Zoom unbounded** — overflow-guard `% (MAX-1)`, все чтения `% zooms.length`, косметика.

**Craft / attach / dupe:**
- **doCraft shared recordCount** — баг реален, но в стоковых 184 рецептах нет overlapping ingredients; клиент не может создать рецепт.
- **Дефолтный станок игнорирует RecipeFilter** — при дефолте (`ENABLE_TABLE_FILTER=true`) оба чека работают; обход требует non-default misconfig.
- **UnloadAttachment race/dupe** — `enqueueWork` атомарен, `dropAllAmmo` обнуляет счётчик, повторный unload = no-op.
- **Slot-индексы OOB / equipment-слоты** — type-валидация (IGun/IAttachment) блокирует броню; vanilla `getItem` bounds-safe.
- **ServerMessageSwapItem как C2S** — строго `playToClient`, нет C2S-поверхности.

**Сеть / handshake / S2C:**
- **Отсутствие rate-limit на всех C2S** — generic MC-свойство; при дефолте ни RapidFire, ни практичный DoS сверх connection-level.
- **ServerMessageSyncGunPack** — S2C, серверный авторитет на урон/баллистику; локальная правка влияет только на клиентские расчёты чита.
- **Handshake (Mapping+Acknowledge)** — рассинхрон ключей дисконнектит самого клиента, нет серверной эскалации.

**Explosion / block / special:**
- **Взрывной снаряд / поджог / параметры взрыва** — `igniteBlock`/radius/damage/destroyBlock серверно из ammo/gun JSON, не из пакета; clamp; NeoForge-хуки есть.
- **AmmoHitBlockEvent (BellRing)** — реален, но эффект чисто косметический (звук/анимация), severity none.
- **LootTableInjector** — полностью серверный datapack, нет C2S-вектора.

---

## 5. Приоритизированный план внедрения тест-векторов в OneTap-MC

**Фаза 1 — высокий приоритет (работают при дефолтном конфиге, максимальный сигнал для AC):**

1. **`GunNoSpread` (spoof-aim) — режим «Легит»** `[#1, уже частично есть]`. Доработать: отправка `ClientMessagePlayerAim(true)` в `EventUpdate`/при наличии TACZ-оружия БЕЗ активации визуального ADS (`LocalPlayerAim`). Добавить стелс-вариант (toggle `aim=true` только в окне ~`aimTime` перед залпом). Гейтить через `try/catch(NoClassDefFoundError)` (TACZ optional). Это самый ценный тест: серверно-авторитетная точность через легит-shaped ванильный пакет.

2. **`GunRapidFire` (новый, Combat)** `[#2 + #5]`. Перехват отправки `ClientMessagePlayerShoot`: счётчик «серверного» timestamp, инкремент `(shootInterval - 5)`, front-load `serverRelativeNow - 300`, держать в окне NETWORK_V. Трекинг server-now через `ServerMessageSyncBaseTimestamp`. Два под-режима: (A) bounded burst (дефолт-конфиг), (B) sustained (для серверов с `ServerShootCooldownCheck=false`). Комбинировать с `GunAimbot` (направление = серверный поворот через `EventMotion`/`Manager.ROTATION`).

3. **`FakeLag` (новый, Misc) + расширение `GunAimbot` backtrack** `[#3]`. `FakeLag` — троттлинг исходящих move/keep-alive для раздувания `connection.latency()`. `GunAimbot`: выбор исторической позиции цели `floor(latency/1000·20+0.5)` тиков назад + velocity-shift (по `OFFSET=3`/`-5·velocity`), не центр модели. Синхронизировать silent-aim-точку с ping-окном rewind.

**Фаза 2 — средний приоритет (грифинг/QoL, демонстрируют no-ownership-check):**

4. **`GlassBreaker` (новый, Combat/Misc)** `[#4]`. Последовательное автонаведение `GunAimbot` по destroyable-glass в радиусе + `AutoReload` + автоогонь. Тест grief-protection (обход `BreakEvent`-only защит).
5. **`RemoteCraft`/`AutoCraft` (новый, Misc/Player)** `[#7]`. Запомнить `containerId` при открытии `GunSmithTableScreen`, слать `ClientMessageCraft` по списку `recipeId` с дистанции/в движении (не слать ContainerClose).

**Фаза 3 — низкий приоритет (стресс/косметика/пассив, для полноты покрытия AC):**

6. **`ReloadSpam`/`PacketFlood` (новый, Misc) или опция в `AutoReload`** `[#6]` — флуд цикла `reload→cancel→reload` N/тик для стресс-теста netty/AC.
7. **`InstantRefit` (новый, Player)** `[#8]` — UI-less swap прицела/глушителя по хоткею, синергия с `FastAds`/`NoVisualAds`.
8. **Расширение `TargetESP`/`NameTags`/`GunAimbot` — приоритизация по серверному состоянию** `[#10]` — читать `getSynShootCoolDown`/`getSynReloadState`/`getSynIsBolting` цели для тайминга атаки (пассив, частично уже есть).
9. **`LaserSpoof` (мелкая фича, Render/Misc)** `[#9]` — обход клиентского `canEdit`; опционально тест краш-инъекции отрицательным `gunSlotIndex`.

**Общие рекомендации по реализации:**
- Все TACZ-вызовы оборачивать `try/catch(Throwable)` (runtime-опциональная зависимость; деградация без мода — как в существующих gun-модулях).
- Пакеты слать через NeoForge `PacketDistributor`-аналог (как `GunNoSpread.java:128` уже шлёт `ClientMessagePlayerAim`).
- Скоупить TACZ-миксины по UUID локального игрока (`exosware.tacz.mixins.json`, `required=false`; образец — `MixinInaccuracyType`).
- Для каждого вектора реализовать парный AC-детект-режим (опция «логировать инвариант-нарушение») — основная цель тестового стенда.

**Файлы-доказательства TACZ:**
- `TACZ-1.21.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntityShoot.java` (#2/#5)
- `TACZ-1.21.1/src/main/java/com/tacz/guns/network/message/ClientMessagePlayerAim.java` (#1)
- `TACZ-1.21.1/src/main/java/com/tacz/guns/event/ammo/DestroyGlassBlock.java` (#4)
- `TACZ-1.21.1/src/main/java/com/tacz/guns/util/HitboxHelper.java`, `.../util/EntityUtil.java` (#3)
