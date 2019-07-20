package jks.tools2d.parallax.editor.vue.edition.data;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.WholePage_Model;

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
				layer = buildOutsideLayer(parallax, worldWidth) ; 
			
			//TODO make it more fexible
			if(layer == null)
			{
				//pageModel.pageList.remove(parallax) ; 
				inside.remove(inc) ; 
				continue ;
			}
			
			returningList.add(layer) ;
			
			inc ++ ; 
		}
		
		return returningList;
	}
	
	protected ParallaxLayer buildOutsideLayer(Parallax_Model parallax, float worldWidth)
	{
		TextureRegion texture = GVars_Vue_Edition.outsideReserve.get(parallax.regionName) ; 
		if(texture == null) {
			GVars_Vue_Edition.outsideReserve.remove(parallax.regionName) ; 
			return null; 
		}
		ParallaxLayer layer = new ParallaxLayer(
			texture,
			true, 
			worldWidth, 
			parallax.parallaxScalingSpeedX,parallax.parallaxScalingSpeedY,
			parallax.sizeRatio) ; 

		layer.setUpEverything(parallax);
		return layer ; 
	}
}