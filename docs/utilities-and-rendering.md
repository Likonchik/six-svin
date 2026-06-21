# Утилиты и рендеринг

Документ описывает подсистему `ru.levin.util.*` — набор низкоуровневых вспомогательных библиотек, на которые опираются все модули клиента ExosWare (HUD, ESP, `AttackAura`, `TargetStrafe`, `Trails` и т. д.), а также пользовательские GLSL-шейдеры, входящие в ресурсы клиента (namespace `exosware`).

Все классы этого слоя — статические/`@UtilityClass` помощники без собственного состояния (за исключением кэшей и нескольких глобальных матриц). Большинство реализует маркер-интерфейс `IMinecraft`, чтобы получить общий статический доступ к `MinecraftClient mc`. Тексты, имена и сообщения в коде — на русском языке.

---

## Содержание

- [Архитектурный обзор](#архитектурный-обзор)
- [Пакет animations — анимации и сглаживание](#пакет-animations--анимации-и-сглаживание)
- [Пакет color — работа с цветом](#пакет-color--работа-с-цветом)
- [Пакет math — математика и рейтрейс](#пакет-math--математика-и-рейтрейс)
- [Пакет move — движение и сеть](#пакет-move--движение-и-сеть)
- [Пакет player — игровые помощники](#пакет-player--игровые-помощники)
- [Пакет render — рендеринг 2D/3D](#пакет-render--рендеринг-2d3d)
- [Пакет vector — проекция мир→экран](#пакет-vector--проекция-мирэкран)
- [Прочее (KeyMappings, IEntity)](#прочее-keymappings-ientity)
- [Пользовательские GLSL-шейдеры](#пользовательские-glsl-шейдеры)
- [Ключевые «магические» константы](#ключевые-магические-константы)

---

## Архитектурный обзор

Утилиты компонуются модулями каждый тик/кадр. Основные потоки данных:

```
Боевой поток:
  AuraUtil / RayTraceUtil  → расчёт вектора цели и проверка видимости
        (MathUtil.getClosestVec)
  → GCDUtil                → квантование поворота под чувствительность мыши
  → NetworkUtils.sendSilentPacket  → отправка пакета (флаг sendingSilent для миксинов)
  → InventoryUtil          → тихая смена слота / использование предмета

Рендер-поток:
  RenderUtil.render3D.setTranslation  → захват матриц projection/model/world (каждый кадр)
  → VectorUtil / Render3DUtil          → проекция world→screen
  → RenderUtil (шейдеры)               → 2D-виджеты (rounded rect, blur, glass)
  → Render3DUtil                       → очередь 3D-боксов/линий, flush в onWorldRender

Цветовой поток:
  Manager.STYLE_MANAGER (тема) → ColorUtil.getColorHud → HUD/ESP

Анимации:
  TimerUtil (мс)  → Easing-кривая  → значение для UI
```

Зависимости от других подсистем:

| Связь | Назначение |
|---|---|
| `IMinecraft` (`mc`) | общий доступ к `MinecraftClient` |
| `Manager.STYLE_MANAGER` | цвета темы для `ColorUtil.getColorHud` |
| `Manager.FUNCTION_MANAGER` | `clientSounds.volume`, `targetStrafe.predict`, `attackAura.target` |
| `Manager.SYNC_MANAGER` | `getItems()` и снимки сущностей |
| `EventKeyBoard` | вход для `MoveUtil.fixMovement` |
| `EventRender3D` | вход для `Render3DUtil` / `RenderAddon` |
| `ClientPlayerInteractionManagerAccessor`, `GameRendererAccessor` | accessor-миксины |
| namespace `exosware` | звуки, шейдеры (`core/...`), текстуры (частицы, ESP, плащи, HUD) |

---

## Пакет animations — анимации и сглаживание

Анимации управляются временем через `TimerUtil` и применяют функции сглаживания (easing) к нормализованному прогрессу.

### `Animation` (абстрактный класс)

Базовый класс анимации, основанной на времени. Хранит длительность в миллисекундах, конечную точку (`endPoint`) и направление `AxisDirection` (`POSITIVE`/`NEGATIVE`). Методы `getOutput()` / `getEndput()` вычисляют сглаженное значение по уравнению `getEquation()` относительно `TimerUtil` за заданную длительность. Хук `correctOutput()` управляет обработкой обратного хода.

> Внимание: метод `setDirection(boolean forwards)` содержит баг — обе ветви присваивают `AxisDirection.POSITIVE`, флаг `forwards` фактически игнорируется.

### `Easing` (enum)

Таблица из ~30 функций сглаживания, каждая — `Function<Double, Double>`: `linear`, `quad`/`cubic`/`quart`/`quint`, `sine`, `expo`, `circ`, `back`, `elastic`, `sigmoid`, плюс собственная `SHRINK_EASING`. `toString()` форматирует имена для выпадающих списков UI. Константа отскока `back` = `1.70158` (и `*1.525`), `SHRINK_EASING.easeAmount` = `1.3`.

### `Direction` (enum)

`FORWARDS` / `BACKWARDS` с методом `opposite()`. Отдельный от `net.minecraft` `AxisDirection`, используемого в `Animation`.

### Реализации (`animations.impl.*`)

| Класс | Назначение |
|---|---|
| `DecelerateAnimation` | замедляющаяся кривая; нормализует `x` по длительности |
| `EaseBackIn` | переопределяет `correctOutput()` = `true`, принимает `easeAmount` |
| `EaseInOutQuad` | квадратичный вход/выход |

---

## Пакет color — работа с цветом

Цвет повсюду упакован в целочисленный ARGB; константы темы берутся из `Manager.STYLE_MANAGER`.

### `ColorUtil` (`util.color`)

Основные ARGB-помощники:

- Геттеры компонентов: `getRed` / `getGreen` / `getBlue` / `getAlpha`.
- Альфа: `applyAlpha`, `withAlpha`, `reAlphaInt`.
- Смешивание: `blendColors`, `blendColorsInt`, `interpolateColor`.
- `gradient(speed, index, colors)` — анимированный градиент на базе `System.currentTimeMillis()`.
- `getColorHud()` — цвет темы через `Manager.STYLE_MANAGER` (первый/второй цвет); по этому пути окрашивается весь клиент (HUD/ESP) через `getColorStyle`/`getColorHud`.
- Семплирование пикселей изображения с кэшем.
- Константы `hud_color` / `hud_color2`.

### `ColorUtilTest` (`util.render`)

Фабрика цвета (`@UtilityClass`) с TTL-кэшем:

- Кэш на `ConcurrentHashMap` + `DelayQueue` с TTL 60 c; фоновый `ScheduledExecutorService` чистит его раз в секунду (живёт всё время работы JVM).
- `rainbow`, `fade`, `overCol`, `multAlpha`, `multDark`, `multBright`.
- Карта `§`-кодов Minecraft (16 записей).
- Константы темы GUI (`0x18181D` и т. д.).
- `removeFormatting` — снятие `§`-форматирования через regex.
- `formatting` оборачивает цвет в служебные символы-сентинелы `⏏...⏏` (собственная кодировка цвета текста).

### `ColorRGBA` (`util.render`)

Неизменяемый value-объект RGBA с клампингом компонентов. Ленивое вычисление HSB (`getHue` / `getSaturation` / `getBrightness`, где brightness — максимальная компонента HSB) и метод `difference()` для сравнения цветов.

---

## Пакет math — математика и рейтрейс

### `MathUtil`

Интерполяция с учётом дельты FPS и геометрия хитбоксов:

- `deltaTick()` = `1 / fps`.
- `fast` / `lerp` / `faster` — FPS-зависимая интерполяция.
- `random` / `getRandom`.
- `interpolate` / `interpolateInt` / `interpolateFloat` / `interpolateAngle`.
- `smoothstep`.
- `getClosestVec` / `getStrictDistance` — клампинг позиции глаз к AABB сущности (для расчёта дальности атаки).
- Перегрузки `clamp`.

### `RayTraceUtil`

Рейкастинг и трекинг попаданий:

- `getMouseOver`, `rayCastEntity` (`ProjectileUtil.raycast`), `rayCastEntities`, `rayCast` (блок).
- Hit-flash: `markHit` / `getHitProgress` — карта по `UUID` с длительностью эффекта `EFFECT_DURATION` = 200 мс (используется для подсветки цели при ударе, например в `TargetESP`).
- Константа перевода градусов в радианы `0.017453292`.

---

## Пакет move — движение и сеть

### `MoveUtil`

- `isMoving` — есть ли горизонтальное движение.
- `isInWeb` — сканирование AABB на наличие паутины.
- `setSpeed` / `setMotion` — установка скорости (нормализация страйфа на `yaw ± 45°`).
- `direction()`, `getSpeed` (через `Math.hypot`), `forward()`.
- `fixMovement(EventKeyBoard, ...)` — коррекция страйфа: перебором сетки 3×3 (forward/strafe) подбирает легитимную пару ввода, ближайшую к нужному углу движения (нужно для байпасов на основе направления / silent-aim).

### `NetworkUtils` (`@UtilityClass`)

Отправщик пакетов с флагом «тихой» отправки:

```java
public void sendSilentPacket(Packet<?> packet) {
    try {
        sendingSilent = true;
        mc.player.networkHandler.sendPacket(packet);
    } finally {
        sendingSilent = false;
    }
}
public boolean isSendingSilent() { return sendingSilent; }
```

Флаг `sendingSilent` читается миксинами, чтобы подавить реакцию клиента на самостоятельно отправленные пакеты (тихие aura/inventory). Это служебная часть анти-детекта.

---

## Пакет player — игровые помощники

### `AuraUtil`

Боевая математика цели:

- `getVelocityTowards(...)` — страйф вокруг цели со смещением предсказания из `Manager.FUNCTION_MANAGER.targetStrafe.predict` (опционально с учётом скорости передвижения по умолчанию).
- `getVector(...)` — лучшая точка из 10 вертикальных шагов, клампленная к половине ширины хитбокса.
- `getDistance`, `getArmor`.

### `GCDUtil`

Квантование поворота под GCD (наименьший шаг) чувствительности мыши клиента — техника обхода анти-чита (silent-aim выглядит как легитимный ввод):

```java
public static float getGCD() {
    float sens = (float) (mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2);
    return sens * sens * sens * 8.0f;          // (sens)^3 * 8
}
public static float getGCDValue() { return getGCD() * 0.15f; }
public static float getDeltaMouse(float delta, float gcdValue) {
    return Math.round(delta / gcdValue);
}
```

Дельта поворота квантуется как `round(delta / gcd) * gcd`, чтобы синтетический поворот совпадал с допустимыми шагами реальной мыши.

### `InventoryUtil`

Клики/свопы слотов и тихое использование предметов:

- Обёртки `clickSlot` (`PICKUP` / `SWAP`), `moveToOffhand`.
- Поиск: `getItem`, `getPearls`, `getAxe`, `getItemSlot`, `getHotBarSlot`.
- `inventorySwapClick2` — тихое использование (например щита) с опциональным пакетом поворота, который отправляется **только** при наличии `attackAura.target` (связка инвентаря с боевым состоянием).
- `startFly` — пакет `START_FALL_FLYING`.
- Вложенный `TotemUtil`: `getSphere` (сканирование блоков сферой) и `getBlock`. `getDistanceOfEntityToBlock` использует `MathHelper.sqrt(float)` — потеря точности на дальних блоках.
- `SearchInvResult` — `record(slot, found, stack)` с общим `NOT_FOUND_RESULT(-1, false, null)`.

### `ServerUtil`

- `getHealth` — чтение HP из скорборда `BELOW_NAME` для жёстко заданных серверов (`reallyworld`, `playrw`, `saturn-x`, `skytime`, `space-times`); для остальных — `health` / `maxHealth`. Ломается, если эти серверы изменят формат.
- `isConnected(ip)`, `selectCompass`.

### `TimerUtil`

Простой миллисекундный секундомер, лежащий в основе анимаций и кулдаунов:

- `reset()`, `hasTimeElapsed(time[, reset])`, `getTime`, `setTime`, `getLastMS`.
- Публичное поле `lastMS`.

### `AudioUtil`

Воспроизведение клиентских звуков из ресурс-пака `exosware`:

- `playSound(name)` загружает ресурс `exosware:sounds/<name>`, декодирует через `AudioSystem` в отдельном потоке (`new Thread`).
- Устанавливает `MASTER_GAIN` из `20 * log10(volume)`, где `volume = clientSounds.volume / 100`.

---

## Пакет render — рендеринг 2D/3D

### `RenderUtil`

Шейдерный рендеринг 2D и проекция мира:

- Хит-тесты: `isHovered`, `isInRegion`.
- Альфа/прозрачность: `injectAlpha`, `applyOpacity`, `applyAlpha`.
- Примитивы через шейдеры `rectangle`/`border`: `drawRoundedRect`, `rectRGB`, `drawRoundedBorder`, `drawCircle`, `drawCircleBorder` (юниформы `Size` / `Radius` / `Smoothness` / `Thickness`).
- `drawBlur` — копирует основной FBO в мемоизированный временный `SimpleFramebuffer`, затем выполняет blur-шейдер.
- `drawLiquidRect` — стеклянный эффект (шейдер `glass`) с юниформами fresnel/distort, семплирует текстуру экрана.
- `drawTexture(String path, ...)` — строит `Identifier.of("exosware", path)` из сырой строки.
- Вложенный класс `render3D`:
  - `setTranslation` — захватывает матрицы projection/model/world (каждый кадр; их затем используют `VectorUtil` и `Render3DUtil`).
  - `worldSpaceToScreenSpace`, `matrixFrom`.
  - `drawShape`, `drawHoleOutline`, `renderFillBox`, `endBuilding`.

> Состояние матриц рендера хранится в глобальных мутабельных статиках (`lastProjMat` / `lastModMat` / `lastWorldSpaceMatrix`). Проекция в `VectorUtil.toScreen` и `RenderUtil` расходятся: одна использует только `positionMatrix`, другая перемножает `proj * model`.

### `Render3DUtil` (`@UtilityClass`)

Отложенная (retained-mode) очередь 3D-рендера:

- Списки `TEXTURE` / `LINE` / `QUAD` (+ варианты `_DEPTH` с тестом глубины); элементы — record'ы `Texture` / `Line` / `Quad`.
- `drawBox`, `drawLine`, `drawQuad`, `drawTexture`, `drawShape` буферизуются, а `onWorldRender` сбрасывает их пакетами, сгруппированными по id текстуры / ширине линии, с блендингом `ONE_MINUS_CONSTANT_ALPHA`.
- `drawShape` при **первом** вызове только кэширует боксы фигуры и ничего не рисует — отрисовка начинается со следующего вызова (ленивое заполнение).
- `canSee` (фрустум), `getNormal`. Использует `ColorUtilTest.multAlpha`.

### `Scissor`

Стековое GL-отсечение (clipping) с трансляцией:

```java
public static void setFromComponentCoordinates(double x, double y, double width, double height) {
    double scale = mc.getWindow().getScaleFactor();
    set((int)(x * scale),
        mc.getWindow().getHeight() - (int)((y + height) * scale),
        (int)(width * scale), (int)(height * scale));
}
```

- `push` / `pop` / `set` / `unset` / `translate`; стек ёмкостью 8 (`State`).
- `set` пересекает текущий прямоугольник с целевым через `java.awt.Rectangle.intersection`, клампит ширину/высоту до неотрицательных.
- `setFromComponentCoordinates` учитывает `getScaleFactor()` окна и переворот оси Y.

### `ShaderManager`

Низкоуровневые эмиттеры вершин:

- `vertexShader` — квады `POSITION_COLOR` (1 цвет или 4 цвета по углам).
- `vertexLine` — нормализованная 3D-линия.
- `getNormal`. Используется шейдерными вызовами `RenderUtil`.

### `RenderAddon`

Рендеры более высокого уровня:

- `renderFakePlayer` — «призрак» (`AbstractClientPlayerEntity`) с фиксированной альфой `0.35` и уровнем света `15728880` для fakelag/спуфинга оборонительной позиции.
- `renderItem`, `renderPlayerItems`, `drawHead`, `drawStaffHead` (собственный head-шейдер, тинт урона).
- `sizeAnimation`.

### `ResourceProvider` (`render.providers`)

Реестр ресурсов рендера:

- `ShaderProgramKeys` для `texture` / `rectangle` / `blur` / `border` / `glass` (`exosware:core/...`).
- `Identifier`'ы для частиц, маркеров `TargetESP`, плащей/элитр (`CUSTOM_CAPE` / `CUSTOM_ELYTRA`), контейнера HUD, изображения выбора цвета.

---

## Пакет vector — проекция мир→экран

### `VectorUtil`

- `toScreen` — использует статические `lastWorldSpaceMatrix` + `previousProjectionMatrix`, отбрасывает точки с `z > 0`.
- `project` — сопряжение поворота камеры + компенсация покачивания обзора (view-bobbing: `strideDistance`, вертикальная/боковая скорость) при включённой опции `bobView`; FOV через `GameRendererAccessor`.
- `getInterpolatedPos` — интерполированные позиции сущности.

### `EntityPosition`

Подкласс `Vector3d`, интерполирующий `lastRenderX/Y/Z` сущности к текущей позиции по partial tick, со смещением по высоте. Фабрика `get()`.

---

## Прочее (KeyMappings, IEntity)

### `KeyMappings` (`util`)

Двусторонняя карта кодов клавиш GLFW ↔ имён:

- `getAllKeys`, `keyMappings(int)` → имя, `keyCode(name)` → код.
- Кнопки мыши кодируются как отрицательные коды: `-GLFW_MOUSE_BUTTON - 2` (`MOUSE1`–`MOUSE8`).
- Фолбэк через reflection по полям `GLFW_KEY_<NAME>`.

### `IEntity` (`util`)

Duck-type интерфейс, подмешиваемый в сущности миксином для модуля `Trails`:

- `exosWareFabric1_21_4$getTrails`
- `exosWareFabric1_21_4$getLastTrailPos`
- `exosWareFabric1_21_4$setLastTrailPos`

---

## Пользовательские GLSL-шейдеры

Шейдеры находятся в `src/main/resources/assets/exosware/shaders/` и собираются в namespace `exosware` (`#version 150`). Общий include — `shaders/include/common.glsl` (содержит, в частности, функцию `ralpha(...)` для скруглённой альфы). Каждый core-шейдер представлен тройкой `*.vsh` / `*.fsh` / `*.json`.

```
assets/exosware/shaders/
├── core/
│   ├── blur.{vsh,fsh,json}
│   ├── border.{vsh,fsh,json}
│   ├── glass.{vsh,fsh,json}
│   ├── rectangle.{vsh,fsh,json}
│   ├── texture.{vsh,fsh,json}
│   └── glass/
│       ├── data.json
│       ├── fragment.fsh
│       └── vertex.vsh
└── include/
    └── common.glsl
```

| Шейдер | Юниформы | Назначение |
|---|---|---|
| `rectangle` | `Size`, `Radius` (vec4 — радиус на угол), `Smoothness` | Скруглённый прямоугольник; альфа из `ralpha(...)`, отсечение `discard` при `alpha < 0.001` |
| `border` | (рамочные параметры + толщина) | Скруглённая рамка (контур) |
| `texture` | `Sampler0` | Отрисовка текстуры (`drawTexture`) |
| `blur` | `Sampler0`, `Size`, `Radius`, `Smoothness`, `BlurRadius` | Радиальный blur скруглённого прямоугольника: `STEPS = 16` направлений × `RADIAL_SAMPLES = 5`, итог делится на `STEPS*RADIAL_SAMPLES + 1`; затем умножается на `ralpha` |
| `glass` (core/`glass.fsh`) | `Sampler0`, `Resolution`, `blurAmount`, `reflect`, `noiseValue`, `Viewport` | Стеклянный эффект: 3D simplex-шум (`snoise`) искажает UV, затем blur (8 направлений × качество 3); вне `Viewport` пиксель проходит без изменений |

> Дубликат стекла: каталог содержит и плоский `glass.{vsh,fsh,json}`, и подпапку `glass/` (`vertex.vsh` / `fragment.fsh` / `data.json`) — два определения стеклянного шейдера.

Фрагмент ядра `rectangle.fsh` (показывает контракт `ralpha`):

```glsl
#moj_import <exosware:common.glsl>
uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
void main() {
    float alpha = ralpha(Size, FragCoord, Radius, Smoothness) * FragColor.a;
    if (alpha < 0.001) discard;
    OutColor = vec4(FragColor.rgb, alpha);
}
```

Шейдеры подключаются к рендер-миксинам `display.*` (`MixinGameRenderer`, `MixinInGameHud`, `MixinWorldRenderer`) и потребляются из `RenderUtil` через `ResourceProvider.ShaderProgramKeys` и эмиттеры вершин `ShaderManager`. Все `Identifier`/шейдеры обязаны поставляться в ресурс-паке `exosware`.

---

## Ключевые «магические» константы

| Константа | Значение | Где |
|---|---|---|
| Перевод град→рад | `0.017453292` | `RayTraceUtil` |
| `EFFECT_DURATION` (hit-flash) | 200 мс | `RayTraceUtil` |
| GCD чувствительности | `(sens*0.6+0.2)^3 * 8 * 0.15` | `GCDUtil` |
| Шаги выборки точки цели | 10 | `AuraUtil.getVector` |
| Отскок `back` | `1.70158` (`*1.525`) | `Easing` |
| `SHRINK_EASING.easeAmount` | `1.3` | `Easing` |
| Альфа fake-player | `0.35`, свет `15728880` | `RenderAddon.renderFakePlayer` |
| TTL цветового кэша | 60 с (очистка раз в 1 с) | `ColorUtilTest` |
| Стек `Scissor` | ёмкость 8 | `Scissor` |
| `blur`-шейдер | `STEPS = 16`, `RADIAL_SAMPLES = 5` | `blur.fsh` |
| `glass`-шейдер | 8 направлений × качество 3 | `glass.fsh` |

> Замечания по корректности: `setFromComponentCoordinates` зависит от `getScaleFactor()` (на разных DPI меняется); `Render3DUtil.drawShape` ничего не рисует на первом вызове; `ServerUtil.getHealth` жёстко завязан на строковые адреса серверов; `Animation.setDirection` фактически no-op; глобальные матрицы рендера — мутабельные статики, что требует строгого порядка вызовов `setTranslation` перед проекцией.
