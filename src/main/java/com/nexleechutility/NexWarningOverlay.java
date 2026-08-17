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
 * Centred "ATTACK &lt;MINION&gt;" alert, shown only once the game reports the target minion has
 * actually become attackable. Drawn in the upper-middle of the screen so it sits clear of the Nex
 * health bar (which occupies top-centre). It reflects current game state - there is no countdown
 * or pre-announcement of the mechanic.
 */
class NexWarningOverlay extends Overlay
{
	private static final Font TITLE_FONT = FontManager.getRunescapeBoldFont().deriveFont(40f);

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
		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();
		// Upper-middle, below the Nex health bar.
		int centerY = (int) (height * 0.22);

		drawCentered(graphics, "ATTACK " + name, width, centerY, TITLE_FONT, Color.GREEN);
		return null;
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
