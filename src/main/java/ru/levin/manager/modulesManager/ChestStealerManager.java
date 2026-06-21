package ru.levin.manager.modulesManager;

import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ChestStealerManager {

    private final File file;
    private final Set<Item> whitelist = new HashSet<>();

    public ChestStealerManager() {
        this.file = new File(
                new File(Objects.requireNonNull(Minecraft.getInstance().gameDirectory), "files/modules"),
                "cheststealer.ew"
        );
        load();

        if (whitelist.isEmpty()) {
            addDefaultItems();
            save();
        }
    }

    private void addDefaultItems() {
        addItem("minecraft:totem_of_undying");
        addItem("minecraft:player_head");
    }

    public boolean addItem(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (whitelist.add(item)) {
                save();
                return true;
            }
        }
        return false;
    }

    public boolean removeItem(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (whitelist.remove(item)) {
                save();
                return true;
            }
        }
        return false;
    }

    public boolean isAllowed(Item item) {
        return whitelist.contains(item);
    }

    public Set<Item> getWhitelist() {
        return whitelist;
    }

    private void save() {
        try {
            file.getParentFile().mkdirs();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (Item item : whitelist) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                    writer.write(id.toString());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void load() {
        whitelist.clear();
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ResourceLocation id = ResourceLocation.tryParse(line.trim());
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    whitelist.add(BuiltInRegistries.ITEM.get(id));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
