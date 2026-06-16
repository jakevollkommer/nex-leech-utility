package com.nexleechutility;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Prominent centred warning, drawn in the upper-middle of the screen so it sits clear of the
 * Nex health bar (which occupies top-centre). Shows "&lt;MINION&gt; INCOMING" with a countdown,
 * then "ATTACK &lt;MINION&gt; NOW" once it becomes attackable.
 */
class NexWarningOverlay extends Overlay
{
	private final Client client;
	private final NexLeechUtilityPlugin plugin;
	private final NexLeechUtilityConfig config;

	@Inject
	NexWarningOverlay(Client client, NexLeechUtilityPlugin plugin, NexLeechUtilityConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
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

		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();
		// Upper-middle, below the Nex health bar.
		int centerY = (int) (height * 0.22);

		if (attackable)
		{
			drawCentered(graphics, "⚔ ATTACK " + name + " NOW", width, centerY, 40f, Color.GREEN);
		}
		else
		{
			drawCentered(graphics, "⚠ " + name + " INCOMING", width, centerY, 40f, Color.RED);
			if (config.showAttackCountdown())
			{
				String sub = config.countdownUnit() == NexLeechUtilityConfig.CountdownUnit.TICKS
					? String.format("attackable in %dt", plugin.getTicksUntilAttackable())
					: String.format("attackable in %.1fs", plugin.getSecondsUntilAttackable());
				drawCentered(graphics, sub, width, centerY + 34, 26f, Color.YELLOW);
			}
		}

		return null;
	}

	private static void drawCentered(Graphics2D graphics, String text, int width, int y, float size, Color color)
	{
		graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, size));
		FontMetrics metrics = graphics.getFontMetrics();
		int x = (width - metrics.stringWidth(text)) / 2;

		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 2, y + 2);
		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}
}
