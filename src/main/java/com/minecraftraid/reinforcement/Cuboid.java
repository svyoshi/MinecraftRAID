package com.minecraftraid.reinforcement;

import org.bukkit.World;

public record Cuboid(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}
