# Система модулей (Function) и настроек

Этот документ описывает каркас модулей клиента ExosWare: базовый класс `Function`, аннотацию метаданных `@FunctionAnnotation`, реестр `FunctionManager`, перечисление категорий `Type`, а также все семь типов настроек (`Setting`) с их сериализацией. В конце приведён рабочий пример создания нового модуля.

Каждая «фича» клиента (чит или утилита) — это подкласс `Function`. Метаданные модуля объявляются декларативно через аннотацию, настройки регистрируются в конструкторе, а вся логика реакции на события сосредоточена в одном методе `onEvent(Event)`.

---

## Базовый класс `Function`

Файл: `src/main/java/ru/levin/modules/Function.java`

`Function` — это `abstract class`, реализующий `IMinecraft` (что даёт доступ к статическому полю `mc`). Класс помечен `@SuppressWarnings("All")`.

### Поля

| Поле | Тип | Назначение |
|------|-----|-----------|
| `initerFunctions` | `FunctionAnnotation` | Кэш аннотации текущего класса, прочитанной рефлексией в конструкторе. |
| `name` | `String` (public) | Отображаемое имя модуля (из `annotation.name()`). |
| `keywords` | `String` (public) | Поисковые ключевые слова. Хранятся как `Arrays.toString(annotation.keywords())`, то есть строка в скобках, а не список. |
| `category` | `Type` (private) | Категория модуля; доступна через `getCategory()`. |
| `bind` | `int` (public) | Код клавиши-бинда (из `annotation.key()`, по умолчанию `0`). |
| `desc` | `String` (public) | Описание (tooltip) из `annotation.desc()`. |
| `state` | `boolean` (public) | Включён ли модуль. |
| `expanded` | `boolean` (public) | Развёрнут ли модуль в ClickGUI (GUI-состояние). |
| `settings` | `ArrayList<Setting>` (private final) | Список настроек модуля; доступен через `getSettings()`. |

> Примечание: поля `name`, `keywords`, `bind`, `desc`, `state`, `expanded` — публичные изменяемые, без инкапсуляции.

### Конструкторы

Есть два конструктора (паттерн «два конструктора»):

```java
// Основной: всё подтягивается из аннотации через initializeProperties()
public Function() {
    initializeProperties();
}

// Запасной ручной: НЕ читает аннотацию и НЕ читает настройки
public Function(String name, Type category) {
    this.name = name;
    this.category = category;
    this.state = false;
    this.bind = 0;
}
```

Метод `initializeProperties()` заполняет поля из аннотации:

```java
private void initializeProperties() {
    name     = initerFunctions.name();
    desc     = initerFunctions.desc();
    category = initerFunctions.type();
    keywords = Arrays.toString(initerFunctions.keywords());
    state    = false;
    bind     = initerFunctions.key();
}
```

### Жизненный цикл и методы

| Метод | Сигнатура | Поведение |
|-------|-----------|-----------|
| `onEvent` | `abstract void onEvent(Event event)` | Единственный обязательный метод подкласса. Внутри обычно идёт диспетчеризация по `instanceof` на конкретный подтип события. |
| `onEnable` | `protected void onEnable() {}` | Хук включения. По умолчанию пустой, переопределяется по необходимости. |
| `onDisable` | `protected void onDisable() {}` | Хук выключения. По умолчанию пустой. |
| `setState` | `final void setState(boolean enabled)` | **Идемпотентный, «тихий».** Если `state == enabled` — сразу выходит. Иначе меняет `state` и вызывает `onEnable()`/`onDisable()` внутри `try/catch` (исключения только `printStackTrace`). Звук и уведомление НЕ играет. Используется при загрузке конфига. |
| `toggle` | `void toggle()` | **Пользовательский путь.** Безусловно инвертирует `state`, вызывает `onEnable()`/`onDisable()` (в `try/catch`), затем играет звук (`playSound`) и добавляет уведомление (`NotificationType.SUCCESS`/`REMOVED`, текст `«Модуль включен!»` / `«Модуль выключен!»`, длительность `2` секунды). |
| `addSettings` | `void addSettings(Setting... options)` | Добавляет настройки в список `settings` (обычно вызывается в конструкторе модуля). |
| `getCategory` | `Type getCategory()` | Возвращает категорию. |
| `isState` | `boolean isState()` | Возвращает текущее состояние. |
| `getSettings` | `List<Setting> getSettings()` | Возвращает список настроек. |
| `getBindCode` / `setBindCode` | `int` / `void(int)` | Чтение/запись кода бинда. |
| `save` | `JsonObject save()` | Сериализация модуля в JSON (см. ниже). |
| `load` | `void load(JsonObject)` | Десериализация модуля из JSON (см. ниже). |

