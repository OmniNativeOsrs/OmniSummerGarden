/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.summergarden;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import omni.osrs.action.OmniActionResult;
import omni.osrs.api.OmniContext;
import omni.osrs.api.OmniItem;
import omni.osrs.api.OmniLocalPlayer;
import omni.osrs.api.OmniNPC;
import omni.osrs.api.OmniObject;
import omni.osrs.model.OmniWorldPoint;
import omni.osrs.plugin.OmniOverlayBuilder;
import omni.osrs.plugin.OmniOverlayStyle;
import omni.osrs.plugin.OmniPlugin;
import omni.osrs.plugin.OmniPluginConfigChange;
import omni.osrs.plugin.OmniPluginRuntime;
import omni.osrs.plugin.OmniTickResult;
import omni.osrs.walk.WalkResult;
import omni.plugins.summergarden.SummerGardenConfig.ShowNumbers;
import omni.plugins.summergarden.SummerGardenLogic.BotState;

/**
 * Summer Garden: shows when to start the maze so the six elementals can be passed, and optionally runs the maze,
 * makes the sq'irk juice, hands it in to Osman and gets more beer glasses.
 *
 * <p>{@link #onTick()} runs once per game tick and sends at most one action; waits are tick counters and loops are
 * one step per tick.
 */
public class SummerGardenPlugin implements OmniPlugin
{
	private static final String SQIRK = "Summer sq'irk";
	private static final String JUICE = "Summer sq'irkjuice";
	private static final String GLASS = "Beer glass";
	private static final String PESTLE = "Pestle and mortar";
	private static final String OSMAN = "Osman";
	private static final String APPRENTICE = "Apprentice";
	private static final int STAMINA_VARBIT = 25;
	private static final int GARDEN_RANGE = 50;
	private static final int STALE_TICKS = 8;
	private static final int DOOR_WAIT = 3;
	private static final int SHELF_WAIT = 5;
	private static final int SHELF_FIRST_WAIT = 6;
	private static final int USE_WAIT = 2;
	private static final int RUN_WAIT = 2;
	private static final int PERCENT = 100;
	private static final String STAMINA_MESSAGE = "[Summer Garden] Low Stamina Warning";
	private static final String CYCLE_MESSAGE = "[Summer Garden] Cycle Ready";

	private final OmniPluginRuntime runtime;
	private final OmniContext ctx;
	private final SummerGardenConfig config;
	private final ElementalCollision collision = new ElementalCollision();

	private BotState botState = BotState.RUN;
	private int wait;
	private int staleTicks;
	private String lastInteracted = "";
	private boolean sentStaminaNotification;
	private boolean sentInventoryFullNotification;
	private boolean nearGarden;
	private boolean crushed;

	/** The last click, so it is not sent again while the game catches up. */
	private CompletableFuture<OmniActionResult> inFlight;
	private CompletableFuture<WalkResult> walking;

	/**
	 * Starts a session.
	 *
	 * @param runtime the session's runtime
	 * @param config the config, already filled from the plugin's settings
	 */
	public SummerGardenPlugin(OmniPluginRuntime runtime, SummerGardenConfig config)
	{
		this.runtime = runtime;
		this.ctx = new OmniContext(runtime.game());
		this.config = config;
		collision.setGateStart(config.useGateStartPoint());
	}

	@Override
	public void close()
	{
		if (walking != null)
		{
			ctx.walker.cancel();
		}
		walking = null;
		inFlight = null;
		SummerGardenOverlay.clear();
	}

	@Override
	public void reset()
	{
		inFlight = null;
		walking = null;
		lastInteracted = "";
		wait = 0;
	}

	@Override
	public String status()
	{
		if (!config.autoHandInAndReset() && !config.autoMazeCompletion())
		{
			return nearGarden ? "Counting down to the start" : "Not in the garden";
		}
		return botState.name();
	}

	@Override
	public boolean onConfigChanged(OmniPluginConfigChange change)
	{
		config.load(change.getCurrent());
		collision.setGateStart(config.useGateStartPoint());
		return true;
	}

	@Override
	public void overlay(OmniOverlayBuilder overlay)
	{
		SummerGardenOverlay.render(overlay);
	}

	@Override
	public OmniTickResult onTick()
	{
		try
		{
			serverTick();
		}
		catch (RuntimeException failure)
		{
			runtime.log().warn("Summer Garden tick failed: {}", String.valueOf(failure.getMessage()));
		}
		return OmniTickResult.next();
	}

