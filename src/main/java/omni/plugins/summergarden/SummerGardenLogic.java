/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.summergarden;

import omni.osrs.model.OmniWorldPoint;

/**
 * Pure decisions of the Summer Garden bot: where things are and which stage of the hand-in loop comes next.
 */
public final class SummerGardenLogic
{
	/** The stages of the hand-in loop. */
	enum BotState
	{
		EXIT_GARDEN,
		COMPLETE_AND_RESET,
		RETURN_TO_HOUSE,
		MAKE_LAST_JUICE,
		RUN
	}

	/** The garden's middle, for the "is the player in the garden" overlay switch. */
	public static final OmniWorldPoint GARDEN = new OmniWorldPoint(2915, 5490, 0);
	/** The tile the maze is started from. */
	public static final OmniWorldPoint MAZE_START = new OmniWorldPoint(2910, 5481, 0);
	/** The tile outside the house door. */
	public static final OmniWorldPoint OUTSIDE_HOUSE_DOOR = new OmniWorldPoint(3321, 3142, 0);
	/** The tile inside the house. */
	public static final OmniWorldPoint INSIDE_HOUSE = new OmniWorldPoint(3321, 3138, 0);
	/** Where Osman stands. */
	public static final OmniWorldPoint OSMAN_TILE = new OmniWorldPoint(3296, 3181, 0);
	/** The start tile when starting on the tile after the gate (also the maze start). */
	public static final OmniWorldPoint START_GATE = new OmniWorldPoint(2910, 5481, 0);
	/** The start tile of the regular start. */
	public static final OmniWorldPoint START_REGULAR = new OmniWorldPoint(2908, 5482, 0);
	/** The launch tile of the gate start. */
	public static final OmniWorldPoint LAUNCH_GATE = new OmniWorldPoint(2907, 5484, 0);
	/** The launch tile of the regular start. */
	public static final OmniWorldPoint LAUNCH_REGULAR = new OmniWorldPoint(2907, 5485, 0);

	/** Al Kharid's region. */
	public static final int REGION_ALKHARID = 13105;
	/** The garden's region. */
	public static final int REGION_GARDEN = 11605;
	/** The house door when closed. */
	public static final int OBJECT_HOUSE_DOOR_CLOSED = 1535;
	/** The sq'irk tree. */
	public static final int OBJECT_SUMMER_TREE = 12943;
	/** The shelf the beer glasses are taken from. */
	public static final int OBJECT_BEER_GLASS_SHELF = 21794;
	/** The door out of the garden. */
	public static final int OBJECT_GARDEN_EXIT = 12941;
	/** The gate back to the start tile. */
	public static final int OBJECT_MAZE_GATE = 11987;
	/** The "click here to play" widget group and child. */
	public static final int PLAY_GROUP = 378;
	/** The "click here to play" widget child. */
	public static final int PLAY_CHILD = 72;
	/** The juice stack the loop works towards. */
	public static final int FULL_JUICE = 25;
	/** Beer glasses carried. */
	public static final int FULL_GLASSES = 25;
	/** The y at which the maze run counts as finished. */
	public static final int MAZE_FINISHED_BELOW_Y = 5481;
	/** Local units a tile is wide. */
	private static final double LOCAL_TILE = 128.0;
	private static final double WALK_BACK_LOCAL_DISTANCE = 2350.0;

	private SummerGardenLogic()
	{
	}

	/**
	 * @param tile a tile
	 * @return whether it is in Al Kharid's region
	 */
	public static boolean inAlKharid(OmniWorldPoint tile)
	{
		return tile != null && tile.getRegionId() == REGION_ALKHARID;
	}

	/**
	 * @param tile a tile
	 * @return whether it is in the garden's region
	 */
	public static boolean inGarden(OmniWorldPoint tile)
	{
		return tile != null && tile.getRegionId() == REGION_GARDEN;
	}

	/**
	 * @param tile a tile
	 * @return whether it is inside Osman's house (the 7 by 5 area at 3318, 3137)
	 */
	public static boolean inHouse(OmniWorldPoint tile)
	{
		return tile != null && tile.getPlane() == 0 && tile.getX() >= 3318 && tile.getX() < 3318 + 7
			&& tile.getY() >= 3137 && tile.getY() < 3137 + 5;
	}

	/**
	 * Whether the player is far enough from the house door to need walking back to it.
	 *
	 * @param tile the player's tile
	 * @return whether the distance is at least 2350 local units (about 18.4 tiles)
	 */
	public static boolean farFromHouseDoor(OmniWorldPoint tile)
	{
		return tile.distance2D(OUTSIDE_HOUSE_DOOR) * LOCAL_TILE >= WALK_BACK_LOCAL_DISTANCE;
	}

	/**
	 * Where the loop goes next from the idle {@link BotState#RUN} stage, looking at the inventory and place.
	 *
	 * @param sqirks "Summer sq'irk" carried
	 * @param juice "Summer sq'irkjuice" carried
	 * @param glasses "Beer glass" carried
	 * @param inGarden whether the player is in the garden
	 * @param inAlKharid whether the player is in Al Kharid
	 * @return the next stage, {@link BotState#RUN} to stay
	 */
	static BotState nextFromRun(long sqirks, long juice, long glasses, boolean inGarden, boolean inAlKharid)
	{
		if (sqirks >= 2 && juice == FULL_JUICE)
		{
			if (inGarden)
			{
				return BotState.EXIT_GARDEN;
			}
			if (inAlKharid)
			{
				return BotState.MAKE_LAST_JUICE;
			}
		}
		else if (juice > 0 && inAlKharid)
		{
			return BotState.COMPLETE_AND_RESET;
		}
		else if (sqirks == 0 && glasses < FULL_GLASSES && juice == 0 && inAlKharid)
		{
			return BotState.RETURN_TO_HOUSE;
		}
		return BotState.RUN;
	}

	/**
	 * Where the loop goes after leaving the garden.
	 *
	 * @param sqirks "Summer sq'irk" carried
	 * @return {@link BotState#MAKE_LAST_JUICE} with sq'irks left, else {@link BotState#COMPLETE_AND_RESET}
	 */
	static BotState afterExit(long sqirks)
	{
		return sqirks >= 2 ? BotState.MAKE_LAST_JUICE : BotState.COMPLETE_AND_RESET;
	}

	/**
	 * Whether the text is a line of Osman's chat that is just clicked through.
	 *
	 * @param text a dialogue text without tags
	 * @return {@code true} for the three lines that are continued on
	 */
	static boolean isOsmanSmallTalk(String text)
	{
		return "Hello again.".equals(text) || "I'd like to talk about sq'irks.".equals(text)
			|| "I have some sq'irk juice for you.".equals(text);
	}

	/**
	 * @param text a dialogue text without tags
	 * @return whether it is Osman's reward line, which ends the hand-in
	 */
	static boolean isOsmanReward(String text)
	{
		return text != null && text.contains("Osman imparts some Thieving advice");
	}
}
