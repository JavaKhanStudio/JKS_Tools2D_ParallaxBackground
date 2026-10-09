package jks.tools2d.parallax.pages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;

/**
 * r212: WebGL 1 and OpenGL ES 2 cannot mipmap a non-power-of-two page, and draw it black. Utils_Etc2_Atlas.fit drops
 * those mipmaps there, and only those. The browser draws it in WebGlCases.npotMipMapAtlasDraws*.
 */
class NpotMipMapAtlasTest
{
	private static Page firstPage(String path, boolean etc2, boolean npotRefused)
	{
		FileHandle atlas = new FileHandle(new File(path));
		TextureAtlasData data = new TextureAtlasData(atlas, atlas.parent(), false);
		Utils_Etc2_Atlas.fit(data, etc2, npotRefused);
		return data.getPages().first();
	}

	@Test
	void calmsNpotPageLoadsLinearWhereMipmapsAreRefused()
	{
		Page page = firstPage("test-data/samples/transfer/calm.atlas", false, true);
		assertFalse(page.useMipMaps);
		assertEquals(TextureFilter.Linear, page.minFilter);
		assertEquals(TextureFilter.Linear, page.magFilter);
	}

	@Test
	void calmKeepsItsMipmapsWhereTheGlMakesThem()
	{
		Page page = firstPage("test-data/samples/transfer/calm.atlas", false, false);
		assertTrue(page.useMipMaps);
		assertEquals(TextureFilter.MipMap, page.minFilter);
	}

	@Test
	void aPowerOfTwoPageKeepsItsMipmapsEverywhere()
	{
		Page page = firstPage("test-data/etc2/city.atlas", false, true);
		assertTrue(page.useMipMaps);
		assertEquals(TextureFilter.MipMapLinearLinear, page.minFilter);
	}
}