	private void serverTick()
	{
		if (!ctx.game.isAutomationReady())
		{
			return;
		}
		OmniLocalPlayer player = ctx.localPlayer.current();
		if (player == null)
		{
			return;
		}
		OmniWorldPoint here = player.getLocation();
		trackElementals(here);
		if (ctx.widgets.isVisible(SummerGardenLogic.PLAY_GROUP, SummerGardenLogic.PLAY_CHILD))
		{
			return;
		}
		if (wait > 0)
		{
			wait--;
			return;
		}
		if (inFlight != null && !inFlight.isDone() || walking != null && !walking.isDone())
		{
			return;
		}
		trackInteracting(player);
		boolean acted = false;
		if (config.autoHandInAndReset())
		{
			switch (botState)
			{
				case RUN:
					botState = SummerGardenLogic.nextFromRun(count(SQIRK), count(JUICE), count(GLASS),
						SummerGardenLogic.inGarden(here), SummerGardenLogic.inAlKharid(here));
					break;
				case EXIT_GARDEN:
					acted = exitGarden(here);
					break;
				case MAKE_LAST_JUICE:
					acted = makeLastJuice(here);
					break;
				case COMPLETE_AND_RESET:
					completeAndReset(here);
					if (config.sendInvFullNotification() && !sentInventoryFullNotification)
					{
						runtime.log().info("You need a beer glass and a pestle and mortar to make sq'irk juice.");
						sentInventoryFullNotification = true;
					}
					return;
				case RETURN_TO_HOUSE:
					returnToHouse(here);
					return;
				default:
					break;
			}
		}
		if (config.autoMazeCompletion() && !acted)
		{
			doMaze(player, here);
		}
	}

	// ==================================================================
	//  The overlay and the countdown
	// ==================================================================

	/** The game tick listener: learns the elementals' cycles, counts down and warns about stamina. */
	private void trackElementals(OmniWorldPoint here)
	{
		if (here.distance2D(SummerGardenLogic.GARDEN) >= GARDEN_RANGE)
		{
			nearGarden = false;
			SummerGardenOverlay.clear();
			return;
		}
		nearGarden = true;
		int tickCount = ctx.prayers.serverTick();
		List<OmniNPC> elementals = ctx.npcs.query().withId(1801, 1802, 1803, 1804, 1805, 1806).list();
		for (OmniNPC npc : elementals)
		{
			collision.updatePosition(npc.getId(), npc.getLocation(), tickCount);
		}
		collision.updateCountdownTimer(tickCount);
		if (config.cycleNotification() && collision.getTicksUntilStart() == config.notifyTicksBeforeStart())
		{
			runtime.log().info("{}", CYCLE_MESSAGE);
		}
		checkStamina();
		pushOverlay(elementals);
	}

	private void checkStamina()
	{
		int threshold = config.staminaThreshold() * PERCENT;
		if (threshold == 0)
		{
			return;
		}
		boolean staminaActive = ctx.vars.varbit(STAMINA_VARBIT) != 0;
		int energy = ctx.movement.runEnergy();
		if (energy <= threshold && !staminaActive && !sentStaminaNotification)
		{
			runtime.log().info("{}", STAMINA_MESSAGE);
			sentStaminaNotification = true;
		}
		else if (energy > threshold)
		{
			sentStaminaNotification = false;
		}
	}

	private void pushOverlay(List<OmniNPC> elementals)
	{
		Map<String, String> rows = new LinkedHashMap<>();
		List<SummerGardenOverlay.Mark> marks = new ArrayList<>();
		if (config.showCountdownTimer())
		{
			rows.put("Ticks until start", String.valueOf(collision.getTicksUntilStart()));
		}
		for (OmniNPC npc : elementals)
		{
			int parity = collision.getParity(npc.getId());
			marks.add(new SummerGardenOverlay.Mark(npc.getLocation(), style(npc.getId(), parity)));
			ShowNumbers numbers = config.showNumbers();
			if (numbers == ShowNumbers.YES || parity != 0 && numbers == ShowNumbers.ON_WRONG)
			{
				rows.put("Elemental " + (npc.getId() - ElementalCollision.FIRST_ID + 1),
					parity != -1 ? String.valueOf(parity) : "?");
			}
		}
		if (config.highlightLaunchTile())
		{
			boolean gate = config.useGateStartPoint();
			marks.add(new SummerGardenOverlay.Mark(gate ? SummerGardenLogic.START_GATE : SummerGardenLogic.START_REGULAR,
				OmniOverlayStyle.INFO));
			marks.add(new SummerGardenOverlay.Mark(gate ? SummerGardenLogic.LAUNCH_GATE
				: SummerGardenLogic.LAUNCH_REGULAR, OmniOverlayStyle.INFO));
		}
		SummerGardenOverlay.update(rows, marks);
	}

