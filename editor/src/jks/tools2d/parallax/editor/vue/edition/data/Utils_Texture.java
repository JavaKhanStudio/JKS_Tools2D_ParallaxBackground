package jks.tools2d.parallax.editor.vue.edition.data;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class Utils_Texture 
{

	public static TextureRegion getTextureRegionFromPath(String path)
	{
		Texture texture = new Texture(new FileHandle(path),true) ;
		texture.setFilter(TextureFilter.MipMap, TextureFilter.MipMap);
		return new TextureRegion(texture) ; 
	}
	
}