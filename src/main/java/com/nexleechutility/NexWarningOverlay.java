package com.nexleechutility;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
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
	private static final Font TITLE_FONT = FontManager.getRunescapeBoldFont().deriveFont(40f);
	private static final Font SUB_FONT = FontManager.getRunescapeBoldFont().deriveFont(26f);

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
			drawCentered(graphics, "ATTACK " + name + " NOW", width, centerY, TITLE_FONT, Color.GREEN);
		}
		else
		{
			drawCentered(graphics, name + " INCOMING", width, centerY, TITLE_FONT, Color.RED);
			if (config.showAttackCountdown())
			{
				drawCentered(graphics, countdownText(), width, centerY + 34, SUB_FONT, Color.YELLOW);
			}
		}

		return null;
	}

	private String countdownText()
	{
		double seconds = plugin.getSecondsUntilAttackable();
		Minion minion = plugin.getWarningMinion();
		int threshold = minion != null ? minion.getThresholdPercent() : 0;

		if (seconds == 0)
		{
			return "attackable any moment";
		}
		if (seconds < 0)
		{
			// No time estimate yet (HP unreadable, or Nex not losing HP) - show the HP context.
			double nexHp = plugin.getNexHpPercent();
			return nexHp >= 0
				? String.format("attackable at %d%% (Nex %.0f%%)", threshold, nexHp)
				: "attackable soon";
		}
		if (config.countdownUnit() == NexLeechUtilityConfig.CountdownUnit.TICKS)
		{
			return String.format("attackable in %dt", plugin.getTicksUntilAttackable());
		}
		return String.format("attackable in %.1fs", seconds);
	}

	private static void drawCentered(Graphics2D graphics, String text, int width, int y, Font font, Color color)
	{
		graphics.setFont(font);
		FontMetrics metrics = graphics.getFontMetrics();
		int x = (width - metrics.stringWidth(text)) / 2;

		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 2, y + 2);
		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}
}
