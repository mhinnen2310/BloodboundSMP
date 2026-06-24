package org.bukkit;

import org.bukkit.block.Block;

public class Location implements Cloneable {
    public Location(World world, double x, double y, double z) {
    }

    public Location(World world, double x, double y, double z, float yaw, float pitch) {
    }

    public World getWorld() {
        return null;
    }

    public double getX() {
        return 0.0D;
    }

    public double getY() {
        return 0.0D;
    }

    public double getZ() {
        return 0.0D;
    }

    public int getBlockX() {
        return 0;
    }

    public int getBlockY() {
        return 0;
    }

    public int getBlockZ() {
        return 0;
    }

    public float getYaw() {
        return 0.0F;
    }

    public float getPitch() {
        return 0.0F;
    }

    public Location add(double x, double y, double z) {
        return this;
    }

    public double distance(Location other) {
        return 0.0D;
    }

    public double distanceSquared(Location other) {
        return 0.0D;
    }

    public Block getBlock() {
        return null;
    }
}
