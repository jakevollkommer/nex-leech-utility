package com.nexleechutility;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Skill;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.npcoverlay.HighlightedNpc;
import net.runelite.client.game.npcoverlay.NpcOverlayService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.client.util.LinkBrowser;

@Slf4j
@PluginDescriptor(
	name = "Nex Leech Utility",
	description = "Leech helper for Nex: damage tracker, minion highlighting, attack alerts, low-stat flashes",
	tags = {"jake", "nex", "leech", "minion", "minions", "contribution", "damage", "tracker", "pvm", "boss", "gwd", "godwars", "ancient", "prison", "zaros", "alert", "flash"}
)
public class NexLeechUtilityPlugin extends Plugin
{
	/** Minimum damage required to qualify for loot at Nex. */
	static final int MINIMUM_LEECH_DAMAGE = 25;
	/** Nex's per-kill unique roll for 100% contribution (1/43). */
	private static final double BASE_UNIQUE_ROLL = 43.0;
	/** Map region id of the Nex arena (Ancient Prison). */
	private static final int NEX_REGION = 11601;

	/** Normalised chat line Nex speaks when she dies, ending the fight. */
	private static final String NEX_DEATH_LINE = "taste my wrath!";

	// The barriers that lead into the Nex fight (outer + inner, with their private/busy variants).
	private static final Set<Integer> NEX_ENTRY_BARRIER_IDS = Set.of(
		ObjectID.NEX_FIGHT_BARRIER_OUTER,
		ObjectID.NEX_FIGHT_BARRIER_OUTER_PRIV,
		ObjectID.NEX_FIGHT_BARRIER_OUTER_BUSY,
		ObjectID.NEX_FIGHT_BARRIER_OUTER_PRIV_BUSY,
		ObjectID.NEX_FIGHT_BARRIER,
		ObjectID.NEX_FIGHT_BARRIER_BUSY,
		ObjectID.NEX_FIGHT_BARRIER_INNER_BUSY
	);

	// Values of the NEX_BARRIER varbit, which tracks the state of the prison barrier / fight.
	private static final int BARRIER_INACTIVE = 0;
	private static final int BARRIER_RESETTING = 1;
	private static final int BARRIER_NEX_DEAD = 2;
	private static final int BARRIER_FIGHT_ACTIVE = 3;

	public enum FlashType
	{
		HP,
		PRAYER
	}

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private OverlayManager overlayManager;
	@Inject private NpcOverlayService npcOverlayService;
	@Inject private ClientUI clientUI;
	@Inject private Hooks hooks;
	@Inject private NexLeechUtilityConfig config;
	@Inject private NexLeechOverlay damageOverlay;
	@Inject private NexWarningOverlay warningOverlay;
	@Inject private NexLeechScreenFlashOverlay screenFlashOverlay;
	@Inject private NexRoomRaysOverlay roomRaysOverlay;

	/** Walkable tiles traced outward from Nex in each direction (the room's open arms). */
	@Getter private final List<WorldPoint> openDirectionTiles = new ArrayList<>();

	// The eight unit step directions; the cardinal subset is the first four.
	private static final int[][] CARDINAL_STEPS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
	private static final int[][] DIAGONAL_STEPS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	@Getter private boolean inFight;
	@Getter private boolean everFought;
	/** Wall-clock time the last fight ended, for the overlay's hide-after-kill timeout. */
	@Getter private long lastFightEndMillis;
	@Getter private int ownDamageThisKill;
	@Getter private int totalDamageThisKill;
	@Getter private int playerCount;
	@Getter private boolean leechComplete;
	private int lastNexStatus;

	/** The minion that can currently be attacked (drawn green); null if none. */
	@Getter private Minion activeMinion;

	/** The attackable leech-target minion the alert overlay is calling out; null if no alert. */
	@Getter private Minion warningMinion;

