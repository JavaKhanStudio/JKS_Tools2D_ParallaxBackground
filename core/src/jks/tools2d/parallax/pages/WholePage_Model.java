package jks.tools2d.parallax.pages;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryo.DefaultSerializer;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.side.SquareBackground;

@DefaultSerializer(WholePage_Model_Serializer.class)
public class WholePage_Model
{	
	public Color topHalf_top ; 
	public Color topHalf_bottom ;
	public float topHalfSize ; 
	
	public Color bottomHalf_top ;
	public Color bottomHalf_bottom ;
	public float bottomHalfSize ; 
	
	public Page_Model pageModel ;
	
	public List<ParallaxLayer> preloadValue ;
	
	public WholePage_Model()
	{
		topHalf_top = Color.WHITE ; 
		topHalf_bottom = Color.WHITE ; 
		bottomHalf_top = Color.WHITE ; 
		bottomHalf_bottom = Color.WHITE ;
		topHalfSize = 0.5f ;
		bottomHalfSize = 0.5f ; 
		
		pageModel = new Page_Model() ; 
	}
	
	// Testing constructor
	public WholePage_Model(String atlasPath, Color topHalf_top, Color topHalf_bottom, Color bottomHalf_top, Color bottomHalf_bottom)
	{
		this.topHalf_top = topHalf_top ; 
		this.topHalf_bottom = topHalf_bottom ; 
		this.bottomHalf_top = bottomHalf_top ; 
		this.bottomHalf_bottom = bottomHalf_bottom ; 
		
		pageModel = new Page_Model() ; 
		pageModel.atlasName = atlasPath ; 
		pageModel.outside = false ; 
	}
	
	public WholePage_Model(String atlasPath)
	{
		pageModel = new Page_Model() ; 
		pageModel.atlasName = atlasPath ; 
		pageModel.outside = false ; 
	}

	public List<ParallaxLayer> getDrawing()
	{
		if(preloadValue == null)
			preload() ;
		
		return preloadValue ; 
	}
	
	public List<ParallaxLayer> getDrawing(String relativePath)
	{
		if("".equals(relativePath))
			return getDrawing() ;
		
		if(preloadValue == null)
			preload(relativePath) ;
		
		return preloadValue ; 
	}
	
	/*
	 * 1   = empty screen
	 * 0.5 = half screen
	 * 0   = full screen
	 */
	public SquareBackground buildTopSquareBackground(float screenPercentage)
	{return new SquareBackground(topHalf_top.cpy(),topHalf_bottom.cpy(),Gdx.graphics.getHeight() * screenPercentage, true) ;}
	
	public SquareBackground buildBottomSquareBackground(float screenPercentage)
	{return new SquareBackground(bottomHalf_top.cpy(),bottomHalf_bottom.cpy(),Gdx.graphics.getHeight() * screenPercentage, false) ;}
	
	public void preload()
	{
		preloadValue = load(Gvars_Parallax.getWorldWidth(),Gvars_Parallax.getWorldHeight()); 
	}
	
	public void preload(String relativePath)
	{
		preloadValue = load(Gvars_Parallax.getWorldWidth(),Gvars_Parallax.getWorldHeight(),relativePath); 
	}
	
	public void forceLoad(TextureAtlas atlas)
	{
		preloadValue = load(Gvars_Parallax.getWorldWidth(),Gvars_Parallax.getWorldHeight(),atlas); 
	}

	
	protected List<ParallaxLayer> load(float worldWidth, float worldHeight,TextureAtlas atlas)
	{
		List<ParallaxLayer> returningList = new ArrayList<ParallaxLayer>() ; 
		ParallaxLayer layer ; 
		
		for(Parallax_Model parallax : pageModel.pageList)
		{
			returningList.add(buildLayer(parallax,atlas,worldWidth)) ;
		}
		
		return returningList;
	}
	
	protected ParallaxLayer buildLayer(Parallax_Model parallax, TextureAtlas atlas,float worldWidth)
	{
		ParallaxLayer layer = new ParallaxLayer(
				atlas.findRegions(parallax.region_Name).get(parallax.region_Position), 
				true, 
				worldWidth, 
				new Vector2(parallax.parallaxScalingSpeedX,parallax.parallaxScalingSpeedY),
				parallax.sizeRatio) ; 

		layer.setDecalPercentY(parallax.decal_Y_Ratio);
		layer.setDecalPercentX(parallax.decal_X_Ratio);
		
		layer.setSpeedAtRest(parallax.speed);
		return layer ; 
	}
	
	// External Reading
	private List<ParallaxLayer> load(float worldWidth, float worldHeight, String relativePath) 
	{
		TextureAtlas atlas = new TextureAtlas(new FileHandle(relativePath + "/" + pageModel.atlasName));
		return load(worldWidth, worldHeight, atlas) ; 
	}
	
	// Internal Reading
	private List<ParallaxLayer> load(float worldWidth, float worldHeight) 
	{
		Gvars_Parallax.getManager().load(pageModel.atlasName, TextureAtlas.class);
		Gvars_Parallax.getManager().finishLoadingAsset(pageModel.atlasName);
		
		TextureAtlas atlas = new TextureAtlas(pageModel.atlasName);
		return load(worldWidth, worldHeight, atlas) ; 
	}
	
	public void cleanPath()
	{
		pageModel.atlasName = pageModel.atlasName.substring(pageModel.atlasName.lastIndexOf("/") + 1, pageModel.atlasName.length()) ; 
	}
		
}