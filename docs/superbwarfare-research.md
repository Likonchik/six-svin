# SuperbWarfare — аудит и каталог фич для OneTap (только подтверждённое кодом)

> Источники: (1) upstream-исходники `D:\Mods_Projects\SuperbWarfare-1.21`; (2) **реальный деплой-jar сервера
> KrutEvent** `D:\KE_gavno\zahvatflag\mods\superbwarfare-1.21.1-0.8.8-client.jar` (декомпилирован Vineflower).
> Каждый пункт подтверждён реальным кодом (файл + строка + цитата). Где эффект зависит от рантайма — помечено.

## ⚠️ ГЛАВНОЕ: сервер KrutEvent переписан (server/client split) — что это меняет
Подтверждено по jar 0.8.8 (классы НЕ обфусцированы, версия новее upstream):
- В клиентском jar есть `annotation/ServerOnly` (`@Target(FIELD)`) и серверные `handler()`-методы пакетов
  **физически присутствуют** (напр. `ShootMessage.handler`, `LaserShootMessage.handler` с `DamageHandler.doDamage(2*damage)`).
- **НО в NeoForge C2S-`handler()` исполняет ПОЛУЧАТЕЛЬ пакета = СЕРВЕР, из СВОЕГО jar.** Когда наш клиент
  шлёт пакет на их выделенный сервер, `handler()` из клиентского jar **НЕ вызывается** — сервер гоняет свою
  (переписанную, возможно захардненную) версию, **которой у нас нет**.
- Регистрация подтверждает (`NetworkRegistry.register()`): пакеты идут `playToServer(TYPE, STREAM_CODEC, handler)`.
  Клиенту для отправки нужны только `TYPE` + `STREAM_CODEC` (они в jar есть) — кодек, напр.,
  `ShootMessage` = `DOUBLE spread, BOOL zoom, optional UUID, optional Vector3f targetPos`.

**Вывод:** `handler()` в клиентском jar — это **НЕ доказательство** того, что делает их сервер. Мой прежний
«аудит доверия пакетов» был построен на **upstream-серверном** коде → к этому серверу он **не переносится**
напрямую. Поэтому ниже всё разделено на:
- **ТОЧНО (client-side)** — работает независимо от сервера, проверено по jar 0.8.8. 100%.
- **МОЖЕМ ОТПРАВИТЬ, сервер-эффект НЕИЗВЕСТЕН** — кодек подтверждён, но примет ли переписанный сервер — только тест.
- **НЕИЗВЕСТНО (server-dependent)** — целиком зависит от их скрытого сервера; `handler()` из клиента не в счёт.

---

## TL;DR — что можно ТОЧНО (проверено по реальному jar 0.8.8)
**100% независимо от сервера (client-side):**
- Visuals/HUD: BulletTracers (`ProjectileEntity.getShooter()`/`getShooterId()`/`COLOR_R/G/B` синк, `getDamage()`),
  ProjectileESP, Player/TargetESP (`lockedEntity:210`/`nearestEntity:223`/`seekingEntity:225`),
  **VehicleESP** (`getHealth:1675`/`getMaxHealth:1683`/`getEnergy:1059`/`getOBBs:3918`/turret-поля), Ammo/Heat HUD.
- NoRecoil/NoVisualRecoil — `recoilY:155`/`fireRecoilTime:144`/`recoilHorizon:154` двигают РЕАЛЬНЫЙ взгляд игрока
  (клиентский кик), зануление = нет отдачи камеры. Серверу всё равно на камеру → реальное преимущество.
- FastADS/NoVisualADS/кастомный FOV — `zoomTime:121`/`customZoom:172`, чистый клиент-рендер.
- AutoFire/Triggerbot — `holdingFireKey:177`-автоматизация: шлёт ОБЫЧНЫЙ `FireKeyMessage`, неотличимо от руки.

**Можем отправить, но примет ли их сервер — НЕИЗВЕСТНО (тест в игре):**
- NoSpread (`gunSpread:162`→0 в `ShootMessage` `:504`), silent-aim (`uuid`/`targetPos` для ракет; поворот
  взгляда для hitscan).

