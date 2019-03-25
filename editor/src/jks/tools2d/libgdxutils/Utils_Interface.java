package jks.tools2d.libgdxutils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class Utils_Interface
{

	public static TextureRegionDrawable buildDrawingRegionTexture(String texturePath)
	{
		Texture texture  = new Texture(Gdx.files.internal(texturePath),true);
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear) ;
	    TextureRegion TextureRegion = new TextureRegion(texture);
	    return new TextureRegionDrawable(TextureRegion);
	}
	
	public static TextureRegion buildTextureRegion(String texturePath)
	{
		Texture texture  = new Texture(Gdx.files.internal(texturePath));
	    return new TextureRegion(texture);
	}
	
//	public static int getItemPosX(int size, int decal)
//	{
//		
//	}
}
