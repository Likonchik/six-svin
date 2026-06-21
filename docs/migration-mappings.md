# Справочник маппингов: Yarn 1.21.4 → Mojmap 1.21.1

> Приложение к [migration-neoforge-1.21.1.md](migration-neoforge-1.21.1.md). Рабочие таблицы для скриптовых
> и ручных проходов. NeoForge работает на **официальных маппингах Mojang (Mojmap)** в рантайме — миксины
> таргетят Mojmap-имена напрямую. Флаг **⚠VERIFY** = точную сигнатуру/имя надо сверить с реальными исходниками 1.21.1.

## 1. Типы (импорты/имена)

Многие типы при переходе меняют **только пакет** (короткое имя то же) — их нельзя ремапить по короткому имени.

| Yarn | Mojmap | Заметка |
| --- | --- | --- |
| `client.MinecraftClient` | `client.Minecraft` | `getInstance()`; поля `.player/.level`; `runDirectory→gameDirectory` |
| `client.network.ClientPlayerEntity` | `client.player.LocalPlayer` | смена пакета |
| `client.network.AbstractClientPlayerEntity` | `client.player.AbstractClientPlayer` | |
| `entity.player.PlayerEntity` | `world.entity.player.Player` | |
| `entity.LivingEntity` | `world.entity.LivingEntity` | имя то же, пакет `entity→world.entity` |
| `entity.Entity` | `world.entity.Entity` | имя то же |
| `client.world.ClientWorld` | `client.multiplayer.ClientLevel` | |
| `world.World` | `world.level.Level` | ⚠ коллизия с `ru.levin…render.World` |
| `client.network.ClientPlayNetworkHandler` | `client.multiplayer.ClientPacketListener` | поле `networkHandler→connection` |
| `client.network.ClientPlayerInteractionManager` | `client.multiplayer.MultiPlayerGameMode` | поле `interactionManager→gameMode` |
| `util.math.Box` | `world.phys.AABB` | методы `expand/contract/getCenter` ⚠VERIFY |
| `util.math.Vec3d` | `world.phys.Vec3` | самый плотный тип (34) |
| `util.math.Vec2f` | `world.phys.Vec2` | |
| `util.math.BlockPos` | `core.BlockPos` | имя то же |
| `block.BlockState` | `world.level.block.state.BlockState` | имя то же |
| `util.Identifier` | `resources.ResourceLocation` | `Identifier.of(ns,path)→fromNamespaceAndPath`; `Identifier.of("a:b")→parse` ⚠VERIFY по числу аргументов |
| `item.ItemStack` | `world.item.ItemStack` | имя то же |
| `item.Item` / `item.Items` | `world.item.Item` / `world.item.Items` | имя то же |
| `text.Text` | `network.chat.Component` | `literal/translatable/empty` те же; `Text.of→nullToEmpty/literal` ⚠VERIFY |
| `text.MutableText` | `network.chat.MutableComponent` | |
| `util.Formatting` | `ChatFormatting` | константы те же |
| `util.Hand` | `world.InteractionHand` | константы те же |
| `sound.SoundEvent` | `sounds.SoundEvent` | имя то же |
| `client.gui.DrawContext` | `client.gui.GuiGraphics` | `drawTexture→blit`, `drawText→drawString`, `getMatrices()→pose()` |
| `client.util.math.MatrixStack` | `com.mojang.blaze3d.vertex.PoseStack` | `push/pop→pushPose/popPose`, `peek().getPositionMatrix()→last().pose()` |
| `client.render.RenderTickCounter` | `client.DeltaTracker` | `getTickDelta(boolean)` → ⚠VERIFY (`getGameTimeDeltaPartialTick`?) |
| `client.render.VertexConsumerProvider` | `client.renderer.MultiBufferSource` | |
| `client.render.Camera` | `client.Camera` | имя то же |
| `util.hit.HitResult` | `world.phys.HitResult` | `BlockHitResult/EntityHitResult` те же |
| `client.gui.screen.Screen` | `client.gui.screens.Screen` | пакет `screen→screens` (мн.ч.) |
| `client.option.KeyBinding` | `client.KeyMapping` | |
| `util.math.MathHelper` | `util.Mth` | `clamp/sin/lerp/sqrt/floor/cos` — имена те же |
| `util.math.Direction` | `core.Direction` | имя то же |
| `util.math.RotationAxis` | `com.mojang.math.Axis` | `POSITIVE_X→XP`, `POSITIVE_Y→YP`, `POSITIVE_Z→ZP`, `NEGATIVE_Y→YN` |
| `screen.slot.SlotActionType` | `world.inventory.ClickType` | `SWAP/QUICK_MOVE/PICKUP` ⚠VERIFY |
| `screen.ScreenHandler` | `world.inventory.AbstractContainerMenu` | `syncId→containerId`, `currentScreenHandler→containerMenu` |
| `command.CommandSource` | `commands.SharedSuggestionProvider` | ⚠VERIFY |
| `registry.Registries` | `core.registries.BuiltInRegistries` | `containsId→containsKey`, `getId→getKey` |
| `component.DataComponentTypes` | `core.component.DataComponents` | `CONTAINER`, `POTION_CONTENTS` |
| `component.type.ContainerComponent` | `world.item.component.ItemContainerContents` | |
| `component.type.PotionContentsComponent` | `world.item.component.PotionContents` | `getEffects→getAllEffects` |
| `entity.attribute.EntityAttributes` | `world.entity.ai.attributes.Attributes` | ⚠VERIFY префикс `GENERIC_` (см. §4) |

