package jks.tools2d.parallax.pages;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.GdxNativesLoader;

import jks.tools2d.parallax.heart.Gvars_Parallax;

/**
 * r173: the ETC2 copy of an atlas carries its mip chain in its .zktx pages, so loading it must not call
 * glGenerateMipmap, which OpenGL ES 3 refuses on a compressed texture. Gdx.gl is a proxy that counts the calls; the
 * atlas is the editor's own ETC2 export of City (r125), mipmapped, 11 levels in its one page.
 */
class Etc2AtlasMipmapTest
{
	private static final File ETC2 = new File("test-data/etc2");
	private static final int LEVELS = 11;

	private int generateMipmap, compressedLevels;

	@BeforeAll
	static void natives()
	{GdxNativesLoader.load();}

	@BeforeEach
	void countingGl()
	{
		Gdx.app = (Application) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Application.class }, (proxy, method, args) -> defaultValue(method.getReturnType()));
		Gdx.graphics = (Graphics) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Graphics.class }, (proxy, method, args) -> defaultValue(method.getReturnType()));
		// Every path the test hands over is absolute.
		Gdx.files = (Files) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Files.class }, (proxy, method, args) ->
				method.getReturnType() == FileHandle.class ? new FileHandle((String) args[0]) : defaultValue(method.getReturnType()));
		Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { GL20.class }, (proxy, method, args) ->
		{
			if (method.getName().equals("glGenerateMipmap"))
				generateMipmap++;
			if (method.getName().equals("glCompressedTexImage2D"))
				compressedLevels++;
			if (method.getName().equals("glGetIntegerv"))
				((IntBuffer) args[1]).put(0, 4);
			if (method.getReturnType() == int.class)
				return 1;
			return defaultValue(method.getReturnType());
		});
		Gvars_Parallax.setCompressedAtlases(true);
	}

	@AfterEach
	void restore()
	{
		Gdx.app = null;
		Gdx.graphics = null;
		Gdx.files = null;
		Gdx.gl = Gdx.gl20 = null;
		Gvars_Parallax.setCompressedAtlases(false);
		Gvars_Parallax.setManager(null);
	}

	private static Object defaultValue(Class<?> type)
	{
		if (type == boolean.class)
			return false;
		if (type == float.class)
			return 0f;
		if (type.isPrimitive() && type != void.class)
			return 0;
		return null;
	}

	/** The call this test is about, without the fix: libGDX's own loading asks for the mipmaps. */
	@Test
	void libgdxAloneGeneratesMipmapsOverTheFilesOwn()
	{
		TextureAtlas atlas = new TextureAtlas(new FileHandle(new File(ETC2, "city.etc2.atlas")));
		assertEquals(1, generateMipmap);
		assertEquals(LEVELS, compressedLevels);
		atlas.dispose();
	}

	@Test
	void aPageFromAFolderUploadsTheFilesLevelsAndGeneratesNone()
	{
		WholePage_Model page = new WholePage_Model("city.atlas");
		page.preload(ETC2.getAbsolutePath(), 40, 40);

		assertEquals(0, generateMipmap);
		assertEquals(LEVELS, compressedLevels);
		assertFiltersKept(page.getLoadedAtlas());
		page.disposeOwnedAtlas();
	}

	@Test
	void aPageThroughTheAssetManagerUploadsTheFilesLevelsAndGeneratesNone()
	{
		AssetManager manager = new AssetManager(new AbsoluteFileHandleResolver());
		Gvars_Parallax.setManager(manager);
		WholePage_Model page = new WholePage_Model(new File(ETC2, "city.atlas").getAbsolutePath());
		page.preload(40, 40);

		assertEquals(0, generateMipmap);
		assertEquals(LEVELS, compressedLevels);
		assertFiltersKept(page.getLoadedAtlas());
		manager.dispose();
	}

	private static void assertFiltersKept(TextureAtlas atlas)
	{
		Texture texture = atlas.getTextures().first();
		assertEquals(Texture.TextureFilter.MipMapLinearLinear, texture.getMinFilter());
		assertEquals(Texture.TextureFilter.Linear, texture.getMagFilter());
	}
}
