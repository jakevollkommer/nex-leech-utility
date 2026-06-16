package com.nexleechutility;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class NexLeechOverlay extends OverlayPanel
{
	private final NexLeechUtilityPlugin plugin;
	private final NexLeechUtilityConfig config;

	@Inject
	NexLeechOverlay(NexLeechUtilityPlugin plugin, NexLeechUtilityConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Keep showing the last kill's stats after the fight ends.
		if (!config.showDamageOverlay() || (!plugin.isInFight() && !plugin.isEverFought()))
		{
			return null;
		}

		int damage = plugin.getOwnDamageThisKill();
		boolean qualified = damage >= NexLeechUtilityPlugin.MINIMUM_LEECH_DAMAGE;
		double contribution = plugin.getContributionPercent();

		// e.g. "25 (0.5%)" or "25 (0.5%) ✓"
		String damageText = String.format("%d (%.1f%%)", damage, contribution);
		if (qualified)
		{
			damageText += " ✓";
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Nex Leech")
			.color(Color.ORANGE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Damage:")
			.right(damageText)
			.rightColor(qualified ? Color.GREEN : Color.RED)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Drop Rate:")
			.right(fraction(plugin.getUniqueChanceRoll()))
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Players:")
			.right(String.valueOf(plugin.getPlayerCount()))
			.build());

		return super.render(graphics);
	}

	private static String fraction(int value)
	{
		return value <= 0 ? "N/A" : "1/" + value;
	}
}
