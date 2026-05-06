package com.minecraftraid.registry;

import com.minecraftraid.model.RaidBlock;
import org.bukkit.World;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RaidBlockRegistry {

    private final Map<String, RaidBlock> byPosition = new ConcurrentHashMap<>();

    public RaidBlock get(World world, int x, int y, int z) {
        return byPosition.get(RaidBlock.positionKey(world.getUID(), x, y, z));
    }

    public RaidBlock get(UUID worldId, int x, int y, int z) {
        return byPosition.get(RaidBlock.positionKey(worldId, x, y, z));
    }

    public void put(RaidBlock block) {
        byPosition.put(block.positionKey(), block);
    }

    public RaidBlock remove(World world, int x, int y, int z) {
        return byPosition.remove(RaidBlock.positionKey(world.getUID(), x, y, z));
    }

    public RaidBlock remove(UUID worldId, int x, int y, int z) {
        return byPosition.remove(RaidBlock.positionKey(worldId, x, y, z));
    }

    public void clear() {
        byPosition.clear();
    }

    public Collection<RaidBlock> snapshot() {
        return Collections.unmodifiableCollection(byPosition.values());
    }

    public void replace(RaidBlock block) {
        byPosition.put(block.positionKey(), block);
    }

    public int size() {
        return byPosition.size();
    }
}
