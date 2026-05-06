package com.minecraftraid.registry;

import com.minecraftraid.model.RaidBlock;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * Same logical {@link RaidBlock} reachable under another block coordinate (second double-chest half).
     */
    public void putAlias(UUID worldId, int x, int y, int z, RaidBlock canonical) {
        byPosition.put(RaidBlock.positionKey(worldId, x, y, z), canonical);
    }

    /** Remove coordinate; clears every coordinate sharing this block's canonical identity. */
    public RaidBlock remove(World world, int x, int y, int z) {
        return remove(world.getUID(), x, y, z);
    }

    public RaidBlock remove(UUID worldId, int x, int y, int z) {
        RaidBlock touched = get(worldId, x, y, z);
        if (touched == null) {
            return null;
        }
        String canonKey = touched.positionKey();
        List<String> purge = new ArrayList<>();
        for (Map.Entry<String, RaidBlock> e : byPosition.entrySet()) {
            if (e.getValue().positionKey().equals(canonKey)) {
                purge.add(e.getKey());
            }
        }
        for (String k : purge) {
            byPosition.remove(k);
        }
        return touched;
    }

    public void clear() {
        byPosition.clear();
    }

    /** One entry per canonical {@link RaidBlock#positionKey()} for persistence. */
    public Collection<RaidBlock> snapshot() {
        LinkedHashMap<String, RaidBlock> unique = new LinkedHashMap<>();
        for (RaidBlock b : byPosition.values()) {
            unique.putIfAbsent(b.positionKey(), b);
        }
        return Collections.unmodifiableCollection(unique.values());
    }

    /** Update every coordinate bound to this canonical raid block identity. */
    public void replace(RaidBlock block) {
        String canonKey = block.positionKey();
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, RaidBlock> e : byPosition.entrySet()) {
            if (e.getValue().positionKey().equals(canonKey)) {
                keys.add(e.getKey());
            }
        }
        for (String k : keys) {
            byPosition.put(k, block);
        }
        if (keys.isEmpty()) {
            byPosition.put(canonKey, block);
        }
    }

    /** Unique canonical raid block count (not alias slot count). */
    public int size() {
        LinkedHashMap<String, RaidBlock> unique = new LinkedHashMap<>();
        for (RaidBlock b : byPosition.values()) {
            unique.putIfAbsent(b.positionKey(), b);
        }
        return unique.size();
    }
}