## 2. Члены (поля/методы) — отдельный проход ПОСЛЕ типов

| Yarn | Mojmap | Кол-во |
| --- | --- | --- |
| `mc.world` / `entity.getWorld()` | `mc.level` / `entity.level()` | 145 |
| `mc.networkHandler` / `getNetworkHandler()` | `mc.getConnection()` | 107 |
| `handler.sendPacket(p)` | `connection.send(p)` | 52 |
| `mc.interactionManager` | `mc.gameMode` | 41 |
| `getYaw()/setYaw()` | `getYRot()/setYRot()`; `prevYaw→yRotO` | 65 |
| `getPitch()/setPitch()` | `getXRot()/setXRot()`; `prevPitch→xRotO` | 56 |
| `entity.getPos()` | `entity.position()` | 69 |
| `getInventory().selectedSlot` | `getInventory().selected` | 29 |
| `getEyePos()` | `getEyePosition()` | 13 ⚠VERIFY |
| `getMainHandStack()/getOffHandStack()` | `getMainHandItem()/getOffhandItem()` | 20 ⚠VERIFY |
| `getStackInHand(hand)` | `getItemInHand(hand)` | ⚠VERIFY |
| `getEquippedStack(slot)` | `getItemBySlot(slot)` | 3 |
| `getAttackCooldownProgress(f)` | `getAttackStrengthScale(f)` | 3 ⚠VERIFY |

## 3. Пакеты (C2S/S2C)

Пакеты переезжают: `network.packet.c2s/s2c.play → network.protocol.game`; `…common → network.protocol.common`.
Имена: `XxxC2SPacket → ServerboundXxxPacket`, `XxxS2CPacket → ClientboundXxxPacket`.

| Yarn | Mojmap | Заметка |
| --- | --- | --- |
| `PlayerMoveC2SPacket` | `ServerboundMovePlayerPacket` | вложенные: `.LookAndOnGround→.Rot`, `.PositionAndOnGround→.Pos`, `.Full→.PosRot` ⚠VERIFY |
| `PlayerInteractItemC2SPacket(hand,seq,yaw,pitch)` | `ServerboundUseItemPacket` | yaw/pitch есть в обеих версиях |
| `PlayerInteractEntityC2SPacket.attack/.interact` | `ServerboundInteractPacket.createAttackPacket/createInteractionPacket` | ⚠VERIFY имена фабрик |
| `UpdateSelectedSlotC2SPacket` | `ServerboundSetCarriedItemPacket` | |
| `ClientCommandC2SPacket(player, Mode.X)` | `ServerboundPlayerCommandPacket(player, Action.X)` | `Mode→Action`; `START_FALL_FLYING/START_SPRINTING` |
| `PlayerActionC2SPacket` | `ServerboundPlayerActionPacket` | `RELEASE_USE_ITEM` |
| `ClickSlotC2SPacket` | `ServerboundContainerClickPacket` | |
| `CloseHandledScreenC2SPacket` | `ServerboundContainerClosePacket` | |
| `SlotChangedStateC2SPacket` | `ServerboundContainerSlotStateChangedPacket` | |
| `KeepAliveC2SPacket` | `ServerboundKeepAlivePacket` | пакет `protocol.common` |
| `ResourcePackStatusC2SPacket(id, Status.X)` | `ServerboundResourcePackPacket(id, Action.X)` | `Status→Action` ⚠VERIFY |
| `ResourcePackSendS2CPacket` | `ClientboundResourcePackPushPacket` | `.id()` |
| `WorldTimeUpdateS2CPacket` | `ClientboundSetTimePacket` | TPS-расчёт |
| `GameMessageS2CPacket` | `ClientboundSystemChatPacket` | `.content()` |
| `EntityVelocityUpdateS2CPacket` | `ClientboundSetEntityMotionPacket` | `getEntityId()→getId()` |
| `InventoryS2CPacket` | `ClientboundContainerSetContentPacket` | `getContents()→getItems()` |
| `DisconnectS2CPacket` | `ClientboundDisconnectPacket` | |
| `PlayerPositionLookS2CPacket` | `ClientboundPlayerPositionPacket` | ⚠VERIFY (реворк в 1.21.2) |

