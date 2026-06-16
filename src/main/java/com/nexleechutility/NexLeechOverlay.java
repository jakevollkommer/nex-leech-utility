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
		if (!config.showDamageOverlay() || !plugin.isInFight())
		{
			return null;
		}

		int damage = plugin.getOwnDamageThisKill();
		boolean qualified = damage >= NexLeechUtilityPlugin.MINIMUM_LEECH_DAMAGE;

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Nex Leech")
			.color(Color.ORANGE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Damage:")
			.right(qualified ? damage + " ✓" : String.valueOf(damage))
			.rightColor(qualified ? Color.GREEN : Color.RED)
			.build());

		return super.render(graphics);
	}
}
