/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.summergarden;

import omni.osrs.model.OmniWorldPoint;

/**
 * When the six summer elementals can be walked past: it learns each elemental's place in its patrol cycle from the tick
 * it is seen on its home tile, and works out the start tick of the 60 tick super-cycle that needs the fewest waits.
 */
public final class ElementalCollision
{
	/** First summer elemental id. */
	public static final int FIRST_ID = 1801;
	/** Last summer elemental id. */
	public static final int LAST_ID = 1806;

	private static final OmniWorldPoint[] HOMES = {
		new OmniWorldPoint(2907, 5488, 0), new OmniWorldPoint(2907, 5490, 0), new OmniWorldPoint(2910, 5487, 0),
		new OmniWorldPoint(2912, 5485, 0), new OmniWorldPoint(2921, 5486, 0), new OmniWorldPoint(2921, 5495, 0)};
	private static final int[] CYCLE_LENGTHS = {10, 10, 12, 20, 12, 12};
	private static final int SUPER_CYCLE = 60;
	private static final int LAUNCH_TICKS = 8;

	private static final boolean[][] VALID_TICKS_NORMAL_START = {
		{false, false, false, false, false, false, false, true, false, false},
		{false, false, false, true, false, false, false, false, true, false},
		{true, false, false, false, false, true, true, true, false, false, false, false},
		{true, true, true, true, false, false, false, true, true, true, true, false, false, true, false, false, false,
			false, false, true},
		{false, false, true, false, false, false, false, false, true, false, false, false},
		{true, false, false, false, true, false, true, true, true, true, true, true}};
	private static final boolean[][] VALID_TICKS_GATE_START = {
		{false, false, false, false, false, false, true, false, false, false},
		{true, true, true, false, false, false, false, true, false, false},
		{false, false, true, true, true, true, false, false, false, false, true, true},
		{true, true, true, false, false, false, true, true, true, true, true, true, false, false, false, false, false,
			true, true, true},
		{true, false, true, false, false, false, true, false, false, false, false, true},
		{false, false, false, true, false, false, true, true, true, true, true, false}};

	private final int[] tickBasis = {-1, -1, -1, -1, -1, -1};
	private boolean gateStart;
	private int ticksUntilStart = -1;

	/**
	 * @param npcId an NPC id
	 * @return whether it is a summer elemental
	 */
	public static boolean isSummerElemental(int npcId)
	{
		return npcId >= FIRST_ID && npcId <= LAST_ID;
	}

	/**
	 * Notes an elemental's position: standing on its home tile fixes its place in the cycle.
	 *
	 * @param npcId the elemental's id
	 * @param location where it is
	 * @param tickCount the current tick
	 */
	public void updatePosition(int npcId, OmniWorldPoint location, int tickCount)
	{
		if (!isSummerElemental(npcId))
		{
			return;
		}
		int index = npcId - FIRST_ID;
		if (location.equals(HOMES[index]))
		{
			tickBasis[index] = tickCount;
		}
	}

	/**
	 * Works out how many ticks remain until the best start of the super-cycle.
	 *
	 * @param tickCount the current tick
	 */
	public void updateCountdownTimer(int tickCount)
	{
		ticksUntilStart = moduloPositive(bestStartPoint() - tickCount, SUPER_CYCLE);
	}

	/**
	 * @return the start tick (within the 60 tick cycle) with the lowest total parity
	 */
	int bestStartPoint()
	{
		int smallestParity = Integer.MAX_VALUE;
		int smallestParityIndex = -1;
		for (int i = 0; i < SUPER_CYCLE; ++i)
		{
			int paritySum = paritySum(i);
			if (paritySum >= smallestParity)
			{
				continue;
			}
			smallestParity = paritySum;
			smallestParityIndex = i;
		}
		if (smallestParityIndex == -1)
		{
			throw new IllegalStateException("Every elemental should be passable on at least one tick.");
		}
		return smallestParityIndex;
	}

	int paritySum(int startCycle)
	{
		int sum = 0;
		for (int elementalIndex = 0; elementalIndex < HOMES.length; ++elementalIndex)
		{
			sum += parityForStartCycle(startCycle, elementalIndex);
		}
		return sum;
	}

	private int parityForStartCycle(int startCycle, int elementalIndex)
	{
		if (tickBasis[elementalIndex] == -1)
		{
			return -1;
		}
		int basis = tickBasis[elementalIndex];
		boolean[] valid = gateStart ? VALID_TICKS_GATE_START[elementalIndex] : VALID_TICKS_NORMAL_START[elementalIndex];
		for (int parity = 0; parity < valid.length; ++parity)
		{
			int cycleLength = CYCLE_LENGTHS[elementalIndex];
			int indexWithParity = moduloPositive(startCycle - basis - parity, cycleLength);
			if (valid[indexWithParity])
			{
				return parity;
			}
		}
		throw new IllegalStateException("Every elemental should be passable on at least one tick.");
	}

	/**
	 * How many waits an elemental needs at the best start.
	 *
	 * @param elementalId the elemental's id
	 * @return the parity, 0 for on-parity, or -1 while some elemental has not been seen yet
	 */
	public int getParity(int elementalId)
	{
		for (int basis : tickBasis)
		{
			if (basis == -1)
			{
				return -1;
			}
		}
		return parityForStartCycle(bestStartPoint(), elementalId - FIRST_ID);
	}

	/**
	 * @return whether the launch window is near: fewer than 8 ticks to the start
	 */
	public boolean isLaunchCycle()
	{
		return ticksUntilStart < LAUNCH_TICKS;
	}

	/**
	 * @param gateStart whether the start tile is the one after the gate
	 */
	public void setGateStart(boolean gateStart)
	{
		this.gateStart = gateStart;
	}

	public int getTicksUntilStart()
	{
		return ticksUntilStart;
	}

	static int moduloPositive(int base, int mod)
	{
		return (base % mod + mod) % mod;
	}
}
