package dev.mrsusu.pathfinding;

import org.bukkit.Location;

/**
 * Result returned by {@link WanderPathfinder}.
 *
 * @param destination The safe target location (may equal origin if no safe spot found).
 * @param found       True when a valid dry-land destination was found within the radius.
 */
public record PathResult(Location destination, boolean found) {}