### Звуки переключения

`toggle()` вызывает приватный `playSound(boolean enable)`, который читает состояние модуля `Manager.FUNCTION_MANAGER.clientSounds`. Если `clientSounds.state == false`, звук не играет. Иначе по режиму `clientSounds.mode` выбирается пара файлов:

| Режим | Включение | Выключение |
|-------|-----------|------------|
| `Type-1` | `nuron.wav` | `nuroff.wav` |
| `Type-2` | `akron.wav` | `akroff.wav` |
| `Type-3` | `celon.wav` | `celoff.wav` |
| `Type-4` | `enableold.wav` | `disableold.wav` |
| прочее | — (тихо `return`) | — |

Файл проигрывается через `AudioUtil.playSound(...)`.

### Сериализация: `save()` / `load()`

`save()` возвращает `JsonObject` следующей формы:

```json
{
  "state": true,
  "keyIndex": 0,
  "Settings": {
    "ИмяНастройки1": <значение>,
    "ИмяНастройки2": <значение>
  }
}
```

- `state` — текущее состояние модуля;
- `keyIndex` — код бинда (`bind`);
- `Settings` — объект, где каждая настройка сериализуется по своему типу через цепочку `instanceof`.

`load(JsonObject)`:
- если `state` есть — восстанавливает через `setState(...)` (идемпотентно и тихо);
- если `keyIndex` есть — восстанавливает через `setBindCode(...)`;
- читает объект `Settings` (если он есть и является `JsonObject`), затем для каждой настройки модуля по имени (`set.getName()`) ищет соответствующее значение и применяет нужный сеттер.

> **Важно:** диспетчеризация настроек выполняется цепочкой `instanceof`-проверок только для семи известных типов. Если добавить новый тип `Setting`, он будет молча пропущен в `save()`/`load()` (нет ветки `else`/`default`).

---

## Аннотация `@FunctionAnnotation`

Файл: `src/main/java/ru/levin/modules/FunctionAnnotation.java`

Аннотация с `@Retention(RetentionPolicy.RUNTIME)` — она читается рефлексией во время выполнения.

| Элемент | Тип | По умолчанию | Назначение |
|---------|-----|--------------|-----------|
| `name()` | `String` | — (обязателен) | Имя модуля. |
| `desc()` | `String` | `""` | Описание/tooltip. |
| `key()` | `int` | `0` | Код клавиши-бинда по умолчанию. |
| `type()` | `Type` | — (обязателен) | Категория модуля. |
| `keywords()` | `String[]` | `{}` | Поисковые ключевые слова (используются в поиске ClickGUI вместе с `name`). |

Пример:

```java
@FunctionAnnotation(
    name = "AttackAura",
    desc = "Автоматически бьёт ближайшую цель",
    type = Type.Combat,
    keywords = {"KillAura", "Aura"}
)
public class AttackAura extends Function { ... }
```

---

## Перечисление `Type`

Файл: `src/main/java/ru/levin/modules/Type.java`

Пять категорий, каждая несёт одно-символьный глиф `icon` для кастомного шрифта иконок в GUI.

| Категория | `icon` | Назначение |
|-----------|--------|-----------|
| `Combat` | `"f"` | Боевые модули (аура, кристаллы, авто-тотем и т.д.). |
| `Move` | `"w"` | Перемещение (Speed, Flight, Blink и т.д.). |
| `Render` | `"E"` | Визуал/HUD (ESP, NameTags, HUD и т.д.). |
| `Player` | `"r"` | Игрок/инвентарь/эксплойты. |
| `Misc` | `"v"` | Прочее (UnHook, NameProtect, Xray, RPC и т.д.). |

Поле `icon` — `public final String`, задаётся в конструкторе перечисления.

---

## Реестр `FunctionManager`

Файл: `src/main/java/ru/levin/modules/FunctionManager.java`

`FunctionManager` — центральный реестр всех модулей («God-реестр»). Особенности:

- Статический список `public static final List<Function> functions = new CopyOnWriteArrayList<>();` — потокобезопасный.
- Около 90 публичных `final` полей-ссылок на конкретные модули (например, `attackAura`, `crystalAura`, `clickGUI`, `clientSounds`, `hud`, `blockESP`, `unHook` и т.д.), чтобы модули могли обращаться друг к другу напрямую через `Manager.FUNCTION_MANAGER.<модуль>`.
- В конструкторе все модули создаются и регистрируются разом: `functions.addAll(Arrays.asList(... new AttackAura(), new CrystalAura(), ... ))`. Регистрация сгруппирована комментариями по категориям (`//Combat`, `//Misc`, `//Movement`, `//Player`, `//Render`).
- В списке регистрации присутствуют закомментированные модули (например, `KTLeave`, `MaceExploit`, `GodMode`, `ShulkerPreview`) — отключённые, но не удалённые.

