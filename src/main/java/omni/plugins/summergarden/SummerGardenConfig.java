/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.summergarden;

import java.util.ArrayList;
import java.util.List;
import omni.osrs.plugin.OmniPluginSetting;
import omni.osrs.plugin.OmniPluginValues;

/**
 * The plugin's settings.
 */
public class SummerGardenConfig
{
	private static final String GENERAL = "General";
	private static final String BOT = "Bot Settings";

	/** When the number of resets an elemental needs is shown. */
	public enum ShowNumbers
	{
		YES,
		ON_WRONG,
		NO
	}

	private boolean highlightLaunchTile = true;
	private ShowNumbers showNumbers = ShowNumbers.YES;
	private int staminaThreshold = 25;
	private boolean cycleNotification;
	private int notifyTicksBeforeStart = 7;
	private boolean showCountdownTimer = true;
	private boolean useGateStartPoint = true;
	private boolean autoMazeCompletion;
	private boolean autoHandInAndReset;
	private boolean waitForOneClick;
	private boolean sendInvFullNotification;

	/**
	 * The settings the client shows, one per config field.
	 *
	 * @return the settings
	 */
	public static List<OmniPluginSetting> settings()
	{
		List<OmniPluginSetting> settings = new ArrayList<>();
		settings.add(OmniPluginSetting.info("guide", "How to use",
			"Turn the plugin on in the Summer Garden minigame. For full automation, enable the 'Auto Hand in and Reset' "
				+ "and 'Auto Maze Completion' options. If 'Auto Hand in and Reset' is on your inventory should only "
				+ "contain a pestle and mortar (stamina potions are not supported). With only 'Auto Maze Completion' "
				+ "you need enough beer glasses and must hand in or bank your sq'irk yourself before returning to the "
				+ "garden. Note that if collision check is not disabled, the plugin might fail the first few attempts "
				+ "until it is tick perfect."));
		settings.add(OmniPluginSetting.toggle("highlightLaunchTile", "Highlight Launch Tile",
			"Whether or not to highlight the tile at which the first elemental will be when you click the Sq'irk tree.",
			true).inGroup(GENERAL));
		settings.add(OmniPluginSetting.choice("showNumbers", "Show Numbers",
			"Whether to show how many resets each elemental will need.", ShowNumbers.class, ShowNumbers.YES)
			.inGroup(GENERAL));
		settings.add(OmniPluginSetting.number("staminaWarning", "Low Stamina Threshold",
			"What stamina level to warn to use stamina potion (0 to disable).", 25, 0, 100).inGroup(GENERAL));
		settings.add(OmniPluginSetting.toggle("cycleNotification", "Cycle Notification",
			"Whether to log a message when the launch cycle is starting.", false).inGroup(GENERAL));
		settings.add(OmniPluginSetting.number("notifyTicksBeforeStart", "Notify ticks before start",
			"Requires \"Cycle Notification\" to be enabled. How many ticks prior to the time you will need to click the "
				+ "tree the message is sent.", 7, 0, 59).inGroup(GENERAL));
		settings.add(OmniPluginSetting.toggle("showCountdownTimer", "Show countdown timer",
			"Shows the ticks until it is time to start running the maze. Click on the tree when it says \"0\"", true)
			.inGroup(GENERAL));
		settings.add(OmniPluginSetting.toggle("useGateStartPoint", "Start on tile after gate",
			"Whether or not to start from the tile you stand on after entering the gate, rather than the normal start "
				+ "tile which is 3 tiles further. This saves the click of having to run to the start tile after "
				+ "clicking the gate.", true).inGroup(GENERAL));
		settings.add(OmniPluginSetting.toggle("autoMazeCompletion", "Auto Maze Completion",
			"Clicks the tree, makes the sq'irk juice and reenters the summer maze.", false).inGroup(BOT));
		settings.add(OmniPluginSetting.toggle("handInAndReset", "Auto Hand in and Reset",
			"Hands in all of your juice to Osman, gets more beer glasses from the shelves and teleports to the garden.",
			false).inGroup(BOT));
		settings.add(OmniPluginSetting.toggle("waitForOneClick", "Disable Collision Check",
			"Immediately clicks the tree without checking elemental NPC position. This is useful when you're in a world "
				+ "with many people and you aren't caught.", false).inGroup(BOT));
		settings.add(OmniPluginSetting.toggle("sendInvFullNotification", "Full Inventory Notification",
			"Logs a message when there's no more empty beer glasses left.", false).inGroup(BOT));
		return settings;
	}

	/**
	 * A config filled from the plugin's settings.
	 *
	 * @param values the settings
	 * @return the config
	 */
	public static SummerGardenConfig from(OmniPluginValues values)
	{
		SummerGardenConfig config = new SummerGardenConfig();
		config.load(values);
		return config;
	}

	/**
	 * Reads every setting present in {@code values} into this config (a live settings change).
	 *
	 * @param values the settings
	 */
	public void load(OmniPluginValues values)
	{
		highlightLaunchTile = bool(values, "highlightLaunchTile", highlightLaunchTile);
		showNumbers = values.contains("showNumbers") ? values.getEnum("showNumbers", ShowNumbers.class) : showNumbers;
		staminaThreshold = integer(values, "staminaWarning", staminaThreshold);
		cycleNotification = bool(values, "cycleNotification", cycleNotification);
		notifyTicksBeforeStart = integer(values, "notifyTicksBeforeStart", notifyTicksBeforeStart);
		showCountdownTimer = bool(values, "showCountdownTimer", showCountdownTimer);
		useGateStartPoint = bool(values, "useGateStartPoint", useGateStartPoint);
		autoMazeCompletion = bool(values, "autoMazeCompletion", autoMazeCompletion);
		autoHandInAndReset = bool(values, "handInAndReset", autoHandInAndReset);
		waitForOneClick = bool(values, "waitForOneClick", waitForOneClick);
		sendInvFullNotification = bool(values, "sendInvFullNotification", sendInvFullNotification);
	}

	private static boolean bool(OmniPluginValues values, String key, boolean fallback)
	{
		return values.contains(key) ? values.getBoolean(key) : fallback;
	}

	private static int integer(OmniPluginValues values, String key, int fallback)
	{
		return values.contains(key) ? values.getInt(key) : fallback;
	}

	public boolean highlightLaunchTile()
	{
		return highlightLaunchTile;
	}

	public ShowNumbers showNumbers()
	{
		return showNumbers;
	}

	public int staminaThreshold()
	{
		return staminaThreshold;
	}

	public boolean cycleNotification()
	{
		return cycleNotification;
	}

	public int notifyTicksBeforeStart()
	{
		return notifyTicksBeforeStart;
	}

	public boolean showCountdownTimer()
	{
		return showCountdownTimer;
	}

	public boolean useGateStartPoint()
	{
		return useGateStartPoint;
	}

	public boolean autoMazeCompletion()
	{
		return autoMazeCompletion;
	}

	public boolean autoHandInAndReset()
	{
		return autoHandInAndReset;
	}

	public boolean waitForOneClick()
	{
		return waitForOneClick;
	}

	public boolean sendInvFullNotification()
	{
		return sendInvFullNotification;
	}
}
