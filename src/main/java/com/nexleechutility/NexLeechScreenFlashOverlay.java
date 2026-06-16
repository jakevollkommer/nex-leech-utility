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

class NexLeechScreenFlashOverlay extends Overlay
{
	private final Client client;
	private final NexLeechUtilityPlugin plugin;
	private final NexLeechUtilityConfig config;

	@Inject
	NexLeechScreenFlashOverlay(Client client, NexLeechUtilityPlugin plugin, NexLeechUtilityConfig config)
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
		if (!config.flashOnLowStats())
		{
			return null;
		}

		NexLeechUtilityPlugin.FlashType flash = plugin.getActiveFlash();
		if (flash == null)
		{
			return null;
		}

		Color base;
		String text;
		if (flash == NexLeechUtilityPlugin.FlashType.HP)
		{
			base = config.lowHpFlashColor();
			text = config.lowHpFlashText();
		}
		else
		{
			base = config.lowPrayerFlashColor();
			text = config.lowPrayerFlashText();
		}

		float fraction = plugin.getFlashAlphaFraction(flash);
		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();

		// Screen tint, fading out as the flash expires.
		int alpha = Math.round(base.getAlpha() * fraction);
		graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
		graphics.fillRect(0, 0, width, height);

		// Centred call-to-action text.
		if (text != null && !text.isEmpty())
		{
			graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 48f));
			FontMetrics metrics = graphics.getFontMetrics();
			int textWidth = metrics.stringWidth(text);
			int x = (width - textWidth) / 2;
			int y = height / 2 + metrics.getAscent() / 2;

			graphics.setColor(new Color(0, 0, 0, alpha));
			graphics.drawString(text, x + 2, y + 2);
			graphics.setColor(new Color(255, 255, 255, Math.round(255 * fraction)));
			graphics.drawString(text, x, y);
		}

		return null;
	}
}
