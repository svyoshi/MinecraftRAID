package com.minecraftraid.reinforcement;

/** One raid block position and its tier before upgrading (for validation on confirm). */
public record ReinforcementTarget(int x, int y, int z, int fromTier) {}