## 4. Реестр version-deltas (откат 1.21.2+ → 1.21.1)

Это НЕ простой rename — настоящие отличия API/поведения. Все помечены к проверке против исходников 1.21.1.

| # | API / узел | Что в 1.21.1 | Риск | Файлы |
| --- | --- | --- | --- | --- |
| D1 | **EntityRenderState / LivingEntityRenderState / ItemEntityRenderState** (1.21.2) | нет render-state; `LivingEntityRenderer.render(entity, yaw, partialTick, PoseStack, MultiBufferSource, light)`; `renderNameTag(Entity, Component, …)` | high | `RenderAddon`, `MixinLivingEntityRenderer`, `MixinPlayerEntityRenderer`, `MixinItemEntityRenderer` |
| D2 | **ShaderProgramKey / Defines / ShaderProgramKeys** (1.21.2) | нет; `RenderSystem.setShader(Supplier<ShaderInstance>)` (void), `GameRenderer::get*Shader`, кастомный реестр через `RegisterShadersEvent`, `getUniform→Uniform` | high | `RenderUtil`, `RenderAddon`, `ResourceProvider`, `Render3DUtil`, `RenderFonts`, `ESP`, `TargetESP`, `Trails`, `Breadcrumbs`, `JumpCircles`, `Particles`, `Prediction` |
| D3 | **`LevelRenderer.renderMain(FrameGraphBuilder,…)`** (1.21.2) | нет FrameGraph; хук блок-аутлайна в `renderLevel` | high | `MixinWorldRenderer` |
| D4 | Рекорд **`Fog`** / `FogRenderer` реворк (1.21.2) | `FogRenderer.setupFog` возвращает void, мутирует стейт; нет рекорда `Fog` | high | `MixinBackgroundRenderer`, `MixinGameRenderer` |
| D5 | **`ItemCooldownManager` группы** по `Identifier` (1.21.2) | по `Item`: поле `cooldowns` `Map<Item,Entry>`; `Entry(startTime,endTime)`; тик-поле `tickCount`; `set(Item,int)`, `isOnCooldown(Item)`; нет `getGroup` | high | `ItemCooldownManagerAccessor`, `ItemCooldownEntryAccessor`, `HUD`, `MixinItem`, `MiddleClickPearl` |
| D6 | `Item.use→InteractionResultHolder<ItemStack>`; Mojmap-переименования методов: `finishUsing→finishUsingItem`, `getMaxUseTime→getUseDuration`, `isUsedOnRelease→useOnRelease` | ✅ ПРОВЕРЕНО по эталону: `getUseDuration(ItemStack, LivingEntity)` в 1.21.1 **сохраняет 2 аргумента** (param НЕ убирать!). `use(Level,Player,InteractionHand)→InteractionResultHolder<ItemStack>` (fail → `InteractionResultHolder.fail(stack)`). MixinItem ✅ сделан. | high | `MixinItem`✅, `SwingAnimations`, `PerfectTime` |
| D7 | Рекорд **`PlayerInput`** / `Input.playerInput` (1.21.2) | прямые поля `Input` (`forwardImpulse/leftImpulse/jumping/shiftKeyDown`) | high | `MixinKeyboardInput`, `MixinClientPlayerEntity` |
| D8 | **`EntityAttributes` без префиксов** (1.21.2 снял `GENERIC_`) | с префиксом (`…MOVEMENT_SPEED`); `getAttributeInstance` — сверить тип параметра | med | `AuraUtil` |
| D9 | **`VehicleMoveC2SPacket.fromVehicle`** + рекорд `PlayerInputC2SPacket` (1.21.2) | конструктор `ServerboundMoveVehiclePacket(entity)`; input-пакет — старая форма | med | `KTLeave` |
| D10 | **`Gui.render`/`renderCrosshair`/`renderEffects`** сигнатуры + `CROSSHAIR_TEXTURE` | сверить сигнатуры и `@At FIELD` цель | high | `MixinInGameHud` |
| D11 | **`RenderTickCounter.Dynamic`** поля + `beginRenderTick(J)I` | `DeltaTracker.Timer`; имена полей/метода ⚠VERIFY | med | `RenderTickCounterDynamicAccessor`, `IMinecraft` |
| D12 | **`@Local(ordinal=2) Matrix4f`** / хрупкие `@At` ординалы | пересверить по байткоду 1.21.1 | med | `MixinGameRenderer`, `MixinServerList`, `MixinMouse` |
| D13 | **`SimpleFramebuffer`/`Framebuffer`/`NativeImageBackedTexture`** | `TextureTarget`/`RenderTarget`/`DynamicTexture` (`bindWrite`, `getColorTextureId`, `setPixelRGBA`) ⚠VERIFY | med | `RenderUtil.drawBlur`, `RenderFonts` |
| D14 | **`AutoTool` Holder<Enchantment>** цепочка (`getRegistryRef/getValue/getEntry`) | `registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY)` ⚠VERIFY | med | `AutoTool` |
| D15 | Аксессоры-имена полей (Mojmap ≠ Yarn) | `jumpingCooldown`, `lastSprinting`, `pendingUpdateManager→BlockStatePredictionHandler`, `bossBars→events`, Screen `renderables/children/narratables` — все ⚠VERIFY | med | `iface/*`, `MixinClientPlayerEntity`, `ClientWorldAccessor`, `BossBarHudAccessor`, `ScreenAccessor` |
| D16 | Sequenced-interaction `PendingUpdateManager.incrementSequence` | существует в обеих, но Mojmap-имена ⚠VERIFY | med | `AutoPotion`, `ClientWorldAccessor` |

