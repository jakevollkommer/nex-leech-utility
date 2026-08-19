package com.nexleechutility;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(NexLeechUtilityConfig.GROUP)
public interface NexLeechUtilityConfig extends Config
{
	String GROUP = "nexleechutility";

	enum FocusMode
	{
		REQUEST,
		FORCE
	}

	enum RayDirections
	{
		CARDINAL,
		ALL_EIGHT
	}

	@ConfigSection(
		name = "Damage",
		description = "Per-kill damage tracking",
		position = 0
	)
	String damageSection = "damage";

	@ConfigSection(
		name = "Minion highlighting",
		description = "Highlight Nex's minions by attackable state",
		position = 1
	)
	String minionSection = "minions";

	@ConfigSection(
		name = "Attack alert",
		description = "Alert once your target minion becomes attackable, and optionally grab focus shortly before",
		position = 2
	)
	String warningSection = "warning";

	@ConfigSection(
		name = "Low stat alerts",
		description = "Flash the screen when HP or prayer drop below a threshold",
		position = 3
	)
	String statsSection = "stats";

	@ConfigSection(
		name = "Hide entities",
		description = "Hide other players and/or thralls inside the Nex room",
		position = 4
	)
	String playersSection = "players";

	@ConfigSection(
		name = "Nex paths",
		description = "Paint the open (walkable) paths outward from Nex",
		position = 5
	)
	String roomSection = "room";

	@ConfigSection(
		name = "Door entry",
		description = "Guard against entering the Nex fight on the wrong world",
		position = 6
	)
	String entrySection = "entry";

	// ===== Damage =====
	@ConfigItem(
		keyName = "showDamageOverlay",
		name = "Show damage overlay",
		description = "Show your own damage this kill. Turns green once you reach the 25 leech threshold.",
		section = damageSection,
		position = 0
	)
	default boolean showDamageOverlay()
	{
		return true;
	}

	@Range(max = 900)
	@ConfigItem(
		keyName = "damageOverlayTimeout",
		name = "Hide after kill (seconds)",
		description = "Hide the damage overlay this many seconds after the kill ends. 0 = keep it visible until the next kill.",
		section = damageSection,
		position = 1
	)
	default int damageOverlayTimeout()
	{
		return 300;
	}

