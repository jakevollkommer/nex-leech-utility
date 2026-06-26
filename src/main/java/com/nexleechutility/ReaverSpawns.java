package com.nexleechutility;

/**
 * Blood-reaver spawn SLOTS relative to Nex's tile, derived from 547 logged spawns (96 batches).
 *
 * Each slot is one distinct reaver position. Reavers in a single kill are spread out (median 3
 * tiles apart) and the cross-kill wobble of a tile is one reaver's jitter, NOT extra reavers - so
 * nearby tiles were clustered (radius 1; each filled slot holds ~1.0 reaver) and ranked by FILL
 * RATE (how often any reaver lands in that slot per kill). Listed most-likely first.
 *
 * Note the spawn is genuinely spread: even the top slot fills only ~42% of kills, and ~6 of many
 * slots fill each kill, so this predicts "the most likely distinct spots", not the exact set.
 * Offsets are {dx, dy}; applied at Nex's live tile and clipped to walkable tiles.
 */
final class ReaverSpawns
{
	// Ranked by fill rate (42%, 40%, 36%, 36%, 32%, 28%, 23%, 18%, 16%, 14%, 14%, 14%).
	static final int[][] SLOTS =
	{
		{-5, 1}, {1, 1}, {4, 1}, {8, 1}, {1, 6}, {0, -3},
		{-3, 1}, {1, -5}, {-2, -1}, {1, 10}, {6, 1}, {5, -1},
	};

	private ReaverSpawns()
	{
	}
}