	/** The upcoming leech-target minion a focus grab is pending for; null if none. */
	private Minion focusMinion;
	/** Whether a focus grab is still pending for {@link #focusMinion}. */
	private boolean focusPending;

	// Nex HP tracking. The minion attackable trigger is HP-gated, so we read Nex's live HP%
	// and her drain rate to estimate seconds-to-attackable adaptively (DPS-independent).
	private static final int RATE_SAMPLES = 10; // ~6s rolling window over game ticks
	private double nexHpPercent = -1; // -1 = unknown / not readable
	private final double[] hpSamples = new double[RATE_SAMPLES];
	private int hpSampleHead;
	private int hpSampleCount;
	private double drainPercentPerSec; // smoothed HP% drained per second; <= 0 means unknown

	// Low-stat flash state. A flash stays up while the stat is below its threshold;
	// if a duration is configured it instead expires after that many ticks.
	@Getter private boolean hpFlashing;
	@Getter private boolean prayerFlashing;
	private int hpFlashTicksLeft;
	private int prayerFlashTicksLeft;

	/** Cached once per tick so the per-frame draw listener doesn't recompute it. */
	private boolean inNexRoom;

	// Hide-config snapshot, refreshed on config change, so the per-entity-per-frame
	// draw listener does plain field reads instead of config-proxy lookups.
	private boolean cfgHidePlayers;
	private boolean cfgHideThralls;
	private boolean cfgHideOnlyInRoom;

	/** Parsed "Mass worlds" config, refreshed on config change. */
	private final Set<Integer> massWorlds = new HashSet<>();