	private OmniOverlayStyle style(int npcId, int parity)
	{
		if (collision.isLaunchCycle() && (npcId == ElementalCollision.FIRST_ID || npcId == ElementalCollision.FIRST_ID + 2))
		{
			return OmniOverlayStyle.INFO;
		}
		if (parity == 0)
		{
			return OmniOverlayStyle.TARGET;
		}
		return parity == -1 ? OmniOverlayStyle.PATH : OmniOverlayStyle.DANGER;
	}

	// ==================================================================
	//  The bot
	// ==================================================================

	/** Remembers the name of the NPC the player last interacted with. */
	private void trackInteracting(OmniLocalPlayer player)
	{
		if (!player.isInteracting() || player.getInteractingNpcIndex() == -1)
		{
			return;
		}
		int index = player.getInteractingNpcIndex();
		OmniNPC npc = ctx.npcs.query().where(candidate -> candidate.getIndex() == index).first();
		if (npc != null && npc.getName() != null)
		{
			lastInteracted = npc.getName();
		}
	}

	private long count(String name)
	{
		return ctx.inventory.count(name);
	}

	private OmniObject door()
	{
		return ctx.objects.query().withId(SummerGardenLogic.OBJECT_HOUSE_DOOR_CLOSED)
			.at(SummerGardenLogic.OUTSIDE_HOUSE_DOOR).nearest();
	}

	private boolean exitGarden(OmniWorldPoint here)
	{
		if (SummerGardenLogic.inAlKharid(here) && SummerGardenLogic.inHouse(here))
		{
			botState = SummerGardenLogic.afterExit(count(SQIRK));
			return false;
		}
		OmniObject exit = ctx.objects.query().withId(SummerGardenLogic.OBJECT_GARDEN_EXIT).nearest();
		if (exit == null)
		{
			return false;
		}
		if (SummerGardenLogic.inGarden(here))
		{
			inFlight = exit.interact(firstAction(exit));
			wait = DOOR_WAIT;
			return true;
		}
		return false;
	}

	/** Gets the player into Osman's house, opening the door when it is closed. */
	private boolean goInsideHouse(OmniWorldPoint here)
	{
		if (!SummerGardenLogic.inAlKharid(here) || here.getPlane() != 0)
		{
			return false;
		}
		if (SummerGardenLogic.inHouse(here))
		{
			return true;
		}
		OmniObject door = door();
		if (door != null)
		{
			inFlight = door.interact(firstAction(door));
			wait = DOOR_WAIT;
			return false;
		}
		inFlight = ctx.movement.walkHere(SummerGardenLogic.INSIDE_HOUSE);
		wait = USE_WAIT;
		return false;
	}

	private boolean makeLastJuice(OmniWorldPoint here)
	{
		if (count(SQIRK) < 2)
		{
			botState = BotState.COMPLETE_AND_RESET;
			return false;
		}
		if (!goInsideHouse(here))
		{
			return true;
		}
		if (ctx.inventory.isFull())
		{
			inFlight = ctx.inventory.drop(JUICE);
			wait = USE_WAIT;
			return true;
		}
		if (!crushed)
		{
			if (count(GLASS) < 1)
			{
				OmniObject shelf = ctx.objects.query().withId(SummerGardenLogic.OBJECT_BEER_GLASS_SHELF).nearest();
				if (shelf != null)
				{
					inFlight = shelf.interact(firstAction(shelf));
					wait = SHELF_FIRST_WAIT;
				}
				return true;
			}
			if (ctx.inventory.contains(PESTLE))
			{
				crushed = true;
				return useOnSqirk();
			}
		}
		if (count(JUICE) != SummerGardenLogic.FULL_JUICE + 1)
		{
			inFlight = ctx.groundItems.take(JUICE);
			wait = SHELF_WAIT;
			return true;
		}
		crushed = false;
		botState = BotState.COMPLETE_AND_RESET;
		return false;
	}

	private boolean useOnSqirk()
	{
		OmniItem pestle = ctx.inventory.query().withName(PESTLE).first();
		OmniItem sqirk = ctx.inventory.query().withName(SQIRK).first();
		if (pestle == null || sqirk == null)
		{
			return false;
		}
		inFlight = pestle.useOn(sqirk);
		wait = USE_WAIT;
		sentInventoryFullNotification = false;
		return true;
	}

