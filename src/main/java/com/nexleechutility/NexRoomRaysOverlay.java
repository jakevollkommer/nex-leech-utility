package com.nexleechutility;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Paints the open (walkable) tiles traced outward from Nex in each direction (see
 * {@link NexLeechUtilityPlugin#getOpenDirectionTiles()}), showing the room's open arms relative to
 * her current position. This is a rendering of the room's static collision geometry - it does not
 * predict, time, or mark any boss mechanic.
 */
class NexRoomRaysOverlay extends Overlay
{
	private final Client client;
	private final NexLeechUtilityPlugin plugin;
	private final NexLeechUtilityConfig config;

	@Inject
	NexRoomRaysOverlay(Client client, NexLeechUtilityPlugin plugin, NexLeechUtilityConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showRoomRays())
		{
			return null;
		}

		Color color = config.roomRayColor();
		Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(color.getAlpha(), 50));

		for (WorldPoint tile : plugin.getOpenDirectionTiles())
		{
			LocalPoint local = LocalPoint.fromWorld(client, tile);
			if (local == null)
			{
				continue;
			}
			Polygon poly = Perspective.getCanvasTilePoly(client, local);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(graphics, poly, color, fill, graphics.getStroke());
			}
		}

		return null;
	}
}
