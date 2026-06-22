package ru.levin.manager.commandManager;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.commandManager.impl.*;

import java.util.ArrayList;
import java.util.List;

public class CommandManager implements IMinecraft {
    private String prefix = ".";
    private final CommandDispatcher<SharedSuggestionProvider> dispatcher = new CommandDispatcher<>();
    private final SharedSuggestionProvider source = new ClientSuggestionProvider(null, mc);
    private final List<Command> commands = new ArrayList<>();

    public CommandManager() {
        register(new FriendCommand());
        register(new GpsCommand());
        register(new WayPointCommand());
        register(new UnHookCommand());
        register(new ConfigCommand());
        register(new ChestStealerCommand());
        register(new StaffCommand());
        register(new BlockESPCommand());
        register(new BindCommand());
        register(new DragCommand());
        register(new IrcCommand());
        register(new MacroCommand());
        register(new PanicCommand());
        register(new ParseCommand());
        register(new VclipCommand());
        register(new HclipCommand());
        register(new MusicCommand());

    }

    public void register(Command command) {
        if (command == null) return;
        command.register(dispatcher);
        this.commands.add(command);
    }

    public final CommandDispatcher getDispatcher() {
        return dispatcher;
    }
    public final SharedSuggestionProvider getSource() {
        return source;
    }
    public final String getPrefix() {
        return prefix;
    }
}