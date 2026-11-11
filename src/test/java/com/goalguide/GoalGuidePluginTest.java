package com.goalguide;

import com.goalguide.know.GoalGuidePlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Starts RuneLite with Goal Guide loaded (./gradlew run). */
public class GoalGuidePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GoalGuidePlugin.class);
		RuneLite.main(args);
	}
}
