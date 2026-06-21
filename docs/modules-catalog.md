# Каталог модулей

Этот документ — полный справочник по всем модулям (`Function`) клиента **ExosWare** (Fabric, Minecraft 1.21.4, group `ru.levin`, archives `exosware`). Каждый модуль расширяет абстрактный класс `ru.levin.modules.Function`, объявляет метаданные через `@FunctionAnnotation` (`name`, `desc`, `key`, `type`, `keywords`) и регистрируется в едином конструкторе `FunctionManager`. Модули сгруппированы по перечислению `Type`: `Combat`, `Move`, `Render`, `Player`, `Misc` (всего 5 категорий).

Настройки описываются через подтипы `Setting`: `BooleanSetting`, `ModeSetting`, `MultiSetting`, `SliderSetting`, `TextSetting`, `BindSetting`, `BindBooleanSetting`. Зарегистрированных модулей около 90.

> Примечание о расположении классов: класс модуля `LittlePet` фактически называется `LittleSnickers`; модуль с `@FunctionAnnotation(name = "WTap")` реализован классом `AttackExtend`; модуль `HighJump` физически лежит в пакете `player`, но аннотирован как `Type.Move`.

---

## Combat

Боевые модули используют общий синглтон прицеливания `RotationController` (доступен как `Manager.ROTATION`). Управляемые им вращения «утекают» на сервер через `EventMotion` (silent aim), сглаживаются и квантуются по GCD для обхода анти-читов. `CrystalAura` и `AutoExplosion` не используют `RotationController`, а пишут собственный `Vector2f` напрямую в `EventMotion`.

