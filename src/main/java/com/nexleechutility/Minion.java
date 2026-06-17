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
	// delayTicks = the EARLIEST number of game ticks from the warning line until the minion can
	// become attackable. OSRS is tick-based (0.6s/tick), so ticks are the source of truth and the
	// seconds countdown is derived (ticks * 0.6). Keyed to the earliest so the countdown reaches 0
	// at the soonest it could be vulnerable - you're ready in time and can never miss. If it's not
	// live yet the overlay shows "any moment"; the "<minion>, don't fail me!" chat line flips it to
	// "ATTACK NOW" at the real moment. Earliest over ~21 logged kills: Fumus 9t (5.4s), Umbra 6t
	// (3.6s), Cruor 8t (4.8s), Glacies 6t (3.6s). (Blood phase / Cruor can run up to ~30t.)
	FUMUS("Fumus", NpcID.NEX_SMOKEMAGE, "fill my soul with smoke!", "fumus, don't fail me!", 9),
	UMBRA("Umbra", NpcID.NEX_SHADOWMAGE, "darken my shadow!", "umbra, don't fail me!", 6),
	CRUOR("Cruor", NpcID.NEX_BLOODMAGE, "flood my lungs with blood!", "cruor, don't fail me!", 8),
	GLACIES("Glacies", NpcID.NEX_ICEMAGE, "infuse me with the power of ice!", "glacies, don't fail me!", 6);

	private final String displayName;
	private final int npcId;
	private final String warningLine;
	private final String activationLine;
	private final int delayTicks;

	Minion(String displayName, int npcId, String warningLine, String activationLine, int delayTicks)
	{
		this.displayName = displayName;
		this.npcId = npcId;
		this.warningLine = warningLine;
		this.activationLine = activationLine;
		this.delayTicks = delayTicks;
	}

	public int getNpcId()
	{
		return npcId;
	}

	public int getDelayTicks()
	{
		return delayTicks;
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