### Геттеры

| Метод | Возвращает | Поведение |
|-------|-----------|-----------|
| `getFunctions()` | `List<Function>` | Возвращает общий список `functions`. |
| `getFunctions(Type category)` | `List<Function>` | Линейный перебор по категории (`function.getCategory() == category`). |
| `static get(String name)` | `Function` (или `null`) | Регистронезависимый поиск по имени (`equalsIgnoreCase`), null-безопасный. |

---

## Типы настроек (`Setting`)

Файл базового класса: `src/main/java/ru/levin/modules/setting/Setting.java`

Базовый `Setting` хранит `name` и `Supplier<Boolean> visible` — предикат условной видимости (позволяет GUI скрывать настройку в зависимости от состояния другой настройки, например `() -> mode.is("Snap")`). Методы базового класса: `getName()`, `isVisible()`, `setVisible(Supplier<Boolean>)`.

> Многие подклассы переопределяют собственные поля `name`/`visible`, перекрывая поля базового класса; поэтому они также переопределяют `getName()`/`isVisible()`, чтобы избежать `null`.

### Сводная таблица всех семи типов

| Тип | Назначение | Ключевые методы | Как сериализуется |
|-----|-----------|-----------------|-------------------|
| `BooleanSetting` | Простой переключатель вкл/выкл. | `get()`, `set(boolean)`, `getDesc()` | Плоское JSON-свойство `boolean` под именем настройки. |
| `ModeSetting` | Одиночный выбор из списка (dropdown). | `get()`, `set(String)`, `is(String)`, `getIndex()`, `getIndex(String)`, `getModes()` | Плоское `String` — выбранное значение `selected`. |
| `MultiSetting` | Множественный выбор (галочки). | `toggle(String)`, `setSelected(Collection)`, `clearSelection()`, `get(String)`, `hasAnySelected()`, `getAvailableModes()`, `getSelectedModes()`, `getAllSelected()`, `getConfigValue()`, `setConfigValue(String)` | Плоское `String` — выбранные режимы через запятую (`getConfigValue()` = `String.join(",", selected)`). |
| `SliderSetting` | Числовой ползунок `[min..max]` с шагом `increment`. | `get()` (возвращает `Number`, clamp), `set(double)` (clamp), `getMin()`, `getMax()`, `getIncrement()` | Плоское `float` (через `getAsFloat`). Хранится как `double`, но при сохранении сужается до `float`. |
| `TextSetting` | Свободный ввод текста. | `getValue()`, `setValue(String)`, плюс GUI-состояние (`isFocused`, `cursorPosition`, `cursorVisible`, `hasText`) | Плоское `String` — значение `value`. |
| `BindSetting` | Захват одной клавиши (keybind-поле). | `getKey()`, `setKey(int)`, `isBinding()`, `setBinding(boolean)` | Плоское `int` — код клавиши `key`. |
| `BindBooleanSetting` | Гибрид: переключатель, который можно ещё и привязать к клавише для инвертирования. | `get()`, `set(boolean)`, `getBindKey()`, `setKey(int)`, `isListeningForBind()`, `setListeningForBind(boolean)`, `onKeyPress(int, boolean)` | **Вложенный объект** `{ "state": boolean, "bindKey": int }`. Единственный тип, сериализуемый как объект. При загрузке проверяется `isJsonObject()`. |

### Детали и нюансы

- **`BooleanSetting`** (`BooleanSetting.java`): хранит `status`. Четыре конструктора (с/без `desc`, с/без `Supplier<Boolean>` видимости).

- **`ModeSetting`** (`ModeSetting.java`): хранит `List<String> modes` и `String selected`. Метод `is(String)` использует `selected.contains(name)` — **подстрочное совпадение, а не равенство** (тонкий момент). Есть GUI-состояние анимации: `expanded`, `modeOffset`, `resetModeOffset()`, `incrementModeOffset(int)`, `getModeOffset()`.

- **`MultiSetting`** (`MultiSetting.java`): хранит `List<String> modes` и `LinkedHashSet<String> selected` (порядок сохраняется). Сериализация через запятую — **имена режимов не должны содержать запятую**.

- **`SliderSetting`** (`SliderSetting.java`): внутри `double`, но в `Function.save/load` сужается до `float` — возможна потеря точности. GUI-состояние перетаскивания: `dragging`, `circleScale = 1f`, `circlePos = -1`. `get()`/`set()` всегда делают `MathHelper.clamp`.