| Модуль | Назначение | Ключевые настройки |
| --- | --- | --- |
| `AttackAura` | Ближняя KillAura: ищет и валидирует лучшую `LivingEntity` (сортировка по health/distance/armor), прицеливается через `Manager.ROTATION` одним из множества серверно-специфичных стилей вращения, атакует по кулдауну, корректирует движение, ломает/разблокирует щит, делает сброс спринта. Вращение утекает на сервер в `EventMotion` (silent aim). | `Mode` (ReallyWorld/HollyWorld/FunTime/SpookyTime/Snap/KoopinAc/LonyGrief/1.8.8), `Targets` multi (Players/Bare/Friends/Mobs/Monsters/Villagers), `Sort` (health/distance/armor), `Settings` multi (only-crits/break-shield/unblock-shield), Attack radius 3.0 (1.8–6), Detect radius 5 (0–10), Elytra radius 40 (0–80), Snap speed 150ms, Correction+type (Free/Focus), Sprint type (Rage/Legit/None), no-attack-if-eating, raycast |
| `CrystalAura` | Аура энд-кристаллов: оценивает позиции под обсидиан/бедрок по упрощённой модели урона, ставит (основная или левая рука), при необходимости мульти-плейс до 3 точек, ломает кристаллы через `PlayerInteractEntityC2SPacket.attack`. Определяет регион-защиту из чата и приостанавливается. Использует собственный `Vector2f rotate`. | `options` multi (no-self-explode/move-correction/falling-player/block-highlight), Radius type Svoy/Grim (Grim = 3.6), Custom radius 5 (2.5–6), Break delay 100ms, off-hand crystal (сравнение с `AutoTotem.hp`), render block, region check (вкл. по умолч.), multi-place |
| `AutoExplosion` | Автоматически ставит энд-кристалл при установке обсидиана (слушает `EventObsidianPlace`) или по бинду, через запланированную последовательность place→crystal→attack; корректирует движение и плавно вращает `serverRot` к кристаллу в `EventMotion`. | movement correction, Delay 100ms (50–300), «place by bind» (sanya) + `BindSetting`. Жёстко: 50ms шаг обсидиана, 4-блочный raycast, 6.0 дистанция сброса, 10°/тик ограничение. Использует `ScheduledExecutorService` (не останавливается при disable) |
| `AutoPotion` | AutoBuff: бросает сплэш-зелья Силы/Скорости/Огнестойкости под себя, подменяя исходящий pitch на 90 (вниз) в `EventMotion`, отправляет последовательные `PlayerInteractItem` пакеты, восстанавливает реальный pitch. Выставляет флаг `isActivePotion`, потребляемый `AttackAura`. | auto-off after buff, potions multi (Strength/Speed/FireResistance). Требует on-ground, твёрдый блок снизу, таймер 500ms, не есть. Неиспользуемые id 5/1/12 |
| `AutoTotem` | Перемещает Тотем бессмертия в левую руку (слот 40) при падении HP ниже порога или контекстной опасности (кристалл/анкор/обсидиан/крипер/вагонетка/мейс/падение), опционально возвращает прежний предмет. Мгновенно реагирует на спавн `EndCrystal`/`TntMinecart` через `EventEntitySpawn`. | trigger multi (Crystal/Mace-player/Creeper/Obsidian/Anchor/Fall/Minecart), HP 4.5 (2–20), elytra-earlier +5, return item, skip-if-orb, keep-enchanted-totems, +absorption, per-source distances |
| `AutoSwap` | Меняет два выбранных предмета между левой рукой и инвентарём по бинду (`SlotActionType.SWAP`), опционально меняет меч↔топор в хотбаре, с обходом FunTime/HollyWorld (отпускает клавиши движения и задерживает свап ~90ms). | swap key (`BindSetting`), first/second item (Shield/Apple/Totem/Orb=PLAYER_HEAD/Firework), swap sword&axe, FT/HW bypass. Тайминг обхода: свап на 90ms, восстановление клавиш на 150ms |
| `WTap` (класс `AttackExtend`) | AttackExtend / расширенный отбрасывание: на 1 тик обнуляет `movementForward` после атаки (`EventAttack`) при спринте и движении, чтобы перезапустить спринт и продлить нокбэк противника. | only-on-ground (по умолч. true). keywords ExtendedAttack/ExtendedKnockBack. Пропускает в жидкости |
| `Criticals` | Packet-криты: на каждый `EventAttack` отправляет пару крошечных up/down `PlayerMove`-пакетов, чтобы сервер засчитал падающий (критический) удар без реального прыжка. | Жёстко y=0.01250004768372. Условие ground/water/flying имеет особенность приоритета операторов (`&&`/`||`) |
| `HitBox` | Увеличивает хитбоксы игроков для упрощения попаданий. Тело `Function` пустое; расширение AABB применяется миксином, читающим слайдер `size`. | Razmer 0.4 (0.1–5.5). Логика — в отдельном миксине |
| `NoFriendDamage` | Отключает исходящий урон по игрокам-друзьям. Тело `Function` пустое; принуждение — в миксине через `FRIEND_MANAGER`. | keywords NFD/FriendDamage. Без настроек |
| `SelfTrap` | Окружает ноги игрока блоками: очередь из 4 горизонтальных соседей, поиск стороны для установки, вращение к ней (собственный `Vector2f` в `EventMotion`), установка любого блока из хотбара, восстановление исходного слота. | Без пользовательских настроек. Случайная задержка 1–3 тика; перебор N/S/E/W; восстановление слота при finish/disable |
| `SuperBow` | Усиливает скорость стрелы лука: на отпускании (`PlayerActionC2SPacket RELEASE_USE_ITEM`) отправляет START_SPRINTING плюс `power` пар почти нулевых (±1e-9) Y move-пакетов для разгона стрелы. | Sila 30 (1–200). Реагирует на `EventPacket` |
| `TargetStrafe` | Стрейф вокруг боевой цели (вместе с `AttackAura`). В основном держит настройки (скорость, метод подтягивания, дистанция, hitbox-boost, предсказание), читаемые другими системами (`AttackAura.predictPos`); при включении принудительно отключает `Speed`, при выключении отпускает forward. | Speed 0.095, pull method Vector/Motion-Velocity, pull distance 7, hitbox-for-boost 0.095, predict (по умолч. вкл.) + predict value 2.5, see-predict |
| `Velocity` | AntiKnockback: отменяет `EntityVelocityUpdateS2CPacket`, нацеленный на локального игрока, чтобы игнорировать серверный нокбэк. | mode (Cancel only). keywords AKB/AntiKnockBack. На базе `EventPacket` |
| `AntiBot` | Определяет и помечает тренировочных ботов анти-чита (хрупкая эвристика по броне/еде), предоставляет `check(entity)` для `AttackAura`; опционально удаляет помеченных ботов из мира. | remove-from-world (по умолч. off). Эвристика: полная кожа/железо, всё зачаровываемо, без повреждений, пустая левая рука, непустая правая, food ровно 20 |

## Movement

Модули движения манипулируют скоростью/позицией игрока, буферизуют C2S-пакеты для блинка и десинк-трюков и управляют элитра-аимботом. Опираются на `MoveUtil`, `NetworkUtils.sendSilentPacket`, `TimerUtil` и `ClientManager.TICK_TIMER`.

