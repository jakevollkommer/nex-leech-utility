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

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("⚠ " + minion.getDisplayName().toUpperCase() + " INCOMING")
			.color(Color.RED)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Attack when it glows green")
			.leftColor(Color.YELLOW)
			.build());

		return super.render(graphics);
	}
}
