package com.nexleechutility;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.function.Function;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.npcoverlay.HighlightedNpc;
import net.runelite.client.game.npcoverlay.NpcOverlayService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.OverlayManager;

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
	/** How many game ticks a screen flash stays visible (and fades over). */
	static final int FLASH_TICKS = 5;

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
	@Inject private NexLeechUtilityConfig config;
	@Inject private NexLeechOverlay damageOverlay;
	@Inject private NexWarningOverlay warningOverlay;
	@Inject private NexLeechScreenFlashOverlay screenFlashOverlay;

	@Getter private boolean inFight;
	@Getter private int ownDamageThisKill;
	@Getter private boolean leechComplete;
	private int lastNexStatus;

	/** The minion that can currently be attacked (drawn green); null if none. */
	@Getter private Minion activeMinion;

	@Getter private boolean warningActive;
	@Getter private Minion warningMinion;

	private int hpFlashTicks;
	private int prayerFlashTicks;
	private boolean hpArmed = true;
	private boolean prayerArmed = true;

	private final Function<NPC, HighlightedNpc> highlighter = this::highlight;

	@Override
	protected void startUp()
	{
		overlayManager.add(damageOverlay);
		overlayManager.add(warningOverlay);
		overlayManager.add(screenFlashOverlay);
		npcOverlayService.registerHighlighter(highlighter);

		// Handle the plugin being enabled mid-fight.
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
		overlayManager.remove(damageOverlay);
		overlayManager.remove(warningOverlay);
		overlayManager.remove(screenFlashOverlay);
		inFight = false;
		activeMinion = null;
		warningActive = false;
		warningMinion = null;
	}

	private void startFight()
	{
		inFight = true;
		ownDamageThisKill = 0;
		leechComplete = false;
		activeMinion = null;
		warningActive = false;
		warningMinion = null;
		hpArmed = true;
		prayerArmed = true;
		hpFlashTicks = 0;
		prayerFlashTicks = 0;
		npcOverlayService.rebuild();
	}

	private void endFight()
	{
		inFight = false;
		activeMinion = null;
		warningActive = false;
		warningMinion = null;
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
			// Barrier raised - fight is live (also fires 0 -> 3 when logging in mid-fight).
			startFight();
		}
		else if (lastNexStatus == 3 && status == 2)
		{
			// Nex just died.
			endFight();
		}
		else if ((status == 0 || status == 1) && lastNexStatus != 2)
		{
			// Left the arena while Nex was alive, or logged out.
			endFight();
		}
		lastNexStatus = status;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.NPC_SAY)
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

		if (death)
		{
			endFight();
			return;
		}

		// A recognised combat line means we're in a fight even if the varbit edge was missed.
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
		// A new phase begins: the previous minion is no longer attackable.
		activeMinion = null;
		npcOverlayService.rebuild();

		// Warn only for the minion we intend to leech (the starting minion or any after it)
		// and only while we still need damage.
		if (config.showVulnerabilityWarning()
			&& !leechComplete
			&& minion.atOrAfter(config.startingMinion()))
		{
			warningActive = true;
			warningMinion = minion;

			if (config.requestFocusOnWarning())
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
		}
	}

	private void onMinionActivated(Minion minion)
	{
		activeMinion = minion;
		npcOverlayService.rebuild();

		// The minion we were warned about is now attackable - dismiss the warning.
		if (warningMinion == minion)
		{
			warningActive = false;
			warningMinion = null;
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!inFight || !(event.getActor() instanceof NPC))
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		if (!hitsplat.isMine())
		{
			return;
		}

		// Damage to Nex and her minions all counts towards loot eligibility.
		ownDamageThisKill += hitsplat.getAmount();

		if (!leechComplete && ownDamageThisKill >= MINIMUM_LEECH_DAMAGE)
		{
			leechComplete = true;
			// We've leeched enough - no need to keep warning for the rest of the rotation.
			warningActive = false;
			warningMinion = null;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		int current = event.getBoostedLevel();

		if (skill == Skill.HITPOINTS)
		{
			if (current <= config.lowHpThreshold())
			{
				if (hpArmed && shouldFlash())
				{
					hpFlashTicks = FLASH_TICKS;
					hpArmed = false;
				}
			}
			else
			{
				hpArmed = true;
			}
		}
		else if (skill == Skill.PRAYER)
		{
			if (current <= config.lowPrayerThreshold())
			{
				if (prayerArmed && shouldFlash())
				{
					prayerFlashTicks = FLASH_TICKS;
					prayerArmed = false;
				}
			}
			else
			{
				prayerArmed = true;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (hpFlashTicks > 0)
		{
			hpFlashTicks--;
		}
		if (prayerFlashTicks > 0)
		{
			prayerFlashTicks--;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (NexLeechUtilityConfig.GROUP.equals(event.getGroup()))
		{
			npcOverlayService.rebuild();
		}
	}

	private boolean shouldFlash()
	{
		return config.flashOnLowStats() && (!config.flashOnlyInFight() || inFight);
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

	private static Color translucent(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(color.getAlpha(), 50));
	}

	/** @return the flash that should currently be drawn, or null. HP takes priority over prayer. */
	public FlashType getActiveFlash()
	{
		if (hpFlashTicks > 0)
		{
			return FlashType.HP;
		}
		if (prayerFlashTicks > 0)
		{
			return FlashType.PRAYER;
		}
		return null;
	}

	/** @return 0..1 fade factor for the active flash (1 = just triggered). */
	public float getFlashAlphaFraction(FlashType type)
	{
		int ticks = type == FlashType.HP ? hpFlashTicks : prayerFlashTicks;
		return Math.max(0f, Math.min(1f, ticks / (float) FLASH_TICKS));
	}

	/**
	 * Normalise a Nex chat line: lowercase, strip HTML tags and a leading "nex:"/"nex|" speaker prefix.
	 */
	static String normalize(String raw)
	{
		return raw.toLowerCase()
			.replaceAll("<[^>]+>", "")
			.replaceFirst("^nex[:|]\\s*", "")
			.trim();
	}

	@Provides
	NexLeechUtilityConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NexLeechUtilityConfig.class);
	}
}