| Модуль | Назначение | Ключевые настройки |
| --- | --- | --- |
| `Speed` | Ванильное горизонтальное ускорение. На `EventMotion` → `MoveUtil.setSpeed(speed)` при движении и не в планировании. | mode (Vanilla), speed 0.1–3.0 (по умолч. 1.0). `onEnable` отключает `targetStrafe`; `onDisable` сбрасывает `ClientManager.TICK_TIMER=1.0`. desc='Поможет умереть быстрее' |
| `Flight` | Полёт в стиле креатива/элитры. Motion-режим: прыжок/присед задают вертикальную скорость, спринт — `MoveUtil.setMotion(xspeed)`. ElytraRWOld: авто-надевание и планирование с хотбарной элитрой. | mode (Motion/ElytraRWOld), X-speed 0–5, Y-speed 0–5. swapDelay 520ms, слоты свапа (6, i), START_FALL_FLYING, фейерверк-спам через `InventoryUtil.inventorySwapClick2` |
| `Blink` | Десинк через задержку пакетов: буферизует исходящие C2S-пакеты и сбрасывает их пачками через `NetworkUtils.sendSilentPacket` каждые `maxTicks` тиков; рисует последний bounding box. | maxTicks 1–50 (по умолч. 20). Исключает `KeepAliveC2SPacket`. Может `toggle()` себя при null player/world |
| `NoSlow` | Отменяет `EventNoSlow` для предотвращения замедления при использовании предметов, по анти-читу. | Modes: Grim (всегда отмена), ReallyWorld (отмена на тиках 1–2), LonyGrief (отправляет `PlayerInteractItemC2SPacket` противоположной рукой, затем отмена) |
| `Timer` | Манипулирует `ClientManager.TICK_TIMER` для клиентской скорости игры. «Умный» режим использует счётчик нарушений для дросселирования всплесков. | timerAmount 0–10 (по умолч. 2), smart bool (по умолч. true), ticks(decay) 0.15–5.0 (по умолч. 3.8). Smart: violation/maxViolation=100 |
| `FreeLook` | Третье лицо со свободной камерой без изменения направления движения. Принудительно `Perspective.THIRD_PERSON_FRONT`, блокируется при наличии цели `AttackAura`. | bind key. Сопряжён с `MixinCamera`/`MixinEntity` через `FreeLookState` + `CameraOverriddenEntity` |
| `ElytraTarget` | Элитра-боевой аимбот: целится в цель `AttackAura`, предсказывает движение, уклоняется, пускает фейерверки, fakelag'и. Публичные `nextPhase`/`overtakingElytra`/`targetDefault`/`onTargetAttack` вызываются из боевой подсистемы. | Modes Обычный/Продвинутый. sila(отлёт) 5–40, silaTime 200–1000, perelet+predict 0.1–6, leaveHP+leaveList multi, resolver+resolverStrength 4–30, prefer+preferDir, avoidWalls, firework+fireworkTime 100–2000, fakelags+fakelagsDistance 5–20/maxDistance 15–50, visual |
| `ElytraRecast` | Авто-передеплой элитры после взлёта (rerek). Кастует через `InventoryUtil.startFly` при падении; проверяет прочность элитры; использует `MixinLivingEntityAccessor.setLastJumpCooldown(0)`. | changePitch (по умолч. true) + pitchValue −90..90 (по умолч. 55), autoJump (по умолч. true) |
| `ElytraMotion` | Замораживает игрока на месте во время планирования рядом с целью `AttackAura`. На `EventMotion` обнуляет motion X/Y/Z и `setPosition(freezePosition)`. | distance 0.1–5.0 (по умолч. 3.0) |
| `SuperFirework` | Усиливает буст фейерверка; экспонирует поля настройки, читаемые миксинами. `onEvent` безусловно перезаписывает все поля жёсткими константами (`speedXZ=1.61` и т.д.) независимо от режима. | mode (BravoHvH/ReallyWorld/PulseHVH/Custom), speed 1.5–8.0 (по умолч. 1.70, только Custom), nearBoost bool |
| `Phase` | Проход сквозь блоки горизонтально на ReallyWorld. Буферизует `PlayerMoveC2SPacket`, при частичном вхождении в блок шлёт 2 `Full`-пакета; при выходе отключается. При disable телепорт в (x−5000, z−5000) и обратно. | Буферизация пакетов; магическое смещение 5000 |
| `Spider` | Авто-лазание по стенам. RwWater ставит вёдра воды; Matrix прыгает по стене. Отменяет серверный чат-пакет 'Извините, но вы не можете поставить блок здесь.' | Modes RwWater/Matrix. RwWater: ведро в хотбар (SWAP), `UpdateSelectedSlotC2SPacket`, `PlayerInteractItemC2SPacket` каждые 20ms, velocity 0,0.3,0. Matrix: vel.y=0.42 |
| `Strafe` | Фиксированный стрейф, масштабируемый усилителем зелья Скорости. | Mode MetaHvH. motion базовый 0.19; по амплификатору: 0→0.25, 1→0.37, 2→0.46, 3→0.7, иначе 0.75+(amp−3)·0.05; +0.1 на прыжке |
| `NoWeb` | Нейтрализует замедление в паутине. Активен только при `MoveUtil.isInWeb()`. | Modes Custom (speedXZ 0.1–1 по умолч. 0.1, speedY 0.1–4 по умолч. 0.1) и ReallyWorld (жёстко vel.y ±0.9, `MoveUtil.setSpeed(0.21)`) |
| `AutoSprint` | Принудительно держит клавишу спринта нажатой на каждом `EventUpdate`; отпускает при disable. | Без настроек |
| `AirStuck` | Подвешивает игрока в воздухе в позиции включения. На `EventMotion` `setPosition(freezePosition)` + нулевая скорость. | packet bool (по умолч. true, отменяет `EventMotion`). `freezePosition` захватывается в `onEnable` |

