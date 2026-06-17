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
	// delaySeconds = the EARLIEST time from the warning line until the minion can become
	// attackable, so the countdown reaches 0 at the soonest it could be vulnerable - you're
	// ready in time and can never miss the window. If it's not live yet the overlay just shows
	// "any moment"; the "<minion>, don't fail me!" chat line flips it to "ATTACK NOW" at the
	// real moment. Earliest gaps over ~21 logged kills (rounded down for safety): Fumus ~5.3s,
	// Umbra ~3.6s, Cruor ~4.8s, Glacies ~3.6s. (Blood phase / Cruor varies up to ~18s.)
	FUMUS("Fumus", NpcID.NEX_SMOKEMAGE, "fill my soul with smoke!", "fumus, don't fail me!", 5.0),
	UMBRA("Umbra", NpcID.NEX_SHADOWMAGE, "darken my shadow!", "umbra, don't fail me!", 3.5),
	CRUOR("Cruor", NpcID.NEX_BLOODMAGE, "flood my lungs with blood!", "cruor, don't fail me!", 4.5),
	GLACIES("Glacies", NpcID.NEX_ICEMAGE, "infuse me with the power of ice!", "glacies, don't fail me!", 3.5);

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
