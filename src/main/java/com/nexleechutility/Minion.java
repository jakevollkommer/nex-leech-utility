package com.nexleechutility;

import net.runelite.api.gameval.NpcID;

/**
 * Nex's four minions, in the fixed order they become attackable during a kill.
 * Each minion is preceded by a "warning" line (Nex invokes the element) and then
 * an "activation" line ("&lt;name&gt;, don't fail me!") at which point it can be attacked.
 * Chat lines are stored lowercased/normalised to match {@link NexLeechUtilityPlugin#normalize}.
 */
public enum Minion
{
	// thresholdPercent = Nex's HP% at which this minion becomes attackable. The trigger is HP-gated
	// (DPS-independent), confirmed in-game and on the wiki: Nex has 3400 HP in 20% phase bands, and
	// each minion goes live as she crosses its threshold. The seconds countdown is NOT a fixed
	// value - it's derived live from Nex's current HP and her measured drain rate, so it adapts to
	// team DPS. The "<minion>, don't fail me!" chat line is the frame-exact confirmation.
	FUMUS("Fumus", NpcID.NEX_SMOKEMAGE, "fill my soul with smoke!", "fumus, don't fail me!", 80),
	UMBRA("Umbra", NpcID.NEX_SHADOWMAGE, "darken my shadow!", "umbra, don't fail me!", 60),
	CRUOR("Cruor", NpcID.NEX_BLOODMAGE, "flood my lungs with blood!", "cruor, don't fail me!", 40),
	GLACIES("Glacies", NpcID.NEX_ICEMAGE, "infuse me with the power of ice!", "glacies, don't fail me!", 20);

	private final String displayName;
	private final int npcId;
	private final String warningLine;
	private final String activationLine;
	private final int thresholdPercent;

	Minion(String displayName, int npcId, String warningLine, String activationLine, int thresholdPercent)
	{
		this.displayName = displayName;
		this.npcId = npcId;
		this.warningLine = warningLine;
		this.activationLine = activationLine;
		this.thresholdPercent = thresholdPercent;
	}

	public int getNpcId()
	{
		return npcId;
	}

	public int getThresholdPercent()
	{
		return thresholdPercent;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}

	/** True if this minion is at or after {@code start} in the fixed rotation. */
	public boolean atOrAfter(Minion start)
	{
		return ordinal() >= start.ordinal();
	}

	public static Minion byNpcId(int id)
	{
		for (Minion m : values())
		{
			if (m.npcId == id)
			{
				return m;
			}
		}
		return null;
	}

	public static Minion byWarningLine(String normalizedMessage)
	{
		for (Minion m : values())
		{
			if (m.warningLine.equals(normalizedMessage))
			{
				return m;
			}
		}
		return null;
	}

	public static Minion byActivationLine(String normalizedMessage)
	{
		for (Minion m : values())
		{
			if (m.activationLine.equals(normalizedMessage))
			{
				return m;
			}
		}
		return null;
	}
}