> **NB:** многие вещи, которые «кажутся» 1.21.4-специфичными, на деле есть и в 1.21.1 — это чистый rename, НЕ delta:
> компоненты предметов (`DataComponents`), реестры (`BuiltInRegistries`), data-driven энчанты (Holder API, с 1.20.5),
> неизменяемый `BufferBuilder` (с 1.21), `RenderTickCounter`/`DeltaTracker` (с 1.21), скоборд/боссбар-парсинг PvP.

## 5. Карантин: файлы только для ручной правки (исключить из скриптового ремапа)

Эти файлы бьются о настоящие version-deltas — НЕ прогонять автозаменой:

```
util/render/RenderUtil.java          util/render/RenderAddon.java
util/render/Render3DUtil.java        util/render/providers/ResourceProvider.java
util/shader/ShaderManager.java       manager/fontManager/RenderFonts.java
mixin/display/MixinWorldRenderer.java        mixin/display/MixinBackgroundRenderer.java
mixin/display/MixinInGameHud.java            mixin/display/MixinGameRenderer.java
mixin/player/MixinLivingEntityRenderer.java  mixin/player/MixinPlayerEntityRenderer.java
mixin/world/MixinItemEntityRenderer.java     mixin/player/MixinKeyboardInput.java
mixin/player/MixinItem.java                  mixin/iface/ItemCooldownManagerAccessor.java
mixin/iface/ItemCooldownEntryAccessor.java   mixin/client/RenderTickCounterDynamicAccessor.java
modules/render/HUD.java                       modules/player/MiddleClickPearl.java
modules/render/SwingAnimations.java           modules/player/PerfectTime.java
modules/player/AutoTool.java                  util/player/AuraUtil.java
modules/misc/KTLeave.java                     modules/combat/AutoPotion.java
modules/render/{ESP,TargetESP,Trails,Breadcrumbs,JumpCircles,Particles,Prediction}.java  (ShaderProgramKeys)
```

Остальное (~100+ файлов: combat/movement/player/misc-логика, util.math/move/player, команды, менеджеры) — кандидаты
на скриптовый Yarn→Mojmap проход + пер-файловая проверка компиляции.
