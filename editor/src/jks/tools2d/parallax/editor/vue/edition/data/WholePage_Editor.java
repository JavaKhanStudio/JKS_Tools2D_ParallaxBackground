package jks.tools2d.parallax.editor.vue.edition.data;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.kryo.DefaultSerializer;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.WholePage_Model;

@DefaultSerializer(WholePage_Editor_Serializer.class)
public class WholePage_Editor extends WholePage_Model
{
	public ArrayList<Boolean> inside ;
	
	public WholePage_Editor()
	{
		super() ; 
		inside = new  ArrayList<Boolean>() ; 
	}
	
	@Override
	protected List<ParallaxLayer> load(float worldWidth, float worldHeight,TextureAtlas atlas)
	{
		List<ParallaxLayer> returningList = new ArrayList<ParallaxLayer>() ; 
		ParallaxLayer layer = null ; 
		
		int inc = 0 ; 
		for(Parallax_Model parallax : pageModel.pageList)
		{
			if(inside.get(inc))
				layer = buildLayer(parallax,atlas,worldWidth) ; 
			else
			{
				//System.out.println(parallax.getRegion_Name());
//				layer = buildOutsideLayer(parallax, worldWidth) ; 
			}
			
			returningList.add(layer) ;
			
			inc ++ ; 
		}
		
		return returningList;
	}
	
//	protected Parallax_Model buildOutsideLayer(Parallax_Model parallax, float worldWidth)
//	{
//		TextureRegion texture = new TextureRegion(new Texture(new FileHandle(parallax.regionName))) ; 
//		return null ; 
//	}
}