**НЕИЗВЕСТНО (целиком на их сервере, `handler()` клиента не в счёт):**
- RemoteExplosion, DroneAimbot, ArtilleryStrike, Laser-instakill, Mortar/DroneHijack, MotionInjector — их
  переписанный сервер мог всё это закрыть. Только эмпирический тест на их сервере даст ответ.

---

> Ниже — полный разбор (upstream + jar). Помни про раздел выше: серверные эксплойты к этому серверу НЕ гарантированы.

## Как читать статусы
- **[ФАКТ]** — подтверждено дословной цитатой кода (хук/поле/строка реально существует именно так).
- **[нужна проверка в игре]** — хук подтверждён, но финальный игровой эффект зависит от серверных гейтов
  (кулдауны, ре-валидация), которые нельзя проверить только по клиент-исходникам.
- **[ОПРОВЕРГНУТО]** — первичная гипотеза не подтвердилась кодом; не используем.

---

## 0. Механизм отправки C2S-пакетов (фундамент для forge-фич) — [ФАКТ]

Чтобы любой «forge-пакет» модуль работал, нужно уметь слать пакеты SBW. Подтверждено:

- Хелпер отправки: `tools/MinecraftUtil.kt:108` —
  `fun sendPacketToServer(packet: CustomPacketPayload) { PacketDistributor.sendToServer(packet) }`
- Базовый класс C2S: `network/PacketPayload.kt:14` — `abstract class ServerPacketPayload : PacketPayload()`;
  `PacketPayload : CustomPacketPayload`, `type()` берётся из `payloadTypeMap`.
- Регистрация: `network/NetworkRegistry.kt:51` — `playToServer<T>()`; имя канала = имя класса без суффикса
  `Message` (CamelCase→snake_case); кодек — `kotlinx.serialization` (`@Serializable` data-классы).
- Значит из OneTap (SBW как `compileOnly`) можно: сконструировать `ShootMessage(...)`/`DroneFireMessage(...)`
  и вызвать `PacketDistributor.sendToServer(...)` тем же каналом. **Дешевле** — звать сам
  `MinecraftUtil.sendPacketToServer(...)` (статический Kotlin-метод) рефлексией/прямо.

---

## 1. Уязвимости сетевого слоя (подтверждённые)

Сервер почти не валидирует C2S-ввод. Все строки/цитаты ниже — из реального кода.

| # | Пакет (файл) | Severity | Что доверяется (цитата) | Статус |
|---|---|---|---|---|
| 1 | `LungeMineAttackMessage` (type=1) | crit | `CustomExplosion.Builder(player)....position(pos).explode()` — `pos` от клиента, без дист/LOS (`:43-54`). Урон/радиус из `ExplosionConfig` (не из пакета). | [ФАКТ] |
| 2 | `DroneFireMessage` | crit | `FiringParametersItem.Parameters(BlockPos(pos.x,pos.y,pos.z), radius, isDepressed)` — `pos` от клиента (`:34-40`), без дист/LOS. | [ФАКТ] |
| 3 | `InteractMessage` (дрон) | crit | `findDrone(level, tag.getString("LinkedDrone"))` без проверки владения → `findLookingEntity(drone,2.0)` → `player.attack(lookingEntity)` (`:21,28,69-71`). | [ФАКТ] |
| 4 | `PlayerStopRiding`→`ClientSetMotionMessage` | crit | приём-сторона: `player.setPos(position.toVec3()); player.deltaMovement = motion.toVec3()` — **без всякой валидации** (`ClientSetMotionMessage.kt:19-20`). | [ФАКТ] |
| 5 | `ShootMessage` | high | `from(stack).shoot(player, spread, zoom, uuid, targetPos.toVec3())` — `spread/uuid/targetPos` от клиента (`:19-29`). `targetPos`→`MissileProjectile.setTargetVec` (`GunItem.kt:836-845`). | [ФАКТ] |
| 6 | `FiringParametersEditMessage` | high | `Parameters(BlockPos(x,y,z), radius, isDepressed)` — `x/y/z/radius` от клиента без проверки (`:14-28`). | [ФАКТ] |
| 7 | `ArtilleryIndicatorFireMessage` | high | `entity.vehicleShoot(player, "Main", entity.targetPos.center)` — `targetPos` из NBT, без range/ownership (`:36,44`). | [ФАКТ] |
| 8 | `VehicleMovementMessage` | high | `vehicle.processInput(keys)` — биты ввода без rate/authority (`:28`). | [ФАКТ] |
| 9 | `AdjustMortarAngleMessage` | high | `TraceTool.findLookingEntity(player, 6.0) as? MortarEntity` — без проверки владения миномётом (`:16`). | [ФАКТ] |
| 10 | `RadarSetPosMessage` | high | `menu.setPos(pos.x,pos.y,pos.z)` — произвольная позиция (`:17`), гейт только `menu.stillValid`. | [ФАКТ] |
| 11 | `SetPerkLevelMessage` | high (DoS) | `Perk.Type.entries[msgType]` — `msgType` от клиента без bounds → IndexOOB → краш (`:17`). | [ФАКТ] (только DoS) |
| 12 | `LaserShootMessage` | high* | `entity.forceHurt(..., (2*damage))` — `damage` от клиента, без cap/range/оружия (`:16-46`). | [ФАКТ, но dead-code] |
| 13 | `RadarSetTargetMessage` | med | фильтр `it.getOwner() === player && distanceTo<=24`, но **не** «цель — враг» (`:19-25`). | [ФАКТ] |
| 14 | `MouseMoveMessage` | med | `entity.mouseInput(speedX, speedY)` без клампа и проверки пилота (`:18,27`). | [ФАКТ] |
| 15 | `AimVillagerMessage` | med | `level().getEntity(villagerId) as? AbstractVillager` — без серверной дист/LOS (`:15`). | [ФАКТ] (PvE-грифинг) |

### Важные оговорки (честно)
- **#12 `LaserShootMessage` — dead-code.** `NetworkRegistry.kt:108 playToServer<LaserShootMessage>()` —
  зарегистрирован, но нигде в SBW не создаётся/не отправляется. Инстакилл реален, но требует
  **построить пакет с нуля** (raw-конструкция + кодек), а не перехватить существующую отправку. Тривиально
  логируется сервером. → **DEFER**.
- **#5 `ShootMessage.targetPos` влияет только на управляемые ракеты** (`MissileProjectile`, `guideType=1`),
  что подтверждено `GunItem.kt:836-845`. Для **обычных пушек** направление выстрела сервер берёт из
  `lookAngle` игрока → silent-aim по обычным стволам делается **поворотом серверного взгляда** (наш
  стандартный механизм `EventMotion`/move-пакет, как с TACZ), а не через `targetPos`.
- **#6 эффект `radius`** (огромный разброс арты) в самом обработчике НЕ доказан — логика применения `radius`
  лежит ниже по коду (`FiringParametersItem`/`ArtilleryEntity`). Сам факт «`radius` принимается без клампа» —
  подтверждён; «разброс до Int.MAX» — **[нужна проверка в игре]**.
- `RadarSetParametersMessage`: обработчик зовёт `menu.setPosToParameters()` с гейтом `stillValid`; сама
  цепочка переноса координат — внутри `FuMO25Menu`, не в обработчике → точную цепочку **не подтверждаю** на
  100%, только то, что `RadarSetPosMessage` принимает произвольный `pos`.

### Отклонено верификацией [ОПРОВЕРГНУТО]
- `MeleeAttackMessage` — урон **пересчитывается сервером** из `Attributes.ATTACK_DAMAGE`, не из пакета. Не спуф.
- `SwitchVehicleWeaponMessage` / `ChangeVehicleSeatMessage` / `BlueprintSetIndexMessage` / `FireModeMessage`
  — сервер клампит/валидирует индекс. Не эксплойт.
- `FireKeyMessage.power` — **не используется** для урона (`onFireKeyRelease` только глушит звук). Не эксплойт.

---

## 2. Каталог фич для OneTap (по тирам)

Для каждого модуля: что делает, **подтверждённый** хук (файл:строка), механизм, и честный статус эффекта.

### Tier A — Visuals / HUD (read-only, риск низкий) ← рекомендую начать
Переиспользуем наш render-слой (`RenderUtil.drawLine/drawBox`, `ColorUtil.getColorStyle`, `EventRender3D/2D`).

