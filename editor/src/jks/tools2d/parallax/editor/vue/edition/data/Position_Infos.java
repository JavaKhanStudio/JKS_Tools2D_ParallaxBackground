package jks.tools2d.parallax.editor.vue.edition.data;

import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;

/** Where an image of the project comes from: an atlas region (name + position) or a loose file (path). */
public class Position_Infos
{
	public boolean fromAtlas;
	/** Region name when {@link #fromAtlas}, image path otherwise. */
	public String url;
	/** Position among the atlas regions sharing the same name. */
	public int position;

	// JSON constructor
	public Position_Infos()
	{}

	public Position_Infos(boolean fromAtlas, String url, int position)
	{
		this.fromAtlas = fromAtlas;
		this.url = url;
		this.position = position;
	}

	public Position_Infos(AtlasRegion region, int position)
	{this(true, region.name, position);}

	@Override
	public String toString()
	{return (fromAtlas ? "atlas:" : "file:") + url + "#" + position;}
}
