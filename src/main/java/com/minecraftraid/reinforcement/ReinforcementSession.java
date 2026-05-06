package com.minecraftraid.reinforcement;

import java.util.List;
import java.util.UUID;

public record ReinforcementSession(
        UUID sessionId,
        UUID ownerUuid,
        UUID worldId,
        List<ReinforcementTarget> targets,
        int countMatTier1,
        int countMatTier2,
        int countMatTier3,
        int totalXp,
        int hpPerTierSnapshot,
        long deadlineMillis
) {}