1. **SbwBulletTracers** — линия от стрелка к снаряду.
   - [ФАКТ] `ProjectileEntity.shooter` публичный гет (`:80-81`), `shooterId` (`:84-85`); цвет синкается —
     `COLOR_R/COLOR_G/COLOR_B` (`EntityDataAccessor`, `:1110-1119`). Позиция/скорость — из базового `Entity`
     (`getX/getDeltaMovement`, публичные).
   - Механизм: read-only-HUD. Прямой аналог нашего `BulletTracers`. **Полностью осуществимо.**

2. **SbwProjectileESP + Incoming-Missile-Warning** — боксы снарядов, предупреждение о ракете.
   - [ФАКТ] фильтр `clientLevel.getEntitiesOfClass(ProjectileEntity)`; читаем `shooter`/`COLOR_*`/позицию.
   - [нужна проверка в игре] точное «время до удара» — `velocity/gravity` у пули **private** (`:123,:139`),
     напрямую не читаются; для оценки брать собственный `GunProp.VELOCITY`/`GunProp` или фактическую
     `getDeltaMovement()`. Для входящей самонаводящейся — статус читать из `ClientEventHandler.seekingEntity`
     (`:364-365`)/`lockedEntity` (`:316-317`).

3. **SbwPlayerESP / TargetESP / head-hitbox** — подсветка игроков, голова, недавние стрелки.
   - [ФАКТ] `ClientEventHandler.nearestEntity` (`:361`), `seekingEntity` (`:364`), `lockedEntity` (`:316`)
     доступны для подсветки текущей цели. Игроки — `clientLevel.getEntitiesOfClass(Player)`.
   - Механизм: read-only-HUD. Переиспользуем `ESP`/`TargetESP`/`NameTags`.

4. **SbwVehicleESP** — HP техники, броня по частям, топливо/энергия, схема сидений. **Сильная фича.**
   - [ФАКТ] всё публичное: `getOBBs()` (`:4042`), `OBB.Part` = EMPTY/WHEEL_LEFT/WHEEL_RIGHT/TURRET/
     MAIN_ENGINE/SUB_ENGINE/BODY/INTERACTIVE/COLLISION (`OBB.kt:273-309`); `health` (`:1686`),
     `energy` (`:873`), `maxEnergy` (`:908`); по-частям HP: `turretHealth` (`:3963`), `leftWheelHealth`
     (`:3958`), `rightWheelHealth` (`:3960`), `mainEngineHealth` (`:3955`), `subEngineHealth` (`:3953`);
     `getPassengers()` (inherited), `getSeatIndex(entity)` (`:722`).
   - Механизм: read-only-HUD. **Полностью осуществимо**, пакетов не нужно.

5. **SbwCrosshairBallistics / Lead-Reticle** — дуга падения, точка упреждения.
   - [ФАКТ] для своей пушки: `GunData.from(stack).get(GunProp.VELOCITY)` (`GunProp.kt:80`), `RPM` (`:227`),
     `SPREAD` (`:71`) — все читаемы через публичный `GunData.get(prop)` (`:166`).
   - [нужна проверка в игре] точная модель гравитации (у пули `gravity` private = 0.05f дефолт) — брать как
     константу 0.05 или эмпирически; дуга строится `t=dist/vel, drop=0.5·g·t²`.

6. **SbwHUD: Ammo / FireMode / Heat** — патроны/режим/нагрев своей пушки.
   - [ФАКТ] `GunData.ammo` (`:668`), `GunProp.MAGAZINE` (`:149`), `reloading()` (`:719`), `reload` (`:714`).
   - Механизм: read-only-HUD. Только своя пушка (чужие — без кастом-синка не читаемы).

### Tier B — Gun-assist (зеркало нашего TACZ-набора)

7. **SbwNoSpread** — нулевой разброс. **Подтверждённый эксплойт.**
   - [ФАКТ] клиент сам считает `gunSpread` (`ClientEventHandler.kt:181`, формула `:1423-1436`) и **шлёт его
     серверу** в `ShootMessage` (`handleClientShoot:1567-1579`: `ShootMessage(gunSpread, zoom, ...)`).
     Обработчик сервера передаёт `spread` прямо в `shoot()` без ре-вывода (`ShootMessage.kt:19-29`).
   - Механизм: modify-outgoing-packet (форж `gunSpread=0`/перехват `ShootMessage`). Эффект подтверждён
     потоком данных. Детектируемо (нулевой спред в движении) → гейтить под `legitMode` + остаточный спред.

