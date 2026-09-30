package jks.tools2d.parallax.heart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** d13: with compressed atlases on, a page's atlas name leads to the ETC2 copy the editor writes next to it. */
class CompressedAtlasTest
{
	@AfterEach
	void off()
	{Gvars_Parallax.setCompressedAtlases(false);}

	@Test
	void offLoadsTheAtlasTheFileNames()
	{assertEquals("calm.atlas", Gvars_Parallax.atlasFile("calm.atlas"));}

	@Test
	void onLoadsItsEtc2Copy()
	{
		Gvars_Parallax.setCompressedAtlases(true);
		assertEquals("calm.etc2.atlas", Gvars_Parallax.atlasFile("calm.atlas"));
		assertEquals("backgrounds/calm.etc2.atlas", Gvars_Parallax.atlasFile("backgrounds/calm.atlas"));
		// Already the copy, or not an .atlas: left alone.
		assertEquals("calm.etc2.atlas", Gvars_Parallax.atlasFile("calm.etc2.atlas"));
		assertEquals("calm.txt", Gvars_Parallax.atlasFile("calm.txt"));
		assertNull(Gvars_Parallax.atlasFile(null));
	}
}
