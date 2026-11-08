package com.phantasia.core.world;

import com.phantasia.core.logic.Direction;

/** Stable arrival identifier within one authored map; coordinates can change. */
public record MapEntrance(String id, WorldPosition position, Direction facing) {}