- **`TextSetting`** (`TextSetting.java`): `setValue` вызывается в конструкторе до `setVisible`.

- **`BindSetting`** (`BindSetting.java`): есть статический реестр `private static final List<BindSetting> allBindings`, в который **каждый экземпляр добавляет себя в конструкторе и никогда не удаляется** — фактически утечка/глобальный реестр на время жизни JVM. Если `name == null`, используется значение по умолчанию `«Кнопка»`. `isVisible()` null-безопасен.

- **`BindBooleanSetting`** (`BindBooleanSetting.java`): помимо `status` хранит `int bindKey` и флаг `listeningForBind`. Метод `onKeyPress(int keyCode, boolean isKeyDown)` инвертирует `status`, если нажатая клавиша совпадает с `bindKey`. Кодировка кнопок мыши: `processedKey = keyCode >= 0 ? keyCode : -(100 + keyCode + 2)` (та же магическая формула, что и в диспетчере биндов). GUI-состояние: `dotState`, `lastDotUpdate`, `expanded`.

---

## Рабочий пример: как написать новый модуль

Допустим, нужно добавить простой модуль категории `Move`, который при включении печатает в чат сообщение, реагирует на каждый игровой тик и имеет несколько настроек разных типов.

### Шаг 1. Создать класс с аннотацией

```java
package ru.levin.modules.movement;

import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.manager.ClientManager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.*;

@FunctionAnnotation(
        name = "ExampleMove",
        desc = "Демонстрационный модуль перемещения",
        type = Type.Move,
        keywords = {"Example", "Demo"}
)
public class ExampleMove extends Function {

    // 1) Объявляем настройки полями
    private final ModeSetting mode =
            new ModeSetting("Режим", "Плавный", "Плавный", "Резкий");

    private final SliderSetting power =
            new SliderSetting("Сила", 1.0, 0.1, 5.0, 0.1);

    private final BooleanSetting onlyGround =
            new BooleanSetting("Только на земле", true);

    // Условная видимость: показывать только в режиме "Резкий"
    private final SliderSetting sharpFactor =
            new SliderSetting("Резкость", 2.0, 1.0, 10.0, 0.5,
                    () -> mode.is("Резкий"));

    public ExampleMove() {
        // 2) Регистрируем настройки в конструкторе
        addSettings(mode, power, onlyGround, sharpFactor);
    }

    // 3) Хуки жизненного цикла (необязательно)
    @Override
    protected void onEnable() {
        ClientManager.message("ExampleMove включён");
    }

    @Override
    protected void onDisable() {
        ClientManager.message("ExampleMove выключен");
    }

    // 4) Основная логика — реакция на события
    @Override
    public void onEvent(Event event) {
        if (event instanceof EventUpdate) {
            if (onlyGround.get() && !mc.player.isOnGround()) return;

            double p = power.get().doubleValue();
            if (mode.is("Резкий")) {
                p *= sharpFactor.get().doubleValue();
            }
            // ... здесь применяем p к движению игрока ...
        }
    }
}
```

### Шаг 2. Зарегистрировать модуль в `FunctionManager`

Добавьте создание экземпляра в список `functions.addAll(Arrays.asList(...))` в конструкторе `FunctionManager` (в группу `//Movement`). Если на модуль нужно ссылаться из других модулей — заведите публичное `final` поле и присвойте его при регистрации:

```java
public final ExampleMove exampleMove;
// ...
functions.addAll(Arrays.asList(
        // ... другие модули ...
        exampleMove = new ExampleMove()
));
```

### Что вы получаете автоматически

- **Метаданные** (`name`, `desc`, `keywords`, категория, бинд) — из аннотации, без вызова `super(...)`.
- **Включение/выключение** — через `toggle()` (звук + уведомление) или `setState(boolean)` (тихо).
- **Сохранение/загрузка** — все четыре настройки автоматически попадут в `Settings` конфига (`ModeSetting` → строка, два `SliderSetting` → `float`, `BooleanSetting` → `boolean`), а `state` и бинд — в `state`/`keyIndex`.
- **Отрисовка в ClickGUI** — настройки нарисуются соответствующими рендерерами; `sharpFactor` будет скрыт, пока режим не `Резкий`.
- **Поиск** — модуль найдётся в ClickGUI по `name` и `keywords`.

> Помните о подводных камнях: `ModeSetting.is(...)` — это `contains` (подстрока), `SliderSetting` сериализуется как `float`, имена режимов `MultiSetting` не должны содержать запятых, а новые (нестандартные) типы `Setting` не сериализуются вовсе.
