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
	// delaySeconds = estimated time from the warning line until the minion becomes attackable.
	// Tuned from ~21 logged kills (mean warning->activation gap): Fumus ~5.6s, Umbra ~5.8s,
	// Cruor ~12.8s (blood phase runs much longer), Glacies ~5.8s with a tail to ~7.8s.
	// Slightly over-estimating is safe: the "<minion>, don't fail me!" chat line truncates
	// the countdown the instant it's actually vulnerable.
	FUMUS("Fumus", NpcID.NEX_SMOKEMAGE, "fill my soul with smoke!", "fumus, don't fail me!", 6.0),
	UMBRA("Umbra", NpcID.NEX_SHADOWMAGE, "darken my shadow!", "umbra, don't fail me!", 6.0),
	CRUOR("Cruor", NpcID.NEX_BLOODMAGE, "flood my lungs with blood!", "cruor, don't fail me!", 13.0),
	GLACIES("Glacies", NpcID.NEX_ICEMAGE, "infuse me with the power of ice!", "glacies, don't fail me!", 7.0);

	private final String displayName;
	private final int npcId;
	private final String warningLine;
	private final String activationLine;
	private final double delaySeconds;

	Minion(String displayName, int npcId, String warningLine, String activationLine, double delaySeconds)
	{
		this.displayName = displayName;
		this.npcId = npcId;
		this.warningLine = warningLine;
		this.activationLine = activationLine;
		this.delaySeconds = delaySeconds;
	}

	public int getNpcId()
	{
		return npcId;
	}

	public double getDelaySeconds()
	{
		return delaySeconds;
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
