package com.nexleechutility;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Skill;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.NpcID;
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

@Slf4j
@PluginDescriptor(
	name = "Nex Leech Utility",
	description = "Leech helper for Nex: damage tracker, minion highlighting, vulnerability warnings, low-stat flashes",
	tags = {"nex", "leech", "minion", "contribution", "pvm"}
)
public class NexLeechUtilityPlugin extends Plugin
{
	/** Minimum damage required to qualify for loot at Nex. */
	static final int MINIMUM_LEECH_DAMAGE = 25;
	/** Nex's per-kill unique roll for 100% contribution (1/43). */
	private static final double BASE_UNIQUE_ROLL = 43.0;
	/** Map region id of the Nex arena (Ancient Prison). */
	private static final int NEX_REGION = 11601;

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

	@Getter private boolean inFight;
	@Getter private boolean everFought;
	@Getter private int ownDamageThisKill;
	@Getter private int totalDamageThisKill;
	@Getter private int playerCount;
	@Getter private boolean leechComplete;
	private int lastNexStatus;

	/** The minion that can currently be attacked (drawn green); null if none. */
	@Getter private Minion activeMinion;

	/** The minion the warning overlay is currently alerting about; null if no warning. */
	@Getter private Minion warningMinion;
	/** Whether a focus grab is still pending for the current warning. */
	private boolean focusPending;

	// Nex HP tracking. The minion attackable trigger is HP-gated, so we read Nex's live HP%
	// and her drain rate to estimate seconds-to-attackable adaptively (DPS-independent).
	private static final int RATE_SAMPLES = 10; // ~6s rolling window over game ticks
	@Getter private double nexHpPercent = -1; // -1 = unknown / not readable
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

