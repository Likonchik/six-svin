package ru.levin.manager.commandManager.impl;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;
import ru.levin.manager.ClientManager;
import ru.levin.manager.commandManager.Command;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class HclipCommand extends Command {

    public HclipCommand() {
        super("hclip");
    }

    @Override
    public void execute(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(arg("offset", DoubleArgumentType.doubleArg())
                .executes(context -> {
                    double offset = DoubleArgumentType.getDouble(context, "offset");
                    teleport(offset);
                    return SINGLE_SUCCESS;
                }));
    }

    private void teleport(double offset) {
        if (offset == 0) {
            ClientManager.message(ChatFormatting.RED + "Смещение не может быть 0!");
            return;
        }

        Vec3 look = mc.player.getViewVector(1.0F).normalize();
        double x = mc.player.getX() + look.x * offset;
        double z = mc.player.getZ() + look.z * offset;
        double y = mc.player.getY();

        for (int i = 0; i < 19; i++) {
            mc.player.connection.send(
                    new ServerboundMovePlayerPacket.Pos(
                            mc.player.getX(), y, mc.player.getZ(),
                            true
                    )
            );
        }

        for (int i = 0; i < 19; i++) {
            mc.player.connection.send(
                    new ServerboundMovePlayerPacket.Pos(
                            x, y, z,
                            true
                    )
            );
        }

        mc.player.setPos(x, y, z);

        ClientManager.message(ChatFormatting.GREEN + "Вы телепортировались на " + ChatFormatting.RED + offset + ChatFormatting.WHITE + " блоков вперёд.");
    }
}
