package com.nexleechutility;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Outlines the most-likely blood-reaver spawn footprints (2x2, matching the NPC) on the ground
 * during the blood phase, each labelled "Blood Reaver". Tiles are anchored to Nex's live position
 * (re-projected each frame, so they track the camera and her dashes).
 */
class NexReaverPredictorOverlay extends Overlay
{
	private static final String LABEL = "Blood Reaver";
	private static final int REAVER_SIZE = 2;

	private final Client client;
	private final NexLeechUtilityPlugin plugin;
	private final NexLeechUtilityConfig config;

	@Inject
	NexReaverPredictorOverlay(Client client, NexLeechUtilityPlugin plugin, NexLeechUtilityConfig config)
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
		if (!config.predictReaverSpawns() || !plugin.isReaverPredictorActive())
		{
			return null;
		}

		Color color = config.reaverPredictColor();
		Color nearestColor = config.reaverNearestColor();
		WorldPoint nearest = plugin.nearestPredictedTile();

		for (WorldPoint wp : plugin.getPredictedReaverTiles())
		{
			LocalPoint sw = LocalPoint.fromWorld(client, wp);
			if (sw == null)
			{
				continue;
			}
			// Shift the SW tile to the centre of the 2x2 footprint for the area poly.
			LocalPoint center = new LocalPoint(
				sw.getX() + (REAVER_SIZE - 1) * Perspective.LOCAL_HALF_TILE_SIZE,
				sw.getY() + (REAVER_SIZE - 1) * Perspective.LOCAL_HALF_TILE_SIZE);

			boolean isNearest = wp.equals(nearest);
			Color c = isNearest ? nearestColor : color;
			graphics.setStroke(new BasicStroke(isNearest ? 2.5f : 1.5f));

			Polygon poly = Perspective.getCanvasTileAreaPoly(client, center, REAVER_SIZE);
			if (poly != null)
			{
				graphics.setColor(c);
				graphics.draw(poly);
			}

			Point text = Perspective.getCanvasTextLocation(client, graphics, center, LABEL, 0);
			if (text != null)
			{
				OverlayUtil.renderTextLocation(graphics, text, LABEL, c);
			}
		}
		return null;
	}
}
