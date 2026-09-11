package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public final class Utils_Parallax
{
	private Utils_Parallax()
	{}

	/** Given one dimension of a region, returns the other one keeping the region aspect ratio. */
	public static float calculateOtherDimension(boolean isWidth, float oneDimen, TextureRegion region)
	{return calculateOtherDimension(isWidth, oneDimen, region.getRegionWidth(), region.getRegionHeight());}

	public static float calculateOtherDimension(boolean isWidth, float oneDimen, float originalWidth, float originalHeight)
	{
		if (isWidth)
			return originalHeight * (oneDimen / originalWidth);
		else
			return originalWidth * (oneDimen / originalHeight);
	}

	/** Unique key of an atlas region: its name followed by its index (-1, "no index", counts as 0). */
	public static String getRegionName(AtlasRegion region)
	{return region.name + Math.max(region.index, 0);}
}
