package org.bukkit.generator;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.World;

public abstract class ChunkGenerator {
    public boolean shouldGenerateNoise() {
        return true;
    }

    public boolean shouldGenerateSurface() {
        return true;
    }

    public boolean shouldGenerateBedrock() {
        return true;
    }

    public boolean shouldGenerateCaves() {
        return true;
    }

    public boolean shouldGenerateDecorations() {
        return true;
    }

    public boolean shouldGenerateMobs() {
        return true;
    }

    public boolean shouldGenerateStructures() {
        return true;
    }

    public Location getFixedSpawnLocation(World world, Random random) {
        return null;
    }
}