	// ===== Minion highlighting =====
	@ConfigItem(
		keyName = "highlightMinions",
		name = "Highlight minions",
		description = "Outline Fumus/Umbra/Cruor/Glacies. Red while invulnerable, green when attackable.",
		section = minionSection,
		position = 0
	)
	default boolean highlightMinions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightBloodreavers",
		name = "Highlight blood reavers",
		description = "Also highlight blood reavers (they contribute towards your damage).",
		section = minionSection,
		position = 1
	)
	default boolean highlightBloodreavers()
	{
		return false;
	}

	@ConfigItem(
		keyName = "deprioritizeMinionAttack",
		name = "De-prioritize attacking minions",
		description = "Removes left-click Attack on a minion until it becomes attackable (green), so you don't misclick an invulnerable minion.",
		section = minionSection,
		position = 6
	)
	default boolean deprioritizeMinionAttack()
	{
		return true;
	}

	@ConfigItem(
		keyName = "startingMinion",
		name = "Starting minion",
		description = "The minion you begin your leech rotation on. Warnings/focus apply to this minion and any after it until you reach 25 damage.",
		section = minionSection,
		position = 2
	)
	default Minion startingMinion()
	{
		return Minion.FUMUS;
	}

	@Alpha
	@ConfigItem(
		keyName = "attackableColor",
		name = "Attackable color",
		description = "Outline colour for a minion that can be attacked now.",
		section = minionSection,
		position = 3
	)
	default Color attackableColor()
	{
		return new Color(0, 255, 0, 255);
	}

	@Alpha
	@ConfigItem(
		keyName = "notAttackableColor",
		name = "Not-yet-attackable color",
		description = "Outline colour for a minion that cannot be attacked yet (kept faint).",
		section = minionSection,
		position = 4
	)
	default Color notAttackableColor()
	{
		return new Color(255, 0, 0, 80);
	}

	@Alpha
	@ConfigItem(
		keyName = "bloodreaverColor",
		name = "Blood reaver color",
		description = "Outline colour for blood reavers.",
		section = minionSection,
		position = 5
	)
	default Color bloodreaverColor()
	{
		return new Color(255, 140, 0, 200);
	}

	// ===== Attack alert =====
	@ConfigItem(
		keyName = "showVulnerabilityWarning",
		name = "Show attack alert",
		description = "Show a centred \"ATTACK\" alert once your target minion has become attackable.",
		section = warningSection,
		position = 0
	)
	default boolean showVulnerabilityWarning()
	{
		return true;
	}

	@ConfigItem(
		keyName = "requestFocusOnWarning",
		name = "Grab focus on warning",
		description = "Bring the client window to the front before your target minion becomes attackable.",
		section = warningSection,
		position = 3
	)
	default boolean requestFocusOnWarning()
	{
		return false;
	}

	@ConfigItem(
		keyName = "focusLeadSeconds",
		name = "Focus lead (seconds)",
		description = "Grab focus this many seconds before the minion is estimated to become attackable. "
			+ "0 = at the estimated moment; a high value grabs focus as soon as the warning appears. "
			+ "If it becomes attackable sooner, focus is grabbed then regardless.",
		section = warningSection,
		position = 4
	)
	default int focusLeadSeconds()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "focusOnKillEnd",
		name = "Grab focus on kill end",
		description = "Bring the client window to the front when Nex dies and loot drops, so you can grab it.",
		section = warningSection,
		position = 5
	)
	default boolean focusOnKillEnd()
	{
		return false;
	}

	@ConfigItem(
		keyName = "focusMode",
		name = "Focus mode",
		description = "Applies to both focus grabs. REQUEST politely asks for attention (dock bounce); FORCE raises the window.",
		section = warningSection,
		position = 6
	)
	default FocusMode focusMode()
	{
		return FocusMode.FORCE;
	}

	// ===== Low stat alerts =====
	@ConfigItem(
		keyName = "flashOnLowStats",
		name = "Flash on low HP/prayer",
		description = "Flash the screen with a message when HP or prayer drop below the thresholds.",
		section = statsSection,
		position = 0
	)
	default boolean flashOnLowStats()
	{
		return false;
	}

	@ConfigItem(
		keyName = "flashOnlyInFight",
		name = "Only during Nex fight",
		description = "Only flash while in the Nex fight.",
		section = statsSection,
		position = 1
	)
	default boolean flashOnlyInFight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowHpThreshold",
		name = "HP threshold",
		description = "Flash when current HP is at or below this value.",
		section = statsSection,
		position = 2
	)
	default int lowHpThreshold()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "lowPrayerThreshold",
		name = "Prayer threshold",
		description = "Flash when current prayer is at or below this value.",
		section = statsSection,
		position = 3
	)
	default int lowPrayerThreshold()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "lowHpFlashText",
		name = "HP flash text",
		description = "Text shown during a low-HP flash.",
		section = statsSection,
		position = 4
	)
	default String lowHpFlashText()
	{
		return "EAT!";
	}

	@ConfigItem(
		keyName = "lowPrayerFlashText",
		name = "Prayer flash text",
		description = "Text shown during a low-prayer flash.",
		section = statsSection,
		position = 5
	)
	default String lowPrayerFlashText()
	{
		return "DRINK PRAYER POT";
	}

	@Alpha
	@ConfigItem(
		keyName = "lowHpFlashColor",
		name = "HP flash color",
		description = "Screen tint for a low-HP flash.",
		section = statsSection,
		position = 6
	)
	default Color lowHpFlashColor()
	{
		return new Color(255, 0, 0, 90);
	}

	@Alpha
	@ConfigItem(
		keyName = "lowPrayerFlashColor",
		name = "Prayer flash color",
		description = "Screen tint for a low-prayer flash.",
		section = statsSection,
		position = 7
	)
	default Color lowPrayerFlashColor()
	{
		return new Color(0, 200, 255, 90);
	}

	@ConfigItem(
		keyName = "flashDurationSeconds",
		name = "Flash duration (seconds)",
		description = "How long a flash stays on screen. 0 = stay until HP/prayer recovers above the threshold.",
		section = statsSection,
		position = 8
	)
	default int flashDurationSeconds()
	{
		return 0;
	}

	// ===== Hide players =====
	@ConfigItem(
		keyName = "hidePlayers",
		name = "Hide other players",
		description = "Hide other players' models (like Entity Hider). Your own character is kept.",
		section = playersSection,
		position = 0
	)
	default boolean hidePlayers()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideThralls",
		name = "Hide thralls",
		description = "Hide reanimated thralls to cut clutter (hides all thralls in the room).",
		section = playersSection,
		position = 1
	)
	default boolean hideThralls()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hidePlayersOnlyInRoom",
		name = "Only inside Nex room",
		description = "Only hide players/thralls while inside the Nex room.",
		section = playersSection,
		position = 2
	)
	default boolean hidePlayersOnlyInRoom()
	{
		return true;
	}

	// ===== Room layout =====
	@ConfigItem(
		keyName = "showRoomRays",
		name = "Show open paths from Nex",
		description = "Paint the open (walkable) paths outward from Nex until they hit a wall.",
		section = roomSection,
		position = 0
	)
	default boolean showRoomRays()
	{
		return false;
	}

	@ConfigItem(
		keyName = "roomRayDirections",
		name = "Directions",
		description = "Trace only the four cardinal directions (N/S/E/W) or all eight (including diagonals).",
		section = roomSection,
		position = 1
	)
	default RayDirections roomRayDirections()
	{
		return RayDirections.ALL_EIGHT;
	}

	@Range(min = 1, max = 15)
	@ConfigItem(
		keyName = "roomRayLength",
		name = "Ray length (tiles)",
		description = "How many tiles to trace outward from Nex in each direction (stops early at a wall).",
		section = roomSection,
		position = 2
	)
	default int roomRayLength()
	{
		return 8;
	}

	@Alpha
	@ConfigItem(
		keyName = "roomRayColor",
		name = "Ray color",
		description = "Colour for the traced open tiles.",
		section = roomSection,
		position = 3
	)
	default Color roomRayColor()
	{
		return new Color(80, 180, 255, 120);
	}

	// ===== Door entry =====
	@ConfigItem(
		keyName = "blockEntryOffMassWorlds",
		name = "Disable entry off mass worlds",
		description = "Remove left-click entry through the Nex fight barrier unless you are on one of the "
			+ "mass worlds listed below, so you can't accidentally start a fight on a normal world. "
			+ "Right-click still lets you enter deliberately.",
		section = entrySection,
		position = 0
	)
	default boolean blockEntryOffMassWorlds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "massWorlds",
		name = "Mass worlds",
		description = "Comma-separated world numbers where door entry stays enabled (e.g. 332).",
		section = entrySection,
		position = 1
	)
	default String massWorlds()
	{
		return "332";
	}

	@ConfigSection(
		name = "Feedback",
		description = "Suggestions, bug reports, and support",
		position = 99
	)
	String feedbackSection = "feedbackSection";

	@ConfigItem(
		keyName = "suggestButton",
		name = "Suggest a feature",
		description = "Have an idea or found a bug? Click the box to open the GitHub issues page",
		section = feedbackSection,
		position = 0
	)
	default boolean suggestButton()
	{
		return false;
	}

	@ConfigItem(
		keyName = "supportButton",
		name = "Buy me a coffee ❤",
		description = "Enjoying the plugin? Click the box to open the Ko-fi page",
		section = feedbackSection,
		position = 1
	)
	default boolean supportButton()
	{
		return false;
	}
}