8. **SbwAimbot** — авто-наведение.
   - Обычные пушки: [ФАКТ] сервер стреляет по `lookAngle` → классический silent-aim **через поворот
     серверного взгляда** (наш `Manager.ROTATION`/`EventMotion`, как у TACZ `GunAimbot`). Цель брать из
     `nearestEntity`/`seekingEntity` или своим скан-кодом.
   - Управляемые ракеты: [ФАКТ] форж `ShootMessage.uuid`/`targetPos` (`GunItem.kt:836-845` → `setTargetVec`/
     `setTargetUuid`) — наведение ракеты в любую точку/цель, без LOS.
   - Переиспользуем sort/stickiness/hitchance/humanize/backtrack из `ru.levin.modules.combat.GunAimbot`.

9. **SbwNoRecoil / NoVisualRecoil** — без отдачи камеры.
   - [ФАКТ] клиентская отдача **двигает реальный взгляд**: `handleGunRecoil` пишет `player.yRot` (`:2462-2465`)
     и `player.xRot` (`:2467-2478`); поля `recoilY` (`:157`), `fireRecoilTime` (`:136`), `recoilForce`
     (`:160`). Зануление этих величин/отмена `handleGunRecoil` убирает кик взгляда (он клиентский — реальный
     физический отброс `deltaMovement` сервер применяет отдельно в `GunItem`).
   - Механизм: mixin/client-event. Эффект на камеру — подтверждён. Прецедент отмены боббинга —
     `GameRendererMixin.bobView` (`:34-44`).

10. **SbwTriggerbot / AutoFire** — авто-огонь по цели.
    - [ФАКТ] огонь идёт через `FireKeyMessage` (`ClickEventHandler.kt:530`); состояние удержания —
      `holdingFireKey` (`:227`), `holdingFireKeyTicks` (`:270`); тайминг — `clientTimer` (MillisTimer, `:221`).
    - [нужна проверка в игре] серверный кап RPM (`shootTimer`) — выше капа сервер отобьёт; триггербот
      (огонь при наведении) эффективен в пределах легитного RPM.

### Tier C — ADS / Zoom / FOV (риск низкий, чистый клиент)

11. **SbwFastADS / InstantZoom / NoVisualADS** — мгновенный/невидимый прицел.
    - [ФАКТ] `zoomTime` (`ClientEventHandler.kt:67`); FOV-применение — `onFovUpdate` (ViewportEvent.ComputeFov,
      `:2601-2648`, `event.fov /= (1 + p*(customZoom-1))`). Установка `zoomTime=1.0` мгновенно/скрытие
      анимации — чистый клиент, сервер zoom-FOV не валидирует.
    - Механизм: client-event/mixin. Зеркало нашего `FastAds`/`NoVisualAds`. Зум-пакет для лока:
      `handleWeaponZoomPress`→`ZoomMessage(0)` (`ClickEventHandler.kt:585`).

12. **SbwFOV / Camera utils** — оверрайд FOV/камеры.
    - [ФАКТ] хуки FOV (`onFovUpdate:2601`), камера — `CameraMixin.setup` (`:40-43` и TAIL `:116-131`).
    - Механизм: mixin. Чистый render, неотслеживаемо.

### Tier D — Packet-эксплойты (SBW edge; громко → строго под `legitMode`)