> Вспомогательные классы пакета `movement/freelook`: `FreeLookState` (статический holder полей `active`/`yaw`/`pitch`, мост между модулем и миксинами камеры) и `CameraOverriddenEntity` (интерфейс контракта переопределения камеры, реализуемый миксином сущности).

## Render

Визуальные модули рисуют 2D HUD-оверлеи и 3D мировые визуалы через события `EventRender2D`/`EventRender3D`/`EventUpdate`/`EventAttack`/`EventPacket`/`EventFog`/`EventHeldItemRenderer`, используя `IMinecraft.tessellator()` BufferBuilders, помощники `RenderUtil`/`RenderAddon`, шрифты `FontUtils`, градиенты темы `ColorUtil` и якоря HUD `Dragging`.

| Модуль | Назначение | Ключевые настройки |
| --- | --- | --- |
| `ESP` | 2D ESP с рамкой: проецирует 8 интерполированных углов AABB на экран, вычисляет прямоугольник, рисует чёрную внутреннюю рамку + 4-сторонний градиент `ColorUtil.getColorStyle`. | MultiSetting targets: Игроков/Друзей/Меня/Предметы. Расширение углов 0.05/+0.15; чёрная рамка; градиент 0/90/180/270° |
| `NameTags` | 2D-нейметеги над игроками и предметами: фон, иконка клиент-юзера, префикс [F], имя+HP (через `nameProtect`), тотем/голова в левой руке, иконки брони/предмета, короткие коды зачарований, эффекты с длительностью; для предметов — имя+количество и превью содержимого шалкера. | Items mode (для предметов). Фильтр `IMPORTANT_ENCHANTS` (EN+RU). Шрифты durman[13/14], sf_bold[15]; health≥300 → 'Unknown' |
| `HUD` | Главный HUD клиента из 9 элементов, каждый позиционируется своим якорем `Dragging`. WaterMark ('ExosWare 1.21.4' + user/FPS/ping/BPS), TargetHUD (из `attackAura.target`, EaseBackIn, голова, градиентный HP+абсорбция), StaffList (скан скорборда/таблиста, флаг стаффа по `PREFIX_MATCHES`/`STAFF_MANAGER`), PotionHUD, ItemCoolDownHUD, Coordinates/TPS, ArmorHUD, Notifications. | MultiSetting 9 элементов: WaterMark/TargetHUD/KeyBinds/StaffList/PotionHUD/ItemCoolDownHUD/Coordinates+TPS/ArmorHUD/Notifications. hudColor mode (тема). `NAME_PATTERN ^\w{3,16}$` |
| `ClickGUI` | Render-модуль, привязанный к `GLFW_KEY_RIGHT_SHIFT`, держит настройки кастомизации ClickGUI и тему; `getGuiColor()`. `onEvent` пустой; `onEnable` вызывает `setState(false)` (момент. тоггл, открывает экран). | тема (Светло-чёрная/Тёмная), alpha, blur + blur element MultiSetting, module strike/filling/rounding/alphaModules |
| `TargetESP` | 3D-индикатор на `attackAura.target`. EaseInOutQuad(800) fade. | Mode Маркер/Маркер2/Призраки/Кружок. Призраки: 3 кольца firefly; Маркер: вращающийся текстурный quad; Кружок: TRIANGLE_STRIP-цилиндр. Вспышка попадания → RED. `SCALE_CACHE[101]` |
| `BlockESP` | 3D-обводка блоков: перебирает кубический радиус вокруг игрока и рисует `drawHoleOutline` для сундуков, печей/коптилен, спавнера, варочной стойки, эндер-сундука, рельсы-детектора; поддерживает runtime-карту `customBlocks`. | radius 1–30 (по умолч. 20). Жёсткие ARGB-цвета на тип. O(r³) скан каждый кадр |
| `CrossHair` | Кастомный прицел: `render(DrawContext)` (вызывается извне) рисует 4 скруглённых прямоугольника с зазором, растущим по кулдауну атаки, и краснеет при наведении на `EntityHitResult`. | attack size 0–20, indent 0–5, line height 2–10, thickness 2–4 |
| `Trails` | 3D-след за игроками. Хранит точки через миксин-аксессор `IEntity#...getTrails()`. Рендерит TRIANGLE_STRIP-ленту с alpha по возрасту. | MultiSetting Игроков/Друзей/Меня. trailLifetimeMs=250, minDistance 0.01; друзья зелёные |
| `Particles` | 3D-текстурная система частиц с двумя пулами: мировые (амбиентные в кубе 48 блоков) и урон-частицы (на `EventAttack` у цели, столкновение с блоками). 11 текстур. | Mode (Корона/Доллар/Светлячок/Сердце/Молния/Линия/Точка/Ромб/Снежинка/Искра/Звезда), count, size, sila, speedMultiplier, lifetime. Внутренние классы Damage/World |
| `Arrows` | 2D-стрелки за кадром, указывающие на игроков: вычисляет азимут относительно yaw, сглаживает угол, рисует triangle.png по анимированному радиусу. Пропускает ботов через `antiBot.check`. | radius 50–160 (по умолч. 70), +20 при спринте если dynamic. Друзья зелёные, цель красная |
| `AspectRatio` | Заглушка/маркер смены соотношения сторон; `onEvent` пустой — масштабирование делает миксин, читающий настройки. | Mode 4:3/16:9/1:1/16:10/Кастомный + custom slider |
| `BlockHighLight` | 3D-подсветка блока под прицелом: при `BlockHitResult` рисует обводку формы блока через `Render3DUtil.drawShapeAlternative`. | Цвет `ColorUtil.getColorStyle(360)`, width 2, filled+outline |
| `Breadcrumbs` | 3D-круги-следы при ходьбе по земле. Спавн с троттлингом 150ms, fade за 1200ms, expire 8000ms, EaseBackIn(400). | Текстура circles5.png; градиент 0/90/180/270 |
| `ExtraTab` | Заглушка («больше игроков в табе»); `onEvent` пустой, поведение в миксине, читающем enabled-состояние. | Без логики в файле |
| `FullBright` | Принудительная яркость через бесконечный NIGHT_VISION (duration −1, amplifier 255) на `EventUpdate`; снимает при disable. | Клиентский эффект на `mc.player` |
| `ItemPhysic` | Заглушка/маркер физики выброшенных предметов; `onEvent` пустой — рендер делает миксин, читающий mode. | Mode Обычная/2D |
| `JumpCircles` | 3D-круги при отрыве отслеживаемого игрока от земли (прыжок). Детектирует переход ground→air, спавнит EaseBackIn-круг, рендерит вращающийся текстурный билборд. | MultiSetting Игроков/Друзей/Меня, scale, circleType (circle/circle2/circle3). Fade 8000ms |
| `LittlePet` (класс `LittleSnickers`) | Спавнит клиентскую фейк-сущность `GhostWolfEntity`, следующую за игроком с самописным пасфайндингом (шаг вверх, детект края, восстановление при застревании). Регистрирует `ClientTickEvents.END_CLIENT_TICK`, приручает на UUID игрока. Опционально гонится за последней целью `EventAttack` в пределах 12 блоков. | «Бегать за таргетом». Speed 0.21/0.91 трение, 0.5 прыжок, gravity −0.08 |
| `NoRender` | Заглушка MultiSetting, перечисляющая подавляемые эффекты; `onEvent` пустой — миксины читают `mods.get(...)`. | MultiSetting: Тряска камеры/Огонь/Вода на экране/Удушье/Скорборд/Плохие эффекты (по умолч. список без 'Скорборд') |
| `Prediction` | Предсказатель траектории эндер-перлов: симулирует каждый `EnderPearlEntity` (MAX_STEPS=150) с гравитацией и сопротивлением, рисует градиентный LINES-трейл и точки приземления; в 2D — метку '%.1f сек' и иконку перла. | Опц. обводка зоны приземления. `ENDER_PEARL_STACK`, читает `Manager.SYNC_MANAGER.getEntities()` |
| `ShulkerPreview` | Помощник превью содержимого шалкера; `onEvent` пустой; предоставляет `isShulkerBox`/`getItems`/`drawPreview`, вызывается из миксинов инвентаря/тултипа. | Сетка 9 предметов; держит собственный `MinecraftClient.getInstance()` |
| `SwingAnimations` | Кастомная анимация предмета в первом лице: `renderFirstPersonItem(...)` (из миксина `HeldItemRenderer`) воспроизводит ванильные трансформы, фаерит `EventHeldItemRenderer`, для основной руки применяет стиль свинга. | Стили свинга Smooth/Block/ToBack/SelfBack/360/Down/Glide/DropDown/DeadCode (corner/slant для DropDown), slowAnimation/speed. Использует `viewModel` |
| `ViewModel` | Чистый модуль-настройки: шесть слайдеров позиций, читаемых `SwingAnimations.renderFirstPersonItem` для перепозиционирования предмета в руке. `onEvent` пустой. | right/left X/Y/Z (по умолч. right 0.6/−0.6/−0.8) |
| `World` | Контроль окружения мира: отменяет `WorldTimeUpdateS2CPacket` при оверрайде времени, принудительно `setTime` (День1000/Ночь13000/Утро0/Восход23000/Кастомное) и погоду (Ясно/Дождь/Гроза), переопределяет цвет тумана и дистанцию в `EventFog`. | Время/погода (см. описание), цвет тумана (тема). Сопряжён с `Manager.STYLE_MANAGER` |

