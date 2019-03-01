package jks.tools2d.parallax.editor.vue.edition;

import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;

public class TR_Infos
{

	boolean fromAtlas ;
	String url ; 
	int position ; 
	
	public TR_Infos(AtlasRegion region)
	{
		fromAtlas = true ; 
		url = region.name ; 
		position = region.index ;
	}
	
}
