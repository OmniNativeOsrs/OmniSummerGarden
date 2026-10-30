/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.summergarden;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import omni.osrs.model.OmniWorldPoint;
import omni.osrs.plugin.OmniOverlayBuilder;
import omni.osrs.plugin.OmniOverlayStyle;

/**
 * Info panel and tile highlights for Summer Garden. The plugin pushes the rows and the highlights each tick.
 */
public final class SummerGardenOverlay
{
	/** One highlighted tile. */
	static final class Mark
	{
		private final OmniWorldPoint tile;
		private final OmniOverlayStyle style;

		Mark(OmniWorldPoint tile, OmniOverlayStyle style)
		{
			this.tile = tile;
			this.style = style;
		}
	}

	private static final Map<String, String> ROWS = new LinkedHashMap<>();
	private static volatile List<Mark> marks = List.of();

	private SummerGardenOverlay()
	{
	}

	/**
	 * Writes the panel's rows and the highlights.
	 *
	 * @param overlay the client's overlay builder
	 */
	public static void render(OmniOverlayBuilder overlay)
	{
		List<Map.Entry<String, String>> rows;
		synchronized (ROWS)
		{
			rows = new ArrayList<>(ROWS.entrySet());
		}
		for (Map.Entry<String, String> row : rows)
		{
			overlay.line(row.getKey(), row.getValue());
		}
		for (Mark mark : marks)
		{
			overlay.highlightTile(mark.tile, mark.style);
		}
	}

	/**
	 * Replaces the rows and the highlights.
	 *
	 * @param newRows the rows in order
	 * @param newMarks the highlighted tiles
	 */
	static void update(Map<String, String> newRows, List<Mark> newMarks)
	{
		synchronized (ROWS)
		{
			ROWS.clear();
			ROWS.putAll(newRows);
		}
		marks = newMarks;
	}

	/** Removes every row and highlight. */
	public static void clear()
	{
		synchronized (ROWS)
		{
			ROWS.clear();
		}
		marks = List.of();
	}
}