> Вспомогательный класс `littlePet/GhostWolfEntity` — подкласс `WolfEntity` для `LittlePet`: `getBaseDimensions` фиксированы (0,0) (без хитбокса), `isAttackable()=false`, `canHit()=false`, поэтому фейк-питомец нельзя ударить/таргетить. Не является `Function`.

## Player

Модули инвентаря/эксплойтов/QoL. Реагируют через единый `onEvent(Event)` с проверками `instanceof`; читают живое состояние из `mc` и менеджеров (`FRIEND_MANAGER`, `SYNC_MANAGER`, `CHESTSTEALER_MANAGER`, `FUNCTION_MANAGER.attackAura`). Два мульти-клиентских эксплойта (`InvseeExploit`, `RegionExploit`) используют файловую систему как IPC.

| Модуль | Назначение | Ключевые настройки |
| --- | --- | --- |
| `ChestStealer` | Авто-кража из `GenericContainerScreen` через QUICK_MOVE. Режим Умный фильтрует через `Manager.CHESTSTEALER_MANAGER.isAllowed`. Пропускает контейнеры из чёрного списка заголовков. | mode (Обычный/Умный), stealDelay (по умолч. 120, 0=всё мгновенно). Блок-лист: Аукцион/Warp/Варпы/Меню/Выбор набора/Кейсы/Магазин |
| `AutoTool` | При атаке по блоку `getBest(pos)` выбирает быстрейший инструмент хотбара (Efficiency + скорость добычи), свапает слот, восстанавливает через 200ms. Пропускает инструменты с прочностью ≤10. Публичные static `itemIndex`/`getBest`. | Без пользовательских настроек |
| `EnderChestExploit` | Держит открытым 27-слотовый контейнер, клиентски скрывая GUI (`currentScreen=null`, lockCursor) для сохранения доступа; по бинду 'Кнопка сейва' выгружает предметы через QUICK_MOVE. | bind (`BindSetting` −1). Магия слотов: ≥27, ≤63, slots−36==27 |
| `InvseeExploit` | Двух-инстансный invsee (Sender/Receiver). Receiver периодически выполняет 'invsee <target>' (2000ms), пишет предметы в `InvseeExploitItems.ew`; Sender пишет цель в `InvseeExploitName.ew` и рисует инвентарь оппонента в перетаскиваемом HUD. Файловый IPC под `files/modules`. | mode (`ModeSetting`), debug. HUD через `Dragging` |
| `RegionExploit` | Двух-клиентский бот клейма WorldGuard-региона. Sender сохраняет координаты/радиус/друзей в `RegionExploitInfo.json`; Receiver (под `FileLock`) планирует //1,//2,/expand,region claim,region addmember с шагом 1с, имя региона = 'region_'+5 цифр. Парсит русский чат успеха/перекрытия. | minecraft (`ModeSetting`), sendKey (`BindSetting`), radius (по умолч. 10), removeOldRegion, addNearbyFriends |
| `FreeCamera` | Свободная/отсоединённая камера. Отменяет `PlayerMoveC2SPacket` и `EventMotion`, замораживает реальную позицию, интегрирует fakeX/Y/Z и yaw/pitch, отключает chunk culling, экспонирует интерполированные геттеры для рендера. Может отслеживать сущность. | speed (1f), yspeed (0.42f). `Type.Player` |
| `GuiWalk` | Движение при открытом GUI. Принудительно задаёт pressed-состояние биндов из сырого ввода; ChatScreen/SignEditScreen подавляют движение. FunTime-режим ставит `ClickSlot`-пакеты в очередь и сбрасывает их фоновым потоком после закрытия инвентаря (обход анти-чита). | bypass (`ModeSetting` Обычный/FunTime). keywords InventoryMove/GuiMove |
| `NoDelay` | Убирает кулдауны: обнуляет кулдаун прыжка (`MixinLivingEntityAccessor.setLastJumpCooldown`) и использования предмета (`MinecraftClientAccessor.setUseCooldown`) для END_CRYSTAL/EXPERIENCE_BOTTLE/BlockItem. | jump, xp, crystal (все true), place (false) |
| `GodMode` | Серверно-специфичный (warp farm) абуз. `onEnable` варпит и кликает слот 21, фейк-закрывает GUI; в PVP спамит клик по слоту 1 каждые 35ms; отменяет конкретные русские сообщения телепорта; периодическое предупреждение. Самоописан как вставленный код. | Без пользовательских настроек |
| `CustomCoolDown` | Клиентское принуждение кулдаунов для золотого яблока (4.6с)/эндер-перла (13.5с)/хоруса (3.5с); очищает использование предмета, пока он на отслеживаемом кулдауне. | items (`MultiSetting`), appleTime, pearlTime, horusTime, PVPonly |
| `AutoAccept` | Авто-принятие tpa-запросов. Парсит русское 'хочет телепортироваться к вам', извлекает/нормализует имя по таблисту, шлёт 'tpaccept'. Опц. только друзья. | onlyFriend (true). keywords TpaAccept |
| `AutoLeave` | Авто-отключение/побег при низком HP или близком игроке. Действия: disconnect / /hub / /home <name>. Опц. защита 'не выходить в PVP' через `ClientManager.playerIsPVP()`. | mode, heal (3), radius (60), run, homeName (home), pvpNoLeave |
| `AutoMessage` | Авто-сообщение в чат после убийства / во время цели / по задержке, с заменой `%target%` на имя цели (фолбэк 'хряк'). Читает цель `AttackAura`. | mode, timer (5000), text ('Привет %target%!') |
| `AutoRespawn` | Авто-возрождение на `DeathScreen` (`requestRespawn` + очистка экрана); опц. шлёт '/home <name>' при совпадении русских death-строк. | autohome (true), home (home) |
| `ClickAction` | Без runtime-логики; экспонирует флаг типа сервера (ReallyWorld/FunTime/HollyWorld) через `nonBatch()`/`batch()`, чтобы другие модули выбирали стратегию свапа/клика. | type (`ModeSetting`) |
| `HighJump` | Принудительно задаёт вертикальную скорость каждый motion-тик (sila по умолч. 2.0); `MoveUtil.setMotion` при спринте; авто-закрывает `ShulkerBoxScreen` через 800ms. Аннотирован `Type.Move`, несмотря на пакет. | sila (2.0) |
| `ItemFixSwap` | Отменяет входящий `UpdateSelectedSlotC2SPacket` (received), чтобы предотвратить серверную смену слота хотбара / десинк от анти-чита. | Без настроек; keywords NoSlotChange/NoServerDesync |
| `ItemScroller` | Маркер-модуль: пустой `onEvent`; объявляет только слайдер 'Задержка'. Быстрое перемещение предметов реализовано вне класса. | scroll (100) |
| `MiddleClickFriend` | Средний клик (кнопка 2) по сущности-игроку добавляет/убирает его в `Manager.FRIEND_MANAGER` с подтверждением в чат. | Без настроек; keywords MCF |
| `MiddleClickPearl` | Бросок эндер-перла по среднему клику или бинду. Шлёт LookAndOnGround-пакет при наличии цели `AttackAura`, затем `InventoryUtil.inventorySwapClick2`. Учитывает кулдаун перла. | mode, bind, inventoryUse (true). keywords MCP |
| `NoInteract` | Маркер: предотвращает открытие контейнеров правым кликом; пустой `onEvent`, принуждение в миксине. | onlyAura (false) — 'Только с AttackAura' |
| `NoPush` | Маркер: убирает коллизию/отталкивание от выбранных источников; пустой `onEvent`, принуждение в миксинах. | mods (`MultiSetting`, по умолч. Игроки+Блоки): Вода/Игроки/Блоки |
| `NoRayTrace` | Маркер: убирает хитбокс/raytrace сущностей; пустой `onEvent`, принуждение в миксине. | keywords NoEntityTrace. Без настроек |
| `PerfectTime` | Авто-релиз трезубца на `TridentItem.MIN_DRAW_DURATION` и арбалета на полном натяге (maxUseTime−1) через RELEASE_USE_ITEM PlayerAction и `stopUsingItem`. | Без настроек |

