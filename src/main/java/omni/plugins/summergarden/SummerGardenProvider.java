/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.summergarden;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import omni.osrs.plugin.OmniPlugin;
import omni.osrs.plugin.OmniPluginProvider;
import omni.osrs.plugin.OmniPluginRuntime;
import omni.osrs.plugin.OmniPluginSetting;
import omni.osrs.runtime.OmniGame;

/**
 * Entry point named by {@code META-INF/omni-plugin.properties} for {@link SummerGardenPlugin}.
 */
public final class SummerGardenProvider implements OmniPluginProvider
{
	private static final String ID = "omnisummergarden";
	private static final String VERSION = descriptorVersion();

	@Override
	public String id()
	{
		return ID;
	}

	@Override
	public String name()
	{
		return "Omni Summer Garden";
	}

	@Override
	public String description()
	{
		return "Runs the Summer Garden maze with collision timing, makes sq'irk juice and hands it in to Osman.";
	}

	@Override
	public String category()
	{
		return "Minigame";
	}

	@Override
	public String version()
	{
		// Must equal version= in the descriptor, which a release build may rewrite: read it back.
		return VERSION;
	}

	@Override
	public List<OmniPluginSetting> settings()
	{
		return SummerGardenConfig.settings();
	}

	@Override
	public String incompatibility(OmniGame game)
	{
		return game.unsupportedFeatures();
	}

	@Override
	public OmniPlugin create(OmniPluginRuntime runtime)
	{
		return new SummerGardenPlugin(runtime, SummerGardenConfig.from(runtime.settings()));
	}

	private static String descriptorVersion()
	{
		try
		{
			Enumeration<URL> descriptors = SummerGardenProvider.class.getClassLoader()
				.getResources("META-INF/omni-plugin.properties");
			while (descriptors.hasMoreElements())
			{
				Properties properties = new Properties();
				try (InputStream in = descriptors.nextElement().openStream())
				{
					properties.load(in);
				}
				if (ID.equals(properties.getProperty("id")) && properties.getProperty("version") != null)
				{
					return properties.getProperty("version").trim();
				}
			}
		}
		catch (IOException ignored)
		{
			// Falls through to the source's own version.
		}
		return "1.0.0";
	}
}