	private final Function<NPC, HighlightedNpc> highlighter = this::highlight;
	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	@Override
	protected void startUp()
	{
		refreshHideConfig();
		overlayManager.add(damageOverlay);
		overlayManager.add(warningOverlay);
		overlayManager.add(screenFlashOverlay);
		overlayManager.add(roomRaysOverlay);
		npcOverlayService.registerHighlighter(highlighter);
		hooks.registerRenderableDrawListener(drawListener);

		clientThread.invokeLater(() ->
		{
			// Reloaded mid-fight (e.g. plugin enabled during a kill) - pick up the active fight.
			if (client.getVarbitValue(VarbitID.NEX_BARRIER) == BARRIER_FIGHT_ACTIVE)
			{
				startFight();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		npcOverlayService.unregisterHighlighter(highlighter);
		hooks.unregisterRenderableDrawListener(drawListener);
		overlayManager.remove(damageOverlay);
		overlayManager.remove(warningOverlay);
		overlayManager.remove(screenFlashOverlay);
		overlayManager.remove(roomRaysOverlay);
		openDirectionTiles.clear();
		inFight = false;
		activeMinion = null;
		warningMinion = null;
		clearPendingFocus();
		hpFlashing = false;
		prayerFlashing = false;
	}

	private void startFight()
	{
		log.debug("Nex fight starting");
		inFight = true;
		everFought = true;
		ownDamageThisKill = 0;
		totalDamageThisKill = 0;
		playerCount = countPlayers();
		leechComplete = false;
		activeMinion = null;
		warningMinion = null;
		clearPendingFocus();
		nexHpPercent = -1;
		resetDrainRate();
		npcOverlayService.rebuild();
	}

	private void endFight()
	{
		log.debug("Nex fight ended (own={}, total={})", ownDamageThisKill, totalDamageThisKill);
		inFight = false;
		lastFightEndMillis = System.currentTimeMillis();
		activeMinion = null;
		warningMinion = null;
		clearPendingFocus();
		nexHpPercent = -1;
		resetDrainRate();
		// Stop any low-stat flash that was scoped to the fight.
		hpFlashing = false;
		prayerFlashing = false;
		// Keep ownDamage/totalDamage/playerCount so the overlay can show the last kill.
		npcOverlayService.rebuild();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() != VarbitID.NEX_BARRIER)
		{
			return;
		}

		// NEX_BARRIER status: 3 = fight active, 2 = Nex dead (loot dropping), 0/1 = inactive/resetting.
		int status = event.getValue();
		boolean fightJustStarted = status == BARRIER_FIGHT_ACTIVE && lastNexStatus != BARRIER_FIGHT_ACTIVE;
		boolean nexJustDied = lastNexStatus == BARRIER_FIGHT_ACTIVE && status == BARRIER_NEX_DEAD;
		boolean fightAborted = (status == BARRIER_INACTIVE || status == BARRIER_RESETTING)
			&& lastNexStatus != BARRIER_NEX_DEAD;

		if (fightJustStarted)
		{
			startFight();
		}
		else if (nexJustDied)
		{
			// Loot drops now - optionally bring the client forward to grab it.
			if (config.focusOnKillEnd())
			{
				grabFocus();
			}
			endFight();
		}
		else if (fightAborted)
		{
			endFight();
		}
		lastNexStatus = status;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		// Nex's callouts arrive as NPC overhead speech (NPC_SAY); a couple as game messages.
		// Filtering by type first skips the bulk of chat (public/clan/private/spam) cheaply.
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.NPC_SAY && type != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = normalize(event.getMessage());
		Minion warningMinionLine = Minion.byWarningLine(message);
		Minion activatedMinionLine = Minion.byActivationLine(message);
		boolean isDeathCallout = message.equals(NEX_DEATH_LINE);

		boolean isRecognizedCallout = warningMinionLine != null || activatedMinionLine != null || isDeathCallout;
		if (!isRecognizedCallout)
		{
			return;
		}

		log.debug("NEX CHAT type={} raw=[{}]", type, event.getMessage());

		if (isDeathCallout)
		{
			endFight();
			return;
		}

		if (!inFight)
		{
			startFight();
		}

		if (warningMinionLine != null)
		{
			onPhaseChange(warningMinionLine);
		}
		else
		{
			onMinionActivated(activatedMinionLine);
		}
	}

	/**
	 * A new phase's pre-callout arrived: the previously attackable minion is no longer the
	 * active target, so clear the green highlight and any standing alert. The on-screen alert
	 * only appears once the game reports the minion has actually become attackable (see
	 * {@link #onMinionActivated}), but a focus grab can be armed here: it fires once the live
	 * estimate of seconds-to-attackable drops within the configured lead time (onGameTick).
	 */
	private void onPhaseChange(Minion minion)
	{
		log.debug("Phase warning callout: {}", minion);
		activeMinion = null;
		warningMinion = null;

		boolean isLeechTarget = !leechComplete && minion.atOrAfter(config.startingMinion());
		if (isLeechTarget && config.requestFocusOnWarning())
		{
			focusMinion = minion;
			focusPending = true;
		}
		else
		{
			clearPendingFocus();
		}
		npcOverlayService.rebuild();
	}

	private void onMinionActivated(Minion minion)
	{
		log.debug("Minion attackable: {}", minion);
		activeMinion = minion;

		// Alert only for the minion we intend to leech - the starting minion or any after it -
		// and only while we still need damage.
		boolean isLeechTarget = !leechComplete && minion.atOrAfter(config.startingMinion());
		warningMinion = (isLeechTarget && config.showVulnerabilityWarning()) ? minion : null;
		// It became attackable before the estimate reached the lead time - grab focus now so it's never missed.
		if (focusPending && focusMinion == minion)
		{
			grabFocus();
		}
		clearPendingFocus();
		npcOverlayService.rebuild();
	}

	/** @return true while the "attack now" alert should be shown for an attackable target minion. */
	public boolean isWarningActive()
	{
		return warningMinion != null;
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!inFight || !(event.getActor() instanceof NPC))
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		if (hitsplat.isMine())
		{
			// Damage to Nex and her minions all counts towards loot eligibility.
			ownDamageThisKill += hitsplat.getAmount();

			boolean justQualifiedForLoot = !leechComplete && ownDamageThisKill >= MINIMUM_LEECH_DAMAGE;
			if (justQualifiedForLoot)
			{
				leechComplete = true;
				warningMinion = null;
				clearPendingFocus();
			}
		}

		if (hitsplat.getHitsplatType() != HitsplatID.HEAL)
		{
			totalDamageThisKill += hitsplat.getAmount();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		int current = event.getBoostedLevel();

		if (skill == Skill.HITPOINTS)
		{
			boolean hpRecovered = current > config.lowHpThreshold();
			boolean shouldStartHpFlash = shouldFlash() && !hpFlashing;
			if (hpRecovered)
			{
				hpFlashing = false;
				hpFlashTicksLeft = 0;
			}
			else if (shouldStartHpFlash)
			{
				hpFlashing = true;
				hpFlashTicksLeft = flashDurationTicks();
			}
		}
		else if (skill == Skill.PRAYER)
		{
			boolean prayerRecovered = current > config.lowPrayerThreshold();
			boolean shouldStartPrayerFlash = shouldFlash() && !prayerFlashing;
			if (prayerRecovered)
			{
				prayerFlashing = false;
				prayerFlashTicksLeft = 0;
			}
			else if (shouldStartPrayerFlash)
			{
				prayerFlashing = true;
				prayerFlashTicksLeft = flashDurationTicks();
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		inNexRoom = isInNexRoom();

		if (inFight)
		{
			playerCount = countPlayers();
			updateNexHp();
		}

		updateRoomRays();

		// Grab focus once the live estimate drops within the configured lead time.
		if (focusPending)
		{
			double secondsUntilAttackable = getSecondsUntilAttackable();
			boolean withinFocusLeadTime = secondsUntilAttackable >= 0
				&& secondsUntilAttackable <= config.focusLeadSeconds();
			if (withinFocusLeadTime)
			{
				grabFocus();
				focusPending = false;
			}
		}

		// A configured duration (> 0) auto-expires the flash; duration 0 means "until recovered".
		if (hpFlashing && hpFlashTicksLeft > 0 && --hpFlashTicksLeft == 0)
		{
			hpFlashing = false;
		}
		if (prayerFlashing && prayerFlashTicksLeft > 0 && --prayerFlashTicksLeft == 0)
		{
			prayerFlashing = false;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (NexLeechUtilityConfig.GROUP.equals(event.getGroup()))
		{
			refreshHideConfig();
			npcOverlayService.rebuild();
		}
	}

	private void grabFocus()
	{
		if (config.focusMode() == NexLeechUtilityConfig.FocusMode.FORCE)
		{
			clientUI.forceFocus();
		}
		else
		{
			clientUI.requestFocus();
		}
	}

	private boolean shouldFlash()
	{
		return config.flashOnLowStats() && (!config.flashOnlyInFight() || inFight);
	}

	private int flashDurationTicks()
	{
		int seconds = config.flashDurationSeconds();
		// 0 => stay until the stat recovers (no auto-expiry).
		return seconds <= 0 ? 0 : (int) Math.ceil(seconds / 0.6);
	}

	private int countPlayers()
	{
		return (int) client.getTopLevelWorldView().players().stream().count();
	}

	/**
	 * Recompute the open-direction tiles: from Nex's current tile, step outward in each configured
	 * direction and collect walkable tiles until a wall is hit. This is purely the room's collision
	 * geometry relative to a visible entity - it does not predict, time, or mark any boss mechanic.
	 */
	private void updateRoomRays()
	{
		openDirectionTiles.clear();
		if (!config.showRoomRays() || !inNexRoom)
		{
			return;
		}

		NPC nex = findNex();
		WorldPoint base = nex == null ? null : nex.getWorldLocation();
		if (base == null)
		{
			return;
		}

		int length = config.roomRayLength();
		boolean diagonals = config.roomRayDirections() == NexLeechUtilityConfig.RayDirections.ALL_EIGHT;
		traceRays(base, length, CARDINAL_STEPS);
		if (diagonals)
		{
			traceRays(base, length, DIAGONAL_STEPS);
		}
	}

	/** Step outward from {@code base} along each given direction, collecting tiles until a wall. */
	private void traceRays(WorldPoint base, int length, int[][] steps)
	{
		for (int[] step : steps)
		{
			for (int dist = 1; dist <= length; dist++)
			{
				WorldPoint tile = base.dx(step[0] * dist).dy(step[1] * dist);
				if (!isWalkable(tile))
				{
					break;
				}
				openDirectionTiles.add(tile);
			}
		}
	}

	private NPC findNex()
	{
		return client.getTopLevelWorldView().npcs().stream()
			.filter(n -> "Nex".equalsIgnoreCase(n.getName()))
			.findFirst().orElse(null);
	}

	private void clearPendingFocus()
	{
		focusMinion = null;
		focusPending = false;
	}

	/**
	 * @return estimated seconds until the pending-focus minion becomes attackable, derived live
	 *         from Nex's current HP and her measured drain rate. 0 = at/past the threshold;
	 *         -1 = unknown (HP unreadable, or Nex not losing HP / healing).
	 */
	private double getSecondsUntilAttackable()
	{
		if (focusMinion == null || nexHpPercent < 0)
		{
			return -1;
		}
		double gap = nexHpPercent - focusMinion.getThresholdPercent();
		if (gap <= 0)
		{
			return 0;
		}
		if (drainPercentPerSec <= 0.01)
		{
			return -1;
		}
		return gap / drainPercentPerSec;
	}

	/** Sample Nex's HP% this tick and recompute the smoothed drain rate (HP% per second). */
	private void updateNexHp()
	{
		NPC nex = findNex();
		int healthScale = nex == null ? 0 : nex.getHealthScale();
		int healthRatio = nex == null ? -1 : nex.getHealthRatio();

		boolean hpBarReadable = healthScale > 0 && healthRatio >= 0;
		if (!hpBarReadable)
		{
			// HP bar not currently readable - keep the last value but stop trusting the rate.
			nexHpPercent = -1;
			resetDrainRate();
			return;
		}

		nexHpPercent = 100.0 * healthRatio / healthScale;

		hpSamples[hpSampleHead % RATE_SAMPLES] = nexHpPercent;
		hpSampleHead++;
		if (hpSampleCount < RATE_SAMPLES)
		{
			hpSampleCount++;
		}

		boolean haveEnoughSamplesForRate = hpSampleCount >= 2;
		if (haveEnoughSamplesForRate)
		{
			double newest = hpSamples[(hpSampleHead - 1) % RATE_SAMPLES];
			double oldest = hpSamples[(hpSampleHead - hpSampleCount) % RATE_SAMPLES];
			double spanSeconds = (hpSampleCount - 1) * 0.6;
			drainPercentPerSec = (oldest - newest) / spanSeconds; // negative if she's healing
		}
	}

	private void resetDrainRate()
	{
		hpSampleHead = 0;
		hpSampleCount = 0;
		drainPercentPerSec = 0;
	}

	private boolean isWalkable(WorldPoint wp)
	{
		LocalPoint localPoint = LocalPoint.fromWorld(client, wp);
		if (localPoint == null)
		{
			return false;
		}
		CollisionData[] collisionMaps = client.getCollisionMaps();
		if (collisionMaps == null || collisionMaps[wp.getPlane()] == null)
		{
			return true; // can't verify - don't over-filter
		}
		int[][] flags = collisionMaps[wp.getPlane()].getFlags();
		int sceneX = localPoint.getSceneX();
		int sceneY = localPoint.getSceneY();
		boolean sceneCoordsInBounds = sceneX >= 0 && sceneY >= 0
			&& sceneX < flags.length && sceneY < flags.length;
		if (!sceneCoordsInBounds)
		{
			return false;
		}
		return (flags[sceneX][sceneY] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
	}

	private HighlightedNpc highlight(NPC npc)
	{
		if (!inFight)
		{
			return null;
		}

		int id = npc.getId();

		boolean isBloodReaver = id == NpcID.NEX_PRISON_BLOOD_REAVER || id == NpcID.NEX_PRISON_BLOOD_REAVER_BOSS;
		if (config.highlightBloodreavers() && isBloodReaver)
		{
			Color color = config.bloodreaverColor();
			return HighlightedNpc.builder()
				.npc(npc)
				.highlightColor(color)
				.fillColor(translucent(color))
				.hull(true)
				.outline(true)
				.build();
		}

		if (config.highlightMinions())
		{
			Minion minion = Minion.byNpcId(id);
			if (minion != null)
			{
				boolean attackable = minion == activeMinion;
				Color color = attackable ? config.attackableColor() : config.notAttackableColor();
				return HighlightedNpc.builder()
					.npc(npc)
					.highlightColor(color)
					.fillColor(translucent(color))
					.hull(true)
					.outline(true)
					.borderWidth(attackable ? 2.5f : 1.5f)
					.outlineFeather(4)
					.build();
			}
		}

		return null;
	}

	// Called for every rendered entity every frame, so this must stay cheap:
	// instanceof short-circuits non-actors, and the room check uses a per-tick cache.
	private boolean shouldDraw(Renderable renderable, boolean drawingUi)
	{
		if (renderable instanceof Player)
		{
			if (!cfgHidePlayers)
			{
				return true;
			}
			Player player = (Player) renderable;
			if (player == client.getLocalPlayer())
			{
				return true;
			}
			return !canHideNow();
		}

		if (renderable instanceof NPC)
		{
			if (!cfgHideThralls || !canHideNow())
			{
				return true;
			}
			int id = ((NPC) renderable).getId();
			return !isThrall(id);
		}

		return true;
	}

	/** Whether player/thrall hiding is currently allowed by the "only in Nex room" gate. */
	private boolean canHideNow()
	{
		return !cfgHideOnlyInRoom || inNexRoom;
	}

	/**
	 * Resurrection thralls are matched by npc id (their display names don't contain "thrall"):
	 * the base Arceuus ghost/skeleton/zombie variants plus the cosmetic reward skins - the
	 * Deadman thralls and the league-reward imp thralls (Jagex's internal "DEBUG_THRALL" names).
	 */
	private static boolean isThrall(int npcId)
	{
		return (npcId >= NpcID.ARCEUUS_THRALL_GHOST_LESSER && npcId <= NpcID.ARCEUUS_THRALL_ZOMBIE_GREATER)
			|| (npcId >= NpcID.DEADMAN_THRALL_ZOMBIE_GREATER_ZUK && npcId <= NpcID.DEADMAN_THRALL_GHOSTLY_GREATER_WISP)
			|| (npcId >= NpcID.DEBUG_THRALL_IMP_MAGIC && npcId <= NpcID.DEBUG_THRALL_IMP_MELEE);
	}

	private void refreshHideConfig()
	{
		cfgHidePlayers = config.hidePlayers();
		cfgHideThralls = config.hideThralls();
		cfgHideOnlyInRoom = config.hidePlayersOnlyInRoom();
		refreshMassWorlds();
	}

	private void refreshMassWorlds()
	{
		massWorlds.clear();
		for (String token : config.massWorlds().split("[,\\s]+"))
		{
			try
			{
				massWorlds.add(Integer.parseInt(token.trim()));
			}
			catch (NumberFormatException ignored)
			{
				// Skip anything that isn't a world number.
			}
		}
	}

	private boolean isInNexRoom()
	{
		int[] regions = client.getMapRegions();
		if (regions == null)
		{
			return false;
		}
		for (int region : regions)
		{
			if (region == NEX_REGION)
			{
				return true;
			}
		}
		return false;
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		maybeDeprioritizeMinionAttack(event);
		maybeDeprioritizeDoorEntry(event);
	}

	private void maybeDeprioritizeMinionAttack(MenuEntryAdded event)
	{
		if (!config.deprioritizeMinionAttack())
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		NPC npc = entry.getNpc();
		if (npc == null)
		{
			return;
		}

		Minion minion = Minion.byNpcId(npc.getId());
		// De-prioritize "Attack" on a minion until it is the active (attackable) one,
		// so you don't left-click an invulnerable minion. When green, left-click Attack returns.
		boolean isAttackOption = "Attack".equalsIgnoreCase(Text.removeTags(entry.getOption()));
		boolean isInvulnerableMinion = minion != null && minion != activeMinion;
		if (isInvulnerableMinion && isAttackOption)
		{
			entry.setDeprioritized(true);
		}
	}

	/**
	 * De-prioritize the fight barrier's left-click entry option unless the current world is one
	 * of the configured mass worlds, so a misclick can't start (or join) a fight on a normal
	 * world. Right-click still enters deliberately.
	 */
	private void maybeDeprioritizeDoorEntry(MenuEntryAdded event)
	{
		if (!config.blockEntryOffMassWorlds() || massWorlds.contains(client.getWorld()))
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		if (isObjectAction(entry.getType()) && NEX_ENTRY_BARRIER_IDS.contains(event.getIdentifier()))
		{
			entry.setDeprioritized(true);
		}
	}

	private static boolean isObjectAction(MenuAction action)
	{
		switch (action)
		{
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
				return true;
			default:
				return false;
		}
	}

	private static Color translucent(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(color.getAlpha(), 50));
	}

	/** @return the flash to draw, or null. HP takes priority over prayer. */
	public FlashType getActiveFlash()
	{
		if (!config.flashOnLowStats())
		{
			return null;
		}
		if (hpFlashing)
		{
			return FlashType.HP;
		}
		if (prayerFlashing)
		{
			return FlashType.PRAYER;
		}
		return null;
	}

	/** Contribution as a percentage of total fight damage, 0 if unknown. */
	public double getContributionPercent()
	{
		if (ownDamageThisKill <= 0 || totalDamageThisKill <= 0)
		{
			return 0;
		}
		return (double) ownDamageThisKill / totalDamageThisKill * 100.0;
	}

	/** Your personal unique-roll denominator (1/N) for this kill, 0 if unknown. */
	public int getUniqueChanceRoll()
	{
		double contribution = getContributionPercent();
		if (contribution <= 0)
		{
			return 0;
		}
		return (int) Math.ceil(BASE_UNIQUE_ROLL * (100.0 / contribution));
	}

	/**
	 * Normalise a Nex chat line: lowercase, strip HTML tags and a leading "nex:"/"nex|" speaker prefix.
	 */
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
	private static final Pattern SPEAKER_PREFIX = Pattern.compile("^nex[:|]\\s*");

	static String normalize(String raw)
	{
		// Precompiled patterns avoid recompiling a regex on every call.
		String s = HTML_TAG.matcher(raw.toLowerCase()).replaceAll("");
		return SPEAKER_PREFIX.matcher(s).replaceFirst("").trim();
	}

	// The config panel cannot host real buttons, so the Feedback "buttons" are checkboxes
	// that act as buttons: any click of the box, tick or untick, opens the link.
	@Subscribe
	public void onFeedbackButtonPressed(ConfigChanged event)
	{
		if (!NexLeechUtilityConfig.GROUP.equals(event.getGroup()) || event.getNewValue() == null)
		{
			return;
		}

		if ("suggestButton".equals(event.getKey()))
		{
			LinkBrowser.browse("https://github.com/jakevollkommer/nex-leech-utility/issues");
			return;
		}

		if ("supportButton".equals(event.getKey()))
		{
			LinkBrowser.browse("https://ko-fi.com/jakevollkommer");
		}
	}

	@Provides
	NexLeechUtilityConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NexLeechUtilityConfig.class);
	}
}
