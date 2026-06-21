package ru.levin.mixin.player;

import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.events.Event;
import ru.levin.events.impl.input.EventKeyBoard;

// 1.21.1: Input uses plain boolean/float fields (no PlayerInput record — that's 1.21.2+).
// KeyboardInput.tick(boolean slowDown, float slowFactor) sets up/down/left/right/forwardImpulse/leftImpulse/jumping/shiftKeyDown.
@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput extends Input {
    @Shadow
    @Final
    private Options options;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(boolean slowDown, float slowFactor, CallbackInfo ci) {
        boolean forwardKey = this.options.keyUp.isDown();
        boolean backwardKey = this.options.keyDown.isDown();
        boolean leftKey = this.options.keyLeft.isDown();
        boolean rightKey = this.options.keyRight.isDown();
        boolean jumpKey = this.options.keyJump.isDown();
        boolean sneakKey = this.options.keyShift.isDown();
        boolean sprintKey = this.options.keySprint.isDown();

        float movementForward = calculateMovement(forwardKey, backwardKey);
        float movementSideways = calculateMovement(leftKey, rightKey);
        EventKeyBoard event = new EventKeyBoard(movementForward, movementSideways, jumpKey, sneakKey, sprintKey);
        Event.call(event);

        this.forwardImpulse = event.getMovementForward();
        this.leftImpulse = event.getMovementStrafe();
        this.up = event.getMovementForward() > 0;
        this.down = event.getMovementForward() < 0;
        this.left = event.getMovementStrafe() > 0;
        this.right = event.getMovementStrafe() < 0;
        this.jumping = event.isJump();
        this.shiftKeyDown = event.isSneak();

        if (slowDown) {
            this.leftImpulse *= slowFactor;
            this.forwardImpulse *= slowFactor;
        }

        ci.cancel();
    }

    private float calculateMovement(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }
}
