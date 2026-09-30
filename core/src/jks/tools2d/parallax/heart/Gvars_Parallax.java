package jks.tools2d.parallax.heart;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;

/**
 * The shared AssetManager, and the default world size: the one set by the last {@link Parallax_Heart} built, used by
 * layers and pages built without a heart. Each heart keeps its own world size, see
 * {@link Parallax_Heart#getWorldWidth()}.
 */
public class Gvars_Parallax
{
	private static AssetManager manager ;
	private static float worldWidth = 40 ;
	private static float worldHeight = 40 ;
	private static boolean compressedAtlases ;
	
	public static AssetManager getManager() 
	{
		if(manager == null)
			buildManager() ; 
		
		return manager ; 
	}


	private static void buildManager()
	{
		manager = new AssetManager() ;
	}


	public static void setManager(AssetManager newManager)
	{manager = newManager ;}

	/**
	 * On: pages load the ETC2 copy of their atlas, {@code name.etc2.atlas}, which the editor writes next to
	 * {@code name.atlas} when a project's ETC2 box is ticked: a quarter of the video memory. A page whose atlas has no
	 * such copy loads the PNG atlas. Turn it on where {@link #supportsEtc2()} says so, and ship both atlases.
	 */
	public static void setCompressedAtlases(boolean on)
	{compressedAtlases = on ;}

	public static boolean isCompressedAtlases()
	{return compressedAtlases ;}

	/**
	 * Whether this device draws ETC2 from video memory as it is: every OpenGL ES 3 GPU, so Android and iOS with GLES 3.
	 * False on desktops, whose drivers accept ETC2 but unpack it to RGBA (no memory saved), and in browsers, where
	 * libGDX cannot read the gzipped pages.
	 */
	public static boolean supportsEtc2()
	{
		ApplicationType type = Gdx.app.getType() ;
		return Gdx.gl30 != null && (type == ApplicationType.Android || type == ApplicationType.iOS) ;
	}

	/** The atlas file a page named {@code atlasName} loads: its ETC2 copy's name when {@link #setCompressedAtlases} is on. */
	public static String atlasFile(String atlasName)
	{
		if (!compressedAtlases || atlasName == null || !atlasName.endsWith(".atlas") || atlasName.endsWith(".etc2.atlas"))
			return atlasName ;
		return atlasName.substring(0, atlasName.length() - ".atlas".length()) + ".etc2.atlas" ;
	}
	
	
	public static float getWidthPercent()
	{return worldWidth/100;}
	
	public static float getHeightPercent()
	{return worldHeight/100;}

	public static float getWorldWidth()
	{return worldWidth;}

	public static void setWorldWidth(float worldWidth)
	{Gvars_Parallax.worldWidth = worldWidth;}

	public static float getWorldHeight()
	{return worldHeight;}

	public static void setWorldHeight(float worldHeight)
	{Gvars_Parallax.worldHeight = worldHeight;}
}