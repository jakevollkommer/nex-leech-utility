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

class NexLeechScreenFlashOverlay extends Overlay
{
	private static final Font FLASH_FONT = FontManager.getRunescapeBoldFont().deriveFont(48f);

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
		NexLeechUtilityPlugin.FlashType flash = plugin.getActiveFlash();
		if (flash == null)
		{
			return null;
		}

		boolean isHpFlash = flash == NexLeechUtilityPlugin.FlashType.HP;
		Color tintColor = isHpFlash ? config.lowHpFlashColor() : config.lowPrayerFlashColor();
		String text = isHpFlash ? config.lowHpFlashText() : config.lowPrayerFlashText();

		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();

		// Screen tint at the configured opacity; stays up while the flash is active.
		graphics.setColor(tintColor);
		graphics.fillRect(0, 0, width, height);

		boolean hasText = text != null && !text.isEmpty();
		if (hasText)
		{
			graphics.setFont(FLASH_FONT);
			FontMetrics metrics = graphics.getFontMetrics();
			int x = (width - metrics.stringWidth(text)) / 2;
			int y = height / 2 + metrics.getAscent() / 2;

			graphics.setColor(Color.BLACK);
			graphics.drawString(text, x + 2, y + 2);
			graphics.setColor(Color.WHITE);
			graphics.drawString(text, x, y);
		}

		return null;
	}
}