## Misc

Разнородные утилиты/читы: анти-скриншер (`UnHook`), спуфинг имени (`NameProtect`), Xray, Discord RPC, серверно-специфичные хелперы (HW/FT/RW/Elytra), автоматизация дуэлей и сетевые/IRC-интеграции.

| Модуль | Назначение | Ключевые настройки |
| --- | --- | --- |
| `UnHook` | Анти-скриншер / самоуничтожение: при включении открывает `UnHookScreen`, `onUnhook()` отключает ВСЕ активные функции (сохраняя их в static `functionsToBack` для восстановления), скрывает папку `C:\ExosWare` через `DosFileAttributeView`, выключает себя. Экран также ставит `ClientManager.legitMode=true`. | bind по умолч. INSERT ('Кнопка возврата'); keywords SelfDestruct |
| `NameProtect` | Спуфинг имени: заменяет реальный ник (опц. и друзей) на настраиваемый фейк в чате/HUD/нейметегах; `&`→§. Читается миксином `MixinTextVisitFactory.protect`, `HUD`, `NameTags`. | Скрывать друзей. Фейк по умолч. 'levin1337' |
| `Optimizer` | Производительность: периодический `System.gc()` каждые 300000ms, принудительно FAST-графика + облака OFF, выключает vsync, MaxFps 260. | Магия: 300000ms GC, 260 fps cap |
| `Xray` | Обводит руды в радиусе (по умолч. 20, нижняя граница жёстко pos−30) через `drawHoleOutline` по типу руды. | Per-ore тоглы (netherite/diamond/emerald/gold/iron/coal/redstone/lapis), radius |
| `ClientSounds` | Только-настройки заглушка: MultiSetting ('Вход в клиент'), 4 типа звуковых режимов, слайдер громкости; `onEvent` пустой (воспроизведение — в другом месте). | MultiSetting, 4 sound mode, volume |
| `IRC` | Тоггл кросс-клиентского чата; единственный оверрайд — `onDisable` → `Manager.IRC_MANAGER.shutdown()`. Логика в `IRC_MANAGER`/`IrcCommand`. | — |
| `AutoDuel` | Спамит 'duel <name> [money]' всем онлайн-игрокам, авто-выбирает слот кита по режиму, авто-подтверждает GUI дуэли; авто-офф при телепорте на 500 блоков или чате старта дуэли. | Русские method-имена `xuesos()`/`pidor()`; slowTime 300–1000ms; kit slotIDs 0–8 |
| `AutoDuelBot` | Рекламирует 'Кидайте дуель...' в локальный/глобальный чат каждые `messageDelay`; парсит '➝ Ник:'/'➝ Ставка:' и авто-запускает '/duel accept <nick>' если ставка в диапазоне. | desc='Тест'; диапазон ставки 1000–1000000 |
| `DiscordRCP` | Discord Rich Presence через daemon-поток, обновляющий присутствие каждые 2с (имя/роль пользователя + кнопки покупки/телеграм). | App id 1384873696375603281; поток 'TH-RPC-Handler'; ссылки exosware.ru/t.me/exosware |
| `ServerRPSpoff` | Перехватывает `ResourcePackSendS2CPacket`, отвечает ACCEPTED затем SUCCESSFULLY_LOADED, отменяет оригинальное событие, чтобы серверный пак не загрузился. | desc пустой |
| `HWHelper` | Хелпер HollyWorld: бинды для trapka/explosive trapka/stun/snowball/babax на предметы (POPPED_CHORUS_FRUIT, PRISMARINE_SHARD, NETHER_STAR, SNOWBALL, FIRE_CHARGE); опц. 'Обход' подавляет клавиши движения ~90–150ms вокруг свапа. | Бинды + 'Обход'. Тайминг 90ms свап / 150ms восстановление |
| `FTHelper` | Хелпер FunTime: бинды для trapka/disorientation/plast/godaura на NETHERITE_SCRAP, ENDER_EYE, DRIED_KELP, PHANTOM_MEMBRANE; `InventoryUtil.use` со слотом хотбара +36. | Бинды на предметы |
| `RWHelper` | RW DragonFly: при `abilities.flying` задаёт `MoveUtil` speed 1 и Y-скорость ±0.5/±0.25 от прыжка/приседа. | `BindBooleanSetting` 'DragonFly' |
| `ElytraHelper` | Бинды свапа нагрудник↔элитра (3-кликовая SWAP-последовательность в слот брони 6), бинд фейерверка и авто-взлёт. При включении предупреждает выставить ViaFabricPlus 1.17. | Бинды свапа/фейерверка/взлёта |
| `DeathCoords` | При смерти (`DeathScreen` + health<1, deathTime<1) печатает X/Y/Z в клиентский чат. | — |
| `Globals` | Запускает локальный сервер `ClientAPI` для обнаружения других юзеров клиента на сервере; кэширует `isClientUser` по UUID каждые 30 тиков. | Жёстко: порт 13599, версия '1.4.3' |
| `NoCommands` | Пустой класс; фактическая блокировка команд через точку реализована в миксине. | desc='Отключение команд через точку' |
| `TPLoot` | Телепорт игрока к лежащим ценным `ItemEntity` через `PlayerMoveC2SPacket.Full`, затем опц. /hub, /spawn, home или кастомная команда после получения лута. | Список ценностей (мечи/головы/броня/тотем/перл/кристалл/фейерверк/элитра/гапплы и т.д.); 100ms таймер после получения |

