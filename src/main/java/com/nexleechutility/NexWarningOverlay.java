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

		// Single compact line: "UMBRA  4%" / "UMBRA  ~2s" (incoming) or "ATTACK UMBRA" (live).
		if (attackable)
		{
			drawCentered(graphics, "ATTACK " + name, width, centerY, TITLE_FONT, Color.GREEN);
		}
		else
		{
			String prox = proximity();
			String label = prox.isEmpty() ? name : name + "  " + prox;
			drawCentered(graphics, label, width, centerY, TITLE_FONT, Color.RED);
		}

		return null;
	}

	// The seconds estimate is only trustworthy in the final burst; above this it's the bursty
	// held-flat HP bar inflating the number, so we fall back to HP proximity.
	private static final double RELIABLE_SECONDS = 10.0;

	/** Short proximity token: "" / "now" / "4%" (HP to go) / "~2s" (reliable final approach). */
	private String proximity()
	{
		if (!config.showAttackCountdown())
		{
			return "";
		}
		double nexHp = plugin.getNexHpPercent();
		Minion minion = plugin.getWarningMinion();
		if (nexHp < 0 || minion == null)
		{
			return "";
		}
		double gap = nexHp - minion.getThresholdPercent();
		if (gap <= 0)
		{
			return "now";
		}
		double seconds = plugin.getSecondsUntilAttackable();
		if (seconds > 0 && seconds <= RELIABLE_SECONDS)
		{
			return config.countdownUnit() == NexLeechUtilityConfig.CountdownUnit.TICKS
				? String.format("~%dt", plugin.getTicksUntilAttackable())
				: String.format("~%.0fs", seconds);
		}
		return String.format("%.0f%%", gap);
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