	private void completeAndReset(OmniWorldPoint here)
	{
		if (!SummerGardenLogic.inAlKharid(here) || here.getPlane() != 0)
		{
			return;
		}
		if (count(JUICE) == 0)
		{
			botState = BotState.RETURN_TO_HOUSE;
			return;
		}
		OmniObject door = door();
		if (door != null && SummerGardenLogic.inHouse(here))
		{
			inFlight = door.interact(firstAction(door));
			wait = DOOR_WAIT;
			return;
		}
		OmniNPC osman = ctx.npcs.query().withName(OSMAN).nearest();
		if (osman == null)
		{
			walking = ctx.walker.walkAsync(SummerGardenLogic.OSMAN_TILE, 2);
			return;
		}
		if (!OSMAN.equals(lastInteracted))
		{
			inFlight = osman.interact("Talk-to");
			wait = USE_WAIT;
			return;
		}
		talkToOsman();
	}

	/** Clicks through Osman's chat and picks the sq'irk option; his reward line ends the hand-in. */
	private void talkToOsman()
	{
		String text = strip(ctx.dialogue.text());
		Optional<String> option = ctx.dialogue.findOption("I'd like to talk about sq'irks.");
		if (option.isPresent())
		{
			inFlight = ctx.dialogue.select(option.get());
			staleTicks = 0;
			return;
		}
		if (SummerGardenLogic.isOsmanReward(text) && ctx.dialogue.canContinue())
		{
			inFlight = ctx.dialogue.clickContinue();
			botState = BotState.RETURN_TO_HOUSE;
			staleTicks = 0;
			return;
		}
		if (SummerGardenLogic.isOsmanSmallTalk(text) && ctx.dialogue.canContinue())
		{
			inFlight = ctx.dialogue.clickContinue();
			staleTicks = 0;
			return;
		}
		forgetStaleConversation();
	}

	/** A conversation that went nowhere is started again after a few ticks. */
	private void forgetStaleConversation()
	{
		if (ctx.dialogue.isOpen())
		{
			staleTicks = 0;
			return;
		}
		if (++staleTicks >= STALE_TICKS)
		{
			staleTicks = 0;
			lastInteracted = "";
		}
	}

	private void returnToHouse(OmniWorldPoint here)
	{
		if (SummerGardenLogic.inGarden(here))
		{
			botState = BotState.RUN;
			return;
		}
		if (!SummerGardenLogic.inHouse(here) && SummerGardenLogic.farFromHouseDoor(here))
		{
			walking = ctx.walker.walkAsync(SummerGardenLogic.OUTSIDE_HOUSE_DOOR, 2);
			return;
		}
		if (!goInsideHouse(here))
		{
			return;
		}
		if (count(GLASS) < SummerGardenLogic.FULL_GLASSES)
		{
			OmniObject shelf = ctx.objects.query().withId(SummerGardenLogic.OBJECT_BEER_GLASS_SHELF).nearest();
			if (shelf != null)
			{
				inFlight = shelf.interact(firstAction(shelf));
				wait = SHELF_WAIT;
			}
			return;
		}
		OmniNPC apprentice = ctx.npcs.query().withName(APPRENTICE).nearest();
		if (apprentice == null)
		{
			return;
		}
		if (!APPRENTICE.equals(lastInteracted))
		{
			inFlight = apprentice.interact("Teleport");
			wait = RUN_WAIT;
			return;
		}
		forgetStaleConversation();
	}

	private void doMaze(OmniLocalPlayer player, OmniWorldPoint here)
	{
		if (ctx.inventory.isFull() && !ctx.inventory.contains(GLASS) || !SummerGardenLogic.inGarden(here))
		{
			return;
		}
		if (count(SQIRK) >= 2 && ctx.inventory.contains(GLASS) && ctx.inventory.contains(PESTLE))
		{
			useOnSqirk();
			return;
		}
		if (player.isMoving())
		{
			return;
		}
		if (here.equals(SummerGardenLogic.MAZE_START))
		{
			if (config.waitForOneClick() || collision.getTicksUntilStart() == 0)
			{
				OmniObject tree = ctx.objects.query().withId(SummerGardenLogic.OBJECT_SUMMER_TREE).nearest();
				if (tree != null)
				{
					inFlight = tree.interact(firstAction(tree));
					wait = RUN_WAIT;
				}
			}
			return;
		}
		if (here.getY() >= SummerGardenLogic.MAZE_FINISHED_BELOW_Y)
		{
			return;
		}
		OmniObject gate = ctx.objects.query().withId(SummerGardenLogic.OBJECT_MAZE_GATE).nearest();
		if (gate != null)
		{
			inFlight = gate.interact(firstAction(gate));
			wait = RUN_WAIT;
		}
	}

	private static String strip(String text)
	{
		return text == null ? "" : text.replaceAll("<[^>]*>", "").trim();
	}

	private static String firstAction(OmniObject obj)
	{
		for (String action : obj.getActions())
		{
			if (action != null && !action.isBlank())
			{
				return action;
			}
		}
		return "Open";
	}
}