> Вспомогательный класс `misc/autoduel/Counter` — секундомер на `System.currentTimeMillis()` (`create()`/`reset()`/`elapsedTime()`/`hasReached(long|double)`/`delay()`/`isStarted()`), используемый `AutoDuel`. Отдельный от `util.player.TimerUtil`.

---

## Заметка об отключённых / закомментированных модулях

В конструкторе `FunctionManager` присутствуют закомментированные регистрации — функции, отключённые, но не удалённые из кодовой базы. На основании анализа это:

| Модуль | Категория (исходная) | Статус / примечание |
| --- | --- | --- |
| `GodMode` | Player | Закомментирован в списке регистрации `FunctionManager`. Серверно-специфичный (warp farm) абуз; `@FunctionAnnotation.desc` буквально гласит 'ГодМод а катлаван ебаная паста' (признание вставленного кода). |
| `KTLeave` | Misc | Закомментирован. Boat/vehicle out-of-bounds полёт ('Calestium'-стиль): Motion/Packet-режимы, phase/noClip, спуф гравитации, automount, отмена/лимит пакетов, jitter. desc='вы стали калестиум юзером'. `teleportToGround` сканирует до 255 блоков; планировщик не останавливается при disable. |
| `MaceExploit` | (Combat) | Закомментирован в списке регистрации `FunctionManager`. Деталей реализации в анализах нет — присутствует только как отключённая запись. |
| `ShulkerPreview` | Render | Указан как закомментированный в `FunctionManager`. При этом класс существует и используется из миксинов инвентаря/тултипа (`isShulkerBox`/`getItems`/`drawPreview`); как самостоятельный пункт меню он отключён. |

Эти модули перечислены в исходниках, но фактически исключены из активной регистрации. При продолжении проекта следует учитывать их серверную привязку и упомянутые проблемы (утечка потоков планировщика у `KTLeave`/`AutoExplosion`, вставленный сторонний код у `GodMode`).