13. **SbwRemoteExplosion** — взрыв в любой точке (vuln #1).
    - [ФАКТ] построить `LungeMineAttackMessage(type=1, uuid, pos)` и `sendPacketToServer`. Нужен `LUNGE_MINE`
      в руке (`stack.shrink(1)` если не креатив). Урон/радиус из `ExplosionConfig`.
    - [нужна проверка в игре] что сервер не дропает взрыв в незагруженном чанке.

14. **SbwDroneArtilleryAimbot** — wallhack-огонь дрона/арты (vuln #2).
    - [ФАКТ] клиент шлёт `DroneFireMessage(pos.toVector3f())` (`ClickEventHandler.kt:716`); сервер кладёт `pos`
      в firing-параметры без дист/LOS. Луп для обхода клиентского кулдауна (`shootCoolDown:357` — клиентский).
    - [нужна проверка в игре] серверный кулдаун на огонь арты/дрона.

15. **SbwArtilleryStrike** — бомбардировка по координатам (vulns #6/#7).
    - [ФАКТ] `FiringParametersEditMessage(x,y,z,radius,...)` → затем `ArtilleryIndicatorFireMessage` (огонь по
      `targetPos` из NBT, `:36,44`).
    - [нужна проверка в игре] эффект `radius`, серверные гейты огня.

16. **SbwDroneHijack / TargetAura** — угон дрона, урон без оружия (vuln #3).
    - [ФАКТ] `InteractMessage` с чужим UUID дрона в NBT `"LinkedDrone"` → сервер зовёт `player.attack` по тому,
      на что смотрит дрон (`findLookingEntity(drone, 2.0)`), без проверки владения (`:21,28,69-71`).

17. **SbwMortarHijack** — угон чужого миномёта (vuln #9).
    - [ФАКТ] `AdjustMortarAngleMessage` правит `TARGET_PITCH` любого `MortarEntity` в 6 блоках LOS без
      проверки владения (`:16`). Питч клампится → это захват управления, не overflow.

### Tier E — Vehicle combat

18. **SbwVehicleAimbot / SilentTurret** — авто-наведение башни.
    - [ФАКТ] `VehicleFireMessage(uuid, targetPos)` (обработчик зовёт `vehicleShoot(...)`); углы башни публичны:
      `turretYRot` (`:251`), `turretXRot` (`:252`), `gunYRot` (`:257`), `gunXRot` (`:258`); `vehicleShoot`
      перегрузки (`:984`, `:1034`).
    - [нужна проверка в игре] серверный кулдаун выстрела техники; клампы углов `SeatInfo`.

19. **SbwVehicleAimbot(radar)** — force-lock через радар (vuln #13).
    - [ФАКТ] `RadarSetTargetMessage` ставит `targetUUID` без проверки «враг ли» (`:19-25`). Требует владеть
      радар-техникой + 24 блока.

20. **SbwSilentVehicleControl** — инъекция ввода техники (vuln #8).
    - [ФАКТ] `VehicleMovementMessage` → `vehicle.processInput(keys)` без rate/authority (`:28`).
    - [нужна проверка в игре] насколько заметно наблюдателям.

21. **SbwVehicleNoRecoil / Camera-decouple** — отдача/камера техники.
    - [ФАКТ] камера техники — `CameraMixin` (`:40-43`/`:116-131`). Отдача/зум — клиент-визуал.

### Tier F — Movement (высший риск десинка/бана, последним)

22. **SbwMotionInjector / Phase / Flight** — телепорт/инъекция скорости (vuln #4).
    - [ФАКТ] спам `PlayerStopRidingMessage(eject)` → сервер шлёт `ClientSetMotionMessage`, чей клиентский
      обработчик делает `player.setPos`+`deltaMovement = motion` **без проверок** (`:19-20`).
    - [нужна проверка в игре] это самый рискованный вектор (анти-чит на движение/десинк).

23. **SbwInfiniteDoubleJump** — мульти-прыжок.
    - [ФАКТ] `ClickEventHandler.kt:645-646`: `sendPacketToServer(DoubleJumpMessage); canDoubleJump = false`.
      Держать `canDoubleJump=true`/слать `DoubleJumpMessage` в цикле. Направление — серверный `lookAngle`
      (`DoubleJumpMessage.kt:24`), т.е. только множит число прыжков.

24. **SbwNoFall (Parachute)** — без урона от падения.
    - [ФАКТ] сервер **валидирует**: `cooldown` + `deltaMovement.y < -0.6` + `fallDistance > 4`
      (`ParachuteMessage.kt:20,23`). → чистого обхода нет; спуф должен удовлетворять этим условиям. Частично.

---

## 3. Что НЕ подтверждено / ограничения (важно для честности)
- `ProjectileEntity.{velocity,gravity,damage,explosionRadius}` — **private** (`:123,139,88,111`) → для трассеров
  читаем только `shooter`/`COLOR_*`/позицию; баллистику считаем по `GunProp` своей пушки или по
  `getDeltaMovement()`. (есть `getDamage()` `:942`, но это не нужно для визуала).
- `ShootMessage.targetPos` — наведение **только** для `MissileProjectile`; обычные пушки → silent-aim через
  поворот взгляда.
- Серверные кулдауны/капы на огонь (RPM, арта, башня) — из клиент-исходников не видны → **все** боевые
  forge-эксплойты помечены [нужна проверка в игре].
- Скриншот-захвата (AWT Robot/`TargetSignal`) в upstream-исходниках **нет** (grep пуст) — это была инъекция
  KrutEvent в их сборку. Наш `MixinSbwScreenshotPacket` рассчитан на KrutEvent-jar, не на ванильный SBW.

---

## 4. Интеграция в OneTap (паттерн TACZ)
1. **build.gradle**: SBW jar как `compileOnly files('libs/superbwarfare-….jar')` + `runtimeOnly` для dev; все
   обращения к SBW — через `try/catch(Throwable)` (graceful без мода).
2. **Модули**: `ru/levin/modules/<category>/Sbw*.java`, наследуют `Function`, `@FunctionAnnotation`,
   регистрация в `FunctionManager` (порядок = UI и обработки; конфликты `EventMotion`-поворота → в пользу
   аимбота). Дефолт выключены.
3. **Cross-mod миксины**: новый `exosware.sbw.mixins.json` (`required=false`, `defaultRequire=0`,
   `remap=false`) + запись в `neoforge.mods.toml` — для рекойла/зума/камеры, где нужен mixin.
4. **Forge-пакеты**: ссылаться на SBW-классы при компиляции (compileOnly), слать через
   `PacketDistributor.sendToServer(...)` или `MinecraftUtil.sendPacketToServer(...)`.
5. **legitMode**: все Tier D/E/F — строго под `ClientManager.legitMode` kill-switch.

### Представительные новые файлы
- `modules/render/SbwTracers.java`, `SbwProjectileESP.java`, `SbwVehicleESP.java` (Tier A).
- `modules/combat/SbwAimbot.java`, `SbwNoSpread.java`, `SbwNoRecoil.java`, `SbwTriggerbot.java` (Tier B).
- `modules/render/SbwFastAds.java` / `SbwNoVisualAds.java` (Tier C).
- `modules/misc/SbwRemoteExplosion.java`, `SbwArtilleryStrike.java`, `SbwDroneAimbot.java`,
  `SbwDroneHijack.java`, `SbwMortarHijack.java` (Tier D).
- `modules/movement/SbwMotionInjector.java`, `SbwDoubleJump.java` (Tier F).
- `mixin/sbw/*` (новый пакет) + `exosware.sbw.mixins.json`.
- правки: `FunctionManager` (регистрация), `build.gradle` (dep).

## 5. Рекомендованный порядок (Milestone)
- **M1 Visuals (Tier A)** — ESP/трассеры/Vehicle-ESP/HUD. Нулевой риск, максимум переиспользования.
- **M2 Gun-assist (Tier B)** — NoSpread (подтверждён), Aimbot, NoRecoil, Triggerbot.
- **M3 ADS/FOV (Tier C)** — FastADS/NoVisualADS/FOV.
- **M4 Packet-эксплойты (Tier D)** — RemoteExplosion/DroneAimbot/Artillery/DroneHijack/Mortar (под legitMode).
- **M5 Vehicle (Tier E)** — VehicleAimbot/SilentControl.
- **M6 Movement (Tier F)** — MotionInjector/DoubleJump (риск десинка, последним).
- **DEFER**: InstaKill (`LaserShootMessage` dead-code, нужен raw-пакет, логируется); SetPerkLevel/AimVillager
  (DoS/PvE-грифинг, не преимущество).

## 6. Проверка в игре (обязательна для Tier D/E/F)
`./gradlew runClient` с установленным SBW → подтвердить руками: исполняет ли сервер инжектированные
координаты/пакеты; есть ли серверный кулдаун на арту/башню/дрон; не дропаются ли координаты для незагруженных
чанков; реальный эффект `radius` арты; что NoSpread/NoRecoil дают видимый эффект.
