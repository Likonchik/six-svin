# Графический интерфейс (ClickGUI, экраны, темы, шрифты)

Этот документ описывает весь визуальный и интерактивный слой клиента **ExosWare** (group `ru.levin`, archives name `exosware`, Minecraft 1.21.4 Fabric): выпадающее меню настроек `ClickGUI` и его систему рендереров настроек (`SettingRenderer`), отдельные экраны `AltManager`, `MainMenu`, `UnHookScreen`, систему тем (`StyleManager` / `Style`) и систему шрифтов (`FontUtils` / `RenderFonts`).

Весь GUI построен по принципу **immediate-mode** (немедленного рендеринга): каждый кадр экран перерисовывается с нуля собственными примитивами (скруглённые прямоугольники, blur, кастомный шрифт), без удерживаемого дерева виджетов Minecraft. Из-за этого расчёт раскладки (layout) дублируется между `render()` и обработчиками ввода (`mouseClicked` и т.д.).

---

## Содержание

- [Обзор архитектуры](#обзор-архитектуры)
- [ClickGUI — выпадающее меню](#clickgui--выпадающее-меню)
  - [Композиция дерева GUI](#композиция-дерева-gui)
  - [Раскладка и анимации](#раскладка-и-анимации)
  - [Поиск, тема, color picker](#поиск-тема-color-picker)
  - [Взаимодействие с функциями](#взаимодействие-с-функциями)
- [Система рендереров настроек (`SettingRenderer`)](#система-рендереров-настроек-settingrenderer)
  - [Контракт интерфейса](#контракт-интерфейса)
  - [Диспетчеризация по типу настройки](#диспетчеризация-по-типу-настройки)
  - [Каталог рендереров](#каталог-рендереров)
  - [Как рисуется одна настройка](#как-рисуется-одна-настройка)
- [Отдельные экраны](#отдельные-экраны)
  - [AltManager](#altmanager)
  - [MainMenu](#mainmenu)
  - [UnHookScreen](#unhookscreen)
- [Система тем (`StyleManager` / `Style`)](#система-тем-stylemanager--style)
- [Система шрифтов (`FontUtils` / `RenderFonts`)](#система-шрифтов-fontutils--renderfonts)
- [Замечания и подводные камни](#замечания-и-подводные-камни)

---

## Обзор архитектуры

| Класс | Путь | Роль |
|-------|------|------|
| `ClickGUI` | `screens/dropdown/ClickGUI.java` | Главное выпадающее меню (5 колонок-категорий), хранит экземпляры всех рендереров и диспетчеризует layout/render/input |
| `SettingRenderer<T>` | `screens/dropdown/SettingRenderer.java` | Обобщённый контракт рендеринга одной настройки |
| Рендереры `*SettingRenderer` | `screens/dropdown/impl/` | Семь конкретных рендереров — по одному на тип настройки |
| `DescriptionRenderQueue` | `screens/dropdown/DescriptionRenderQueue.java` | Отложенная очередь тултипов (z-order поверх панелей) |
| `SearchState` | `screens/dropdown/search/SearchState.java` | Состояние поля поиска |
| `AltManager` | `screens/altmanager/AltManager.java` | Менеджер альт-аккаунтов |
| `MainMenu` | `screens/mainmenu/MainMenu.java` | Кастомный главный экран вместо ванильного |
| `UnHookScreen` | `screens/unhook/UnHookScreen.java` | Экран «паники» / анхука |
| `StyleManager` | `manager/themeManager/StyleManager.java` | Хранилище и персист тем-градиентов |
| `Style` | `manager/themeManager/Style.java` | Неизменяемая тема: имя + пара цветов |
| `FontUtils` | `manager/fontManager/FontUtils.java` | Реестр семейств шрифтов как массивов `RenderFonts[256]` |
| `RenderFonts` | `manager/fontManager/RenderFonts.java` | Рантайм-растеризатор глифов (AWT → GPU) и рендерер текста |

Все экраны переиспользуют один и тот же набор примитивов рисования (`RenderUtil`, `Scissor`, `RenderAddon`, `ColorUtil`), анимации (`util.animations`) и шрифты (`FontUtils.<семейство>[размер]`).

---

## ClickGUI — выпадающее меню

`ClickGUI extends Screen implements IMinecraft` — это модуль настроек (категория → функция → настройка), перерисовываемый каждый кадр. Открывается биндом (по умолчанию `GLFW_KEY_RIGHT_SHIFT`, см. модуль `ClickGUI` в `modules.render`).

### Композиция дерева GUI

Иерархия отображения строится сверху вниз:

```
ClickGUI (Screen)
└── renderCategories = EnumSet.of(Combat, Move, Render, Player, Misc)   // 5 колонок-панелей
    └── для каждой Type → renderPanel()
        └── Manager.FUNCTION_MANAGER.getFunctions(category)              // список модулей категории
            └── фильтр isFunctionVisible() (поиск по name / keywords)
                └── анимированный заголовок функции (градиент темы)
                    └── при раскрытии (expand) — список Setting'ов функции
                        └── instanceof-диспетч на нужный *SettingRenderer
```

Ключевой момент: **дерево не хранится** как объекты-виджеты. Каждый кадр `render()` заново итерирует `Manager.FUNCTION_MANAGER.getFunctions(category)`, фильтрует через `isFunctionVisible()` (сравнение текста поиска с `function.name` и `function.keywords`, регистронезависимо), анимирует прогресс раскрытия и для каждой видимой настройки выполняет цепочку `instanceof`-проверок, выбирая один из семи экземпляров рендереров.

Набор категорий жёстко задан в `renderCategories` (`EnumSet.of(Combat, Move, Render, Player, Misc)`) — новая категория `Type` в меню **не появится** без правки этого поля.

### Раскладка и анимации

Константы раскладки (поля `ClickGUI`):

| Константа | Значение | Назначение |
|-----------|----------|-----------|
| `PANEL_WIDTH` | `125` | Ширина колонки-категории |
| `PANEL_HEIGHT` | `280` | Высота панели |
| `PANEL_MARGIN` | `8` | Отступ между панелями |
| `FUNCTION_HEIGHT` | `20` | Высота строки функции |
| `TITLE_HEIGHT` / `TITLE_MARGIN_TOP` | `20` / `5` | Заголовок панели |
| `SCROLL_AREA_HEIGHT` | `PANEL_HEIGHT - SCROLL_AREA_Y_OFFSET - 5` | Зона прокрутки внутри панели |

Панели центрируются как сетка: `totalWidth = categories * (PANEL_WIDTH + PANEL_MARGIN) - PANEL_MARGIN`, затем `startX = (width - totalWidth)/2`, `startY = (height - PANEL_HEIGHT)/2`.

Открытие/закрытие меню анимируется через `EaseInOutQuad(250, 1)` (поле `animationOpen`): при закрытии (`isClose`) рендер прекращается и вызывается `super.close()` только когда анимация в направлении `NEGATIVE` завершена. Масштабирование контента вокруг центра экрана делает `RenderAddon.sizeAnimation(matrices, width/2, height/2, animation)`.

Прокрутка категорий — **двойной lerp**: целевое смещение `scrollTargets` лерпуется в `scrollOffsets` с фактором `SCROLL_LERP_FACTOR = 20`, плюс дополнительное сглаживание `SCROLL_SMOOTH_FACTOR = 12` и скорость `SCROLL_SPEED = 12`. Прогресс раскрытия функций и анимация поворота стрелки-индикатора хранятся в `HashMap`'ах, ключом по `Function` (`expandProgress`, `arrowRotationProgress`).

> Важно: статические поля состояния (`scrollOffsets`, `scrollTargets`, `themeScrollOffset`, `colorPickerOpen`, `selectedColor1/2`, `themeMenu`) сохраняются между повторными открытиями меню, так как объявлены `static`.

### Поиск, тема, color picker

- **Поиск** — `SearchState` (изменяемая структура: `text`, `focused`, `cursorPosition`, тайминги мигания курсора). Ограничение 30 символов в `charTyped`. Любой ввод сбрасывает прокрутку всех категорий в 0. Совпадение идёт по `function.name` ИЛИ `function.keywords`. Константы: `SEARCH_HEIGHT = 20`, `SEARCH_MAX_WIDTH = 180`.
- **Панель тем** (`renderTheme`) — горизонтально прокручиваемый ряд градиентных «пипсов» (`VISIBLE_THEMES = 11`, `THEME_MAX_WIDTH = 180`). Первый пип открывает 2-осевой color picker; правый клик по пользовательскому пипу удаляет тему. Текстуры берутся из `images/gui/pips.png`, `images/gui/pick.png`, `images/gui/colors2.png`; пиксели сэмплятся из `ResourceProvider.color_image`.
- **Color picker** — две площадки `pick.png`, сэмплящие цвет из изображения; кнопка «Add theme» автоматически именует темы `Custom`, `Custom-2`, `Custom-3`… избегая коллизий, и вызывает `setTheme()`.

Текст окончания подписки рисует `renderExpiryText` («Окончание - …»), читая `Manager.USER_PROFILE`.

### Взаимодействие с функциями

Модель кликов по строке функции (в `ClickGUI.mouseClicked`):

| Кнопка мыши | Действие |
|-------------|----------|
| Левый клик | toggle (вкл/выкл функции) |
| Правый клик | раскрыть/свернуть список настроек |
| Средний клик | начать привязку бинда к функции |

Биндкоды функций: для клавиатуры — `keyCode`; для мыши — кодируется как `-(button+2)`. Сентинелы «не привязано»: `Function` использует `0`, `BindSetting` — `-1`.

Ввод (`mouseClicked`/`keyPressed`/`charTyped`/`mouseScrolled`/`mouseDragged`) проходит ту же итерацию `instanceof`-диспетча, заново выводя прямоугольник каждой настройки и пробрасывая событие в `renderer.mouseClicked(...)`. Значения настроек читаются/пишутся напрямую через объекты настроек (`setting.set/get`).

---

## Система рендереров настроек (`SettingRenderer`)

### Контракт интерфейса

```java
public interface SettingRenderer<T extends Setting> {
    void render(DrawContext ctx, T setting, int x, int y, int width, int height);
    boolean mouseClicked(T setting, double mouseX, double mouseY, int button,
                         int x, int y, int width, int height);

    default boolean mouseReleased(...) { return false; }
    default boolean mouseScrolled(...) { return false; }
    default boolean keyPressed(T setting, int keyCode, int scanCode, int modifiers) { return false; }
    default boolean charTyped(T setting, char c, int modifiers) { return false; }
    default boolean keyReleased(...) { return false; }
    default void tick(T setting, float delta) {}

    int getHeight();
}
```

Только `render`, `mouseClicked` и `getHeight` обязательны; остальные хуки имеют пустые/`false` реализации по умолчанию. `getHeight()` обеспечивает синхронность раскладки, hit-testing'а и рендеринга — `ClickGUI` использует его (наряду с `computeSettingsHeight()` / `getSettingRendererHeight()`) чтобы все три прохода (расчёт высоты, рисование, обработка кликов) давали одинаковую геометрию.

### Диспетчеризация по типу настройки

`ClickGUI` владеет **по одному экземпляру** каждого рендерера (паттерн Strategy + `instanceof`):

```java
private final BooleanSettingRenderer     booleanSettingRenderer     = new BooleanSettingRenderer();
private final BindBooleanSettingRenderer bindbooleanSettingRenderer = new BindBooleanSettingRenderer();
private final BindSettingRenderer        bindSettingRenderer        = new BindSettingRenderer();
private final ModeSettingRenderer        modeSettingRenderer        = new ModeSettingRenderer();
private final MultiSettingRenderer       multiSettingRenderer       = new MultiSettingRenderer();
private final SliderSettingRenderer      sliderSettingRenderer      = new SliderSettingRenderer();
private final TextSettingRenderer        textSettingRenderer        = new TextSettingRenderer();
```

Выбор рендерера происходит цепочкой `instanceof` в `render`, `computeSettingsHeight`, `getSettingRendererHeight` и `mouseClicked` — эти четыре места **должны оставаться идентичными** по порядку проверок.

### Каталог рендереров

| Рендерер | Тип настройки | Что рисует | Высота |
|----------|---------------|-----------|--------|
| `BooleanSettingRenderer` | `BooleanSetting` | Переключатель-тумблер (`WIDTH=22`, `SWITCH_HEIGHT=12`, `KNOB_RADIUS=8`); анимированный сдвиг кнопки (lerp 0.15), фон лерпует от серого `(50,50,50,200)` к цвету темы; авто-скролл имени при наведении | `16` |
| `BindBooleanSettingRenderer` | `BindBooleanSetting` | Тумблер + раскрывающаяся под-панель привязки бинда («Binding…», анимация точек каждые 400 мс); кнопка закрытия `images/gui/fl.png`, иконка-шестерёнка `iconsWex[24]` глиф `H` | `16` |
| `BindSettingRenderer` | `BindSetting` | Строка имени + кнопка с именем клавиши (`ClientManager.getKey`) или `NONE`/`Binding`+точки | `15` |
| `ModeSettingRenderer` | `ModeSetting` | Ряд «чипов» одиночного выбора с переносом по строкам; высота от переноса (`getHeight(setting,width)`); выбранный чип — цвет темы | `BOX_HEIGHT=12` |
| `MultiSettingRenderer` | `MultiSetting` | То же, но множественный выбор; счётчик «выбрано/всего»; `setting.toggle(mode)` на каждый чип | как Mode |
| `SliderSettingRenderer` | `SliderSetting` | Горизонтальный слайдер (трек + заполнение цветом темы); анимированный круг-ползунок (lerp 0.2, масштаб 1.2× при перетаскивании); значение округляется до `increment`, формат `%d`/`%.1f`/`%.2f` по величине | `20` (`BAR_HEIGHT=4`, `CIRCLE_RADIUS=6`) |
| `TextSettingRenderer` | `TextSetting` | Редактируемое поле: лейбл + скруглённое поле (ширина зажата `MIN=60..MAX=105`); мигающий курсор (500 мс), горизонтальный скролл за курсором, полное редактирование (`backspace/delete/arrows/enter/escape`) | — |

Состояние перетаскивания/анимации слайдера хранится **на самом объекте настройки** (`setting.circlePos`, `setting.circleScale`, `setting.dragging`), что согласовано с GUI-only полями в `SliderSetting`. Аналогично `BindBooleanSetting` хранит `dotState`/`lastDotUpdate`.

`DescriptionRenderQueue` — статическая отложенная очередь тултипов: во время рендера настройки вызывается `add(text, x, y)`, а `ClickGUI.render` один раз в конце кадра делает `renderAll()` (шрифт `durman[14]`, чёрный фон), чтобы тултипы рисовались поверх панелей. Очередь очищается в каждом `renderAll()`.

### Как рисуется одна настройка

Полный путь отрисовки одной настройки за кадр:

1. `ClickGUI.renderPanel` итерирует функции категории и при раскрытии — их `Setting`'и.
2. Для каждой настройки выполняется `instanceof`-диспетч → выбирается конкретный `*SettingRenderer`.
3. Высота вычисляется через `getSettingRendererHeight()` / `renderer.getHeight()` (для `Mode`/`Multi` — с учётом переноса по ширине), чтобы корректно сдвинуть `y` следующей настройки.
4. `renderer.render(ctx, setting, x, y, width, height)` рисует контрол: примитивы через `RenderUtil` (скруглённые прямоугольники, круги, blur), акцентный цвет берётся из `Manager.STYLE_MANAGER.getFirstColor()`.
5. Текст рисуется через `FontUtils.<семейство>[размер]` → `RenderFonts`, который лениво печёт страницы глифов в GPU-текстуры.
6. Если курсор над контролом — описание ставится в `DescriptionRenderQueue` и выводится в конце кадра.
7. Ввод (`mouseClicked` и т.д.) повторно выводит прямоугольник настройки той же логикой и вызывает соответствующий хук рендерера, который меняет значение через `setting.set(...)`.

---

## Отдельные экраны

### AltManager

`screens/altmanager/AltManager.java` — экран управления альт-аккаунтами поверх `Manager.ACCOUNT_MANAGER`.

- Анимированный градиентный заголовок (кликабельный — эффект «тряски»: `shakeTime=20`, смещение Y по `sin(shakeTime*0.5)*3`).
- Поле ввода имени (16 символов; вставка `Ctrl+V` санируется до `\w`).
- Прокручиваемый список аккаунтов с кнопками `Select`/`Delete` на каждый, плюс `Create` / `Clear-all` (с диалогом подтверждения) / `Random`.
- `Random` генерирует имя `exosware_` + случайные цифры, обрезает до 16 символов и вызывает `ClientManager.loginAccount()` (офлайн/cracked-сессия).
- Все константы раскладки умножаются на `SCALE = 1.5`.
- Заголовки/брендинг (`ExosWare 1.21.4`) захардкожены; заголовок рисуется `sf_bold[48]` с анимированным градиентом `ColorUtil.getColorStyle`.

### MainMenu

`screens/mainmenu/MainMenu.java` — кастомный титульный экран, заменяющий ванильный (подставляется из `client.MixinTitleScreen` при `!legitMode`).

- Заголовок `ExosWare 1.21.4`, анимированный градиент + тряска по клику. Рендер заголовка — `sf_bold[54]`, градиент `ColorUtil.getColorStyle(30)`→`(260)`, время `(millis%4000)/1500`.
- Внутренние классы-виджеты `Button` и `CombinedButton` (lerp наведения + масштаб до 1.02). `CombinedButton` делит одну 200px-кнопку на половины `Options | Quit` с «мёртвым» 2px-зазором посередине для hit-testing'а.
- Навигация: `SelectWorldScreen`, `MultiplayerScreen`, `AltManager`, `OptionsScreen`, `scheduleStop()`.
- `shouldCloseOnEsc() = false`.

### UnHookScreen

`screens/unhook/UnHookScreen.java` — экран «паники» / анти-скриншер. **Ничего не рисует.** В первом же кадре `render()`:

1. Устанавливает `ClientManager.legitMode = true` (глобальный kill-switch — отключает всю шину событий и биндов).
2. Вызывает `UnHook.onUnhook()` (модуль `modules.misc.UnHook`) — отключает все активные функции, сохраняя их в `functionsToBack`, и прячет папку `C:\ExosWare`.
3. Делает `mc.setScreen(null)` (отсоединяет экран).

Это не полноценный экран, а «кнопка паники»: связан с `Manager.FUNCTION_MANAGER.unHook`.

---

## Система тем (`StyleManager` / `Style`)

`Style` — неизменяемый объект темы:

```java
public class Style {
    public final String name;
    public final int[] colors;   // пара цветов градиента
}
```

`StyleManager implements IMinecraft` хранит `List<Style>` (`CopyOnWriteArrayList`) и текущую тему `currentStyle`. На `init()` засеваются **5 встроенных тем**:

| Имя | Цвет 1 | Цвет 2 |
|-----|--------|--------|
| `Клиентский` | `#5433FF` | `#00FFFF` |
| `Осень` | `#FF7D00` | `#FFD700` |
| `Кислотный` | `#CCFF00` | `#00FF00` |
| `Океан` | `#0077BE` | `#00B4D8` |
| `Вишневый` | `#8B0000` | `#FF1493` |

После встроенных загружаются пользовательские темы (`loadCustomThemes`), затем `currentStyle = styles.get(0)`.

**Персист** — файл `<runDirectory>/files/themes.ew` (UTF-8). Сохраняются **только** темы, чьё имя начинается с `custom` (регистронезависимо: `style.name.toLowerCase().startsWith("custom")`), в формате одной строки на тему:

```
Имя:#HEX1:#HEX2
```

Парсинг (`loadCustomThemes`) разбивает строку по `:` и требует минимум 3 части. Только `Custom`-темы можно удалить (`removeStyle` проверяет тот же префикс).

**API цвета**, питающий весь клиент:

- `getFirstColor()` — `currentStyle.colors[0]` или `-1`, если темы нет.
- `getSecondColor()` — `currentStyle.colors[1]`, иначе fallback на `getFirstColor()`.

Эти методы через `ColorUtil.getColorStyle` / `getColorHud` определяют акцентный градиент по всему клиенту: HUD, ESP, рендереры настроек, fog модуля `World` и т.д. Смена активной `Style` перекрашивает весь интерфейс.

Вспомогательный `HexColor.toColor(hex)` парсит `#RRGGBB` и ставит alpha `255`; `colorToHex(color)` сериализует обратно (`& 0xFFFFFF`, формат `%06X`).

---

## Система шрифтов (`FontUtils` / `RenderFonts`)

`FontUtils` — статический реестр семейств шрифтов. Каждое семейство — массив `RenderFonts[256]`, **индексируемый размером в пикселях** (паттерн flyweight по размеру):

```java
public static volatile RenderFonts[] durman   = new RenderFonts[256];
public static volatile RenderFonts[] sf_bold  = new RenderFonts[256];
public static volatile RenderFonts[] icomoon  = new RenderFonts[256];
// ...
```

Доступ: `FontUtils.durman[13]`, `FontUtils.sf_bold[54]` и т.п.

`init()` (синхронизированный, идемпотентный через `initialized`) загружает 13 TTF из `/assets/exosware/font/`:

| Семейство | Файл | Назначение |
|-----------|------|-----------|
| `comfortaa` | `comfortaa.ttf` | (нестатическое поле, экземплярное) |
| `durman` | `durman.ttf` | Рабочий шрифт настроек/тултипов |
| `glitched` | `glitched.ttf` | Эффектный |
| `icons` | `icons.ttf` | Глиф-иконки |
| `monsterrat` | `monsterrat.ttf` | (sic — опечатка от Montserrat) |
| `profont` | `profont.ttf` | Моноширинный |
| `sf_bold` | `sf_bold.ttf` | Заголовки |
| `sf_medium` | `sf_medium.ttf` | Подзаголовки/кнопки |
| `iconsWex` | `iconsWex.ttf` | Глиф-иконки (шестерёнка и пр.) |
| `gilroy` | `gilroy.ttf` | — |
| `gilroy_bold` | `gilroy-bold.ttf` | — |
| `hud` | `hud.ttf` | HUD |
| `icomoon` | `icomoon.ttf` | Глиф-иконки |

`initializationFont(array, name)` создаёт `Font.createFont(TRUETYPE_FONT, …)` и заполняет `array[i] = new RenderFonts(font, i)` для `i` от 1 до 255 (индекс 0 не используется).

`RenderFonts` (`manager/fontManager/RenderFonts.java`) — самодостаточный растеризатор и рендерер:

- Лениво печёт страницы глифов (`charsPerPage = 256`, `padding = 5`) из AWT-`Font` в `BufferedImage` → `NativeImageBackedTexture`, кэширует глифы и их ширины.
- Рисует quad'ы через `Tessellator` (`POSITION_TEXTURE_COLOR`). Поддержка выравнивания left/right/centered, обрезанного (clipped) текста, переноса по словам.
- Поддержка кодов цвета Minecraft `§`/`&` (таблица `COLOR_CODES_FLOAT` из 16 цветов).
- Per-character линейный градиент и анимированный (ping-pong) градиент текста (`renderAnimatedGradientText`) — используется для заголовков `MainMenu`/`AltManager`.
- Рендер в масштабе `SCALE_FACTOR = 0.5` для чёткости: `getWidth()`/`getHeight()` уже применяют 0.5, поэтому, например, `durman[13]` высотой ~6.5px.
- Текстуры страниц глифов регистрируются под `Identifier.of("font", "temp/" + случайные16символов)`; асинхронный предпрогрев — на однопоточном executor'е.

---

## Замечания и подводные камни

- **Жёстко заданный набор категорий.** `renderCategories = EnumSet.of(Combat, Move, Render, Player, Misc)` — любая новая `Type` не отобразится в `ClickGUI` без правки.
- **Дублирование layout-математики.** Hit-testing в обработчиках ввода заново выводит прямоугольники настроек независимо от `render`, с небольшими ad-hoc сдвигами по Y (напр. `BindSetting`/`Slider` рисуются на `settingY-2`; у `BindBoolean` разные `bindBoxY` в `render` и `mouseClicked`) — зоны клика могут быть смещены на пару пикселей.
- **Статическое состояние меню** (`scrollOffsets`, `themeMenu`, `colorPickerOpen`, `selectedColor1/2` и др.) переживает повторное открытие меню.
- **Захардкоженные пути текстур:** `images/gui/colors2.png`, `images/gui/pips.png`, `images/gui/pick.png`, `images/gui/fl.png`; пиксели цвета — из `ResourceProvider.color_image`.
- **Персист тем** только для имён, начинающихся с `Custom` (регистронезависимо), в `files/themes.ew`.
- **Масштаб шрифта 0.5.** Размеры вроде `durman[13]` — это ~6.5px реальной высоты; учитывайте при расчёте раскладки.
- **`ModeSettingRenderer`/`MultiSettingRenderer`** содержат ошибку приоритета операторов в lerp наведения: `(hovered?1f:0f - hoverProg)*0.08f` парсится как `hovered ? 1f : (0f - hoverProg)`, поэтому ненаведённые чипы анимируются к `-hoverProg`, а не к 0. Также `ColorUtil.interpolateColor(base, base, hoverProg)` — no-op (оба аргумента одинаковы), фон ненаведённого чипа не меняется.
- **`UnHookScreen`** ничего не рисует и саморазрушается в первом кадре (`legitMode=true` + `onUnhook` + `setScreen(null)`) — это «кнопка паники», а не настоящий экран.
- **`monsterrat`** — опечатка в имени поля/файла (от Montserrat); сохранена как есть в коде.
- **Бинды:** клавиатурные — `keyCode`; мышь — `-(button+2)`; сентинелы «не привязано» — `0` (для `Function`) и `-1` (для `BindSetting`). `clearAllBindings` в командах явно пропускает `clickGUI`, чтобы GUI нельзя было заблокировать.