	private final Function<NPC, HighlightedNpc> highlighter = this::highlight;
	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	@Override
	protected void startUp()
	{
		refreshHideConfig();
		overlayManager.add(damageOverlay);
		overlayManager.add(warningOverlay);
		overlayManager.add(screenFlashOverlay);
		npcOverlayService.registerHighlighter(highlighter);
		hooks.registerRenderableDrawListener(drawListener);

		clientThread.invokeLater(() ->
		{
			if (client.getVarbitValue(VarbitID.NEX_BARRIER) == 3)
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
		inFight = false;
		activeMinion = null;
		warningMinion = null;
		focusPending = false;
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
		focusPending = false;
		nexHpPercent = -1;
		resetDrainRate();
		npcOverlayService.rebuild();
	}

	private void endFight()
	{
		log.debug("Nex fight ended (own={}, total={})", ownDamageThisKill, totalDamageThisKill);
		inFight = false;
		activeMinion = null;
		warningMinion = null;
		focusPending = false;
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

		int status = event.getValue();
		if (status == 3 && lastNexStatus != 3)
		{
			startFight();
		}
		else if (lastNexStatus == 3 && status == 2)
		{
			// Nex died and loot drops - optionally bring the client forward to grab it.
			if (config.focusOnKillEnd())
			{
				grabFocus();
			}
			endFight();
		}
		else if ((status == 0 || status == 1) && lastNexStatus != 2)
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
		Minion warning = Minion.byWarningLine(message);
		Minion activation = Minion.byActivationLine(message);
		boolean death = message.equals("taste my wrath!");

		if (warning == null && activation == null && !death)
		{
			return;
		}

		log.debug("NEX CHAT type={} raw=[{}]", type, event.getMessage());

		if (death)
		{
			endFight();
			return;
		}

		if (!inFight)
		{
			startFight();
		}

		if (warning != null)
		{
			onPhaseWarning(warning);
		}
		else
		{
			onMinionActivated(activation);
		}
	}

	private void onPhaseWarning(Minion minion)
	{
		log.debug("Phase warning: {} (target start={})", minion, config.startingMinion());
		// A new phase begins: the previous minion is no longer attackable.
		activeMinion = null;

		// Alert for the minion we intend to leech (the starting minion or any after it),
		// but only while we still need damage. Otherwise clear any stale warning.
		if (config.showVulnerabilityWarning()
			&& !leechComplete
			&& minion.atOrAfter(config.startingMinion()))
		{
			warningMinion = minion;
			// Focus is grabbed live once the DPS-based estimate drops within the lead time (onGameTick).
			focusPending = config.requestFocusOnWarning();
		}
		else
		{
			warningMinion = null;
			focusPending = false;
		}

		npcOverlayService.rebuild();
	}

	private void onMinionActivated(Minion minion)
	{
		log.debug("Minion attackable: {}", minion);
		// Keep the warning up (it switches to an "attack now" message) until the phase
		// passes, we leech enough, or the fight ends - so it stays visible.
		activeMinion = minion;
		// It became attackable before the estimate reached the lead time - grab focus now so it's never missed.
		if (warningMinion == minion && focusPending)
		{
			grabFocus();
			focusPending = false;
		}
		npcOverlayService.rebuild();
	}

	/** @return true while the warning overlay should be shown. */
	public boolean isWarningActive()
	{
		return warningMinion != null;
	}

	/** @return true once the warned-about minion is attackable (overlay shows "attack now"). */
	public boolean isWarningMinionAttackable()
	{
		return warningMinion != null && warningMinion == activeMinion;
	}

	/**
	 * @return estimated seconds until the warned-about minion becomes attackable, derived live from
	 *         Nex's current HP and her measured drain rate. 0 = at/past the threshold;
	 *         -1 = unknown (HP unreadable, or Nex not losing HP / healing).
	 */
	public double getSecondsUntilAttackable()
	{
		if (warningMinion == null || nexHpPercent < 0)
		{
			return -1;
		}
		double gap = nexHpPercent - warningMinion.getThresholdPercent();
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

	/** @return estimated game ticks until attackable (>= 0), or -1 if unknown. */
	public int getTicksUntilAttackable()
	{
		double seconds = getSecondsUntilAttackable();
		return seconds < 0 ? -1 : (int) Math.round(seconds / 0.6);
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

			if (!leechComplete && ownDamageThisKill >= MINIMUM_LEECH_DAMAGE)
			{
				leechComplete = true;
				warningMinion = null;
				focusPending = false;
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
			if (current > config.lowHpThreshold())
			{
				hpFlashing = false;
				hpFlashTicksLeft = 0;
			}
			else if (shouldFlash() && !hpFlashing)
			{
				hpFlashing = true;
				hpFlashTicksLeft = flashDurationTicks();
			}
		}
		else if (skill == Skill.PRAYER)
		{
			if (current > config.lowPrayerThreshold())
			{
				prayerFlashing = false;
				prayerFlashTicksLeft = 0;
			}
			else if (shouldFlash() && !prayerFlashing)
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

		// Grab focus once the live estimate drops within the configured lead time.
		if (focusPending)
		{
			double estimate = getSecondsUntilAttackable();
			if (estimate >= 0 && estimate <= config.focusLeadSeconds())
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

	/** Sample Nex's HP% this tick and recompute the smoothed drain rate (HP% per second). */
	private void updateNexHp()
	{
		NPC nex = client.getTopLevelWorldView().npcs().stream()
			.filter(n -> "Nex".equalsIgnoreCase(n.getName()))
			.findFirst().orElse(null);

		int scale = nex == null ? 0 : nex.getHealthScale();
		int ratio = nex == null ? -1 : nex.getHealthRatio();
		if (scale <= 0 || ratio < 0)
		{
			// HP bar not currently readable - keep the last value but stop trusting the rate.
			nexHpPercent = -1;
			resetDrainRate();
			return;
		}

		nexHpPercent = 100.0 * ratio / scale;

		hpSamples[hpSampleHead % RATE_SAMPLES] = nexHpPercent;
		hpSampleHead++;
		if (hpSampleCount < RATE_SAMPLES)
		{
			hpSampleCount++;
		}

		if (hpSampleCount >= 2)
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

	private HighlightedNpc highlight(NPC npc)
	{
		if (!inFight)
		{
			return null;
		}

		int id = npc.getId();

		if (config.highlightBloodreavers()
			&& (id == NpcID.NEX_PRISON_BLOOD_REAVER || id == NpcID.NEX_PRISON_BLOOD_REAVER_BOSS))
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
			// Arceuus resurrection thralls (ghost/skeleton/zombie, lesser/superior/greater)
			// are a contiguous id range; their display names don't contain "thrall".
			return !(id >= NpcID.ARCEUUS_THRALL_GHOST_LESSER && id <= NpcID.ARCEUUS_THRALL_ZOMBIE_GREATER);
		}

		return true;
	}

	/** Whether player/thrall hiding is currently allowed by the "only in Nex room" gate. */
	private boolean canHideNow()
	{
		return !cfgHideOnlyInRoom || inNexRoom;
	}

	private void refreshHideConfig()
	{
		cfgHidePlayers = config.hidePlayers();
		cfgHideThralls = config.hideThralls();
		cfgHideOnlyInRoom = config.hidePlayersOnlyInRoom();
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
		if (minion != null
			&& minion != activeMinion
			&& "Attack".equalsIgnoreCase(Text.removeTags(entry.getOption())))
		{
			entry.setDeprioritized(true);
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

	@Provides
	NexLeechUtilityConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NexLeechUtilityConfig.class);
	}
}
