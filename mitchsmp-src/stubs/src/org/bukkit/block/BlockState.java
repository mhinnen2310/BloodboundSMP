package org.bukkit.block;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public interface BlockState {
    Material getType();

    BlockData getBlockData();
}
