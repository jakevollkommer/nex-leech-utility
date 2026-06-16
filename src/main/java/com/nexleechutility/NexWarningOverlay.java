package com.nexleechutility;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class NexWarningOverlay extends OverlayPanel
{
	private final NexLeechUtilityPlugin plugin;
	private final NexLeechUtilityConfig config;

	@Inject
	NexWarningOverlay(NexLeechUtilityPlugin plugin, NexLeechUtilityConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showVulnerabilityWarning() || !plugin.isWarningActive())
		{
			return null;
		}

		Minion minion = plugin.getWarningMinion();
		if (minion == null)
		{
			return null;
		}

		String name = minion.getDisplayName().toUpperCase();
		boolean attackable = plugin.isWarningMinionAttackable();

		if (attackable)
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("⚔ ATTACK " + name + " NOW")
				.color(Color.GREEN)
				.build());
		}
		else
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("⚠ " + name + " INCOMING")
				.color(Color.RED)
				.build());

			if (config.showAttackCountdown())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Attackable in:")
					.right(String.format("%.1fs", plugin.getSecondsUntilAttackable()))
					.rightColor(Color.YELLOW)
					.build());
			}
			else
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Get ready - about to be vulnerable")
					.leftColor(Color.YELLOW)
					.build());
			}
		}

		return super.render(graphics);
	}
}
