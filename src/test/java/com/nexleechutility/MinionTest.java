package com.nexleechutility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MinionTest
{
	@Test
	public void normalizeStripsSpeakerPrefixAndTags()
	{
		// Both observed chat formats ("Nex| ..." and "Nex: ...") should reduce to the bare line.
		assertEquals("fill my soul with smoke!", NexLeechUtilityPlugin.normalize("Nex|Fill my soul with smoke!"));
		assertEquals("umbra, don't fail me!", NexLeechUtilityPlugin.normalize("Nex: Umbra, don't fail me!"));
		assertEquals("taste my wrath!", NexLeechUtilityPlugin.normalize("<col=ff0000>Nex: Taste my wrath!</col>"));
	}

	@Test
	public void warningLinesMapToMinions()
	{
		assertEquals(Minion.FUMUS, Minion.byWarningLine("fill my soul with smoke!"));
		assertEquals(Minion.UMBRA, Minion.byWarningLine("darken my shadow!"));
		assertEquals(Minion.CRUOR, Minion.byWarningLine("flood my lungs with blood!"));
		assertEquals(Minion.GLACIES, Minion.byWarningLine("infuse me with the power of ice!"));
		assertNull(Minion.byWarningLine("taste my wrath!"));
	}

	@Test
	public void activationLinesMapToMinions()
	{
		assertEquals(Minion.FUMUS, Minion.byActivationLine("fumus, don't fail me!"));
		assertEquals(Minion.GLACIES, Minion.byActivationLine("glacies, don't fail me!"));
		assertNull(Minion.byActivationLine("darken my shadow!"));
	}

	@Test
	public void rotationOrderIsFumusFirst()
	{
		// atOrAfter encodes the leech rotation: starting on Umbra excludes Fumus but includes Cruor/Glacies.
		assertFalse(Minion.FUMUS.atOrAfter(Minion.UMBRA));
		assertTrue(Minion.UMBRA.atOrAfter(Minion.UMBRA));
		assertTrue(Minion.CRUOR.atOrAfter(Minion.UMBRA));
		assertTrue(Minion.GLACIES.atOrAfter(Minion.UMBRA));
	}

	@Test
	public void npcIdLookupRoundTrips()
	{
		for (Minion m : Minion.values())
		{
			assertEquals(m, Minion.byNpcId(m.getNpcId()));
		}
		assertNull(Minion.byNpcId(-1));
	}
}
