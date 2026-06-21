package ru.levin.manager.commandManager.impl;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import ru.levin.manager.ClientManager;
import ru.levin.manager.commandManager.Command;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class VclipCommand extends Command {

    public VclipCommand() {
        super("vclip");
    }

    @Override
    public void execute(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("up").executes(context -> {
            double offset = findFreeSpace(true);
            teleport(offset);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("down").executes(context -> {
            double offset = findFreeSpace(false);
            teleport(offset);
            return SINGLE_SUCCESS;
        }));

        builder.then(arg("offset", DoubleArgumentType.doubleArg())
                .executes(context -> {
                    double offset = DoubleArgumentType.getDouble(context, "offset");
                    teleport(offset);
                    return SINGLE_SUCCESS;
                }));
    }

    private void teleport(double offset) {
        if (offset == 0) {
            ClientManager.message(ChatFormatting.RED + "Свободное место не найдено!");
            return;
        }

        double startY = mc.player.getY();
        double endY = startY + offset;

        for (int i = 0; i < 19; i++) {
            mc.player.connection.send(
                    new ServerboundMovePlayerPacket.Pos(
                            mc.player.getX(), startY, mc.player.getZ(),
                            true
                    )
            );
        }

        for (int i = 0; i < 19; i++) {
            mc.player.connection.send(
                    new ServerboundMovePlayerPacket.Pos(
                            mc.player.getX(), endY, mc.player.getZ(),
                            true
                    )
            );
        }

        mc.player.setPos(mc.player.getX(), endY, mc.player.getZ());
        ClientManager.message(ChatFormatting.GREEN + "Вы телепортировались на " + (offset > 0 ? "вверх" : "вниз") +
                " (" + (int) offset + " блоков).");
    }

    private double findFreeSpace(boolean up) {
        BlockPos start = mc.player.blockPosition();
        int step = up ? 1 : -1;

        for (int i = 1; i < 256; i++) {
            BlockPos checkPos = start.offset(0, i * step, 0);
            BlockPos checkPosAbove = checkPos.above();

            if (mc.level.isEmptyBlock(checkPos) && mc.level.isEmptyBlock(checkPosAbove)) {
                return i * step;
            }
        }
        return 0;
    }
}
