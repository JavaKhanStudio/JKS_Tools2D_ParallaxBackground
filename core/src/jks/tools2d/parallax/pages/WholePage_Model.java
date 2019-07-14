package jks.tools2d.parallax.pages;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.kryo.DefaultSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.side.SquareBackground;

@DefaultSerializer(WholePage_Model_Serializer.class)
@JsonIgnoreProperties(value = { "preloadValue" })
public class WholePage_Model
{	
	
	public Color topHalf_top ; 
	public Color topHalf_bottom ;
	public float topHalfSize ; 
	
	public Color bottomHalf_top ;
	public Color bottomHalf_bottom ;
	public float bottomHalfSize ; 
	
	public boolean repeatOnX = true ; 
	public boolean repeatOnY = false ;
	
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

	@JsonIgnore
	public List<ParallaxLayer> getDrawing()
	{
		if(preloadValue == null)
			preload() ;
		
		return preloadValue ; 
	}
	
	@JsonIgnore
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
	
	@JsonIgnore
	public void forceLoad(TextureAtlas atlas)
	{
		preloadValue = load(Gvars_Parallax.getWorldWidth(),Gvars_Parallax.getWorldHeight(),atlas); 
	}

	
	protected List<ParallaxLayer> load(float worldWidth, float worldHeight,TextureAtlas atlas)
	{
		List<ParallaxLayer> returningList = new ArrayList<ParallaxLayer>() ; 
		
		for(Parallax_Model parallax : pageModel.pageList)
		{
			returningList.add(buildLayer(parallax,atlas,worldWidth)) ;
		}
		
		return returningList;
	}
	
	protected ParallaxLayer buildLayer(Parallax_Model parallax, TextureAtlas atlas,float worldWidth)
	{
		ParallaxLayer layer = new ParallaxLayer(
				atlas.findRegions(parallax.regionName).get(parallax.regionPosition), 
				true, 
				worldWidth, 
				parallax.parallaxScalingSpeedX,parallax.parallaxScalingSpeedY,
				parallax.sizeRatio) ; 

		layer.setUpEverything(parallax);
		
		return layer ; 
	}
	
	// External Reading
	private List<ParallaxLayer> load(float worldWidth, float worldHeight, String relativePath) 
	{
		if(pageModel.atlasName == null)
			return new ArrayList<ParallaxLayer>() ; 
		
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

	public Color getTopHalf_top() 
	{return topHalf_top;}

	public void setTopHalf_top(Color topHalf_top) 
	{this.topHalf_top = topHalf_top;}

	public Color getTopHalf_bottom() 
	{return topHalf_bottom;}

	public void setTopHalf_bottom(Color topHalf_bottom) 
	{this.topHalf_bottom = topHalf_bottom;}

	public float getTopHalfSize() 
	{return topHalfSize;}

	public void setTopHalfSize(float topHalfSize) 
	{this.topHalfSize = topHalfSize;}

	public Color getBottomHalf_top() 
	{return bottomHalf_top;}

	public void setBottomHalf_top(Color bottomHalf_top) 
	{this.bottomHalf_top = bottomHalf_top;}

	public Color getBottomHalf_bottom() 
	{return bottomHalf_bottom;}

	public void setBottomHalf_bottom(Color bottomHalf_bottom) 
	{this.bottomHalf_bottom = bottomHalf_bottom;}

	public float getBottomHalfSize() 
	{return bottomHalfSize;}

	public void setBottomHalfSize(float bottomHalfSize) 
	{this.bottomHalfSize = bottomHalfSize;}

	public boolean isRepeatOnX() 
	{return repeatOnX;}

	public void setRepeatOnX(boolean repeatOnX) 
	{this.repeatOnX = repeatOnX;}

	public boolean isRepeatOnY()
	{return repeatOnY;}

	public void setRepeatOnY(boolean repeatOnY) 
	{this.repeatOnY = repeatOnY;}

	public Page_Model getPageModel()
	{return pageModel;}

	public void setPageModel(Page_Model pageModel) 
	{this.pageModel = pageModel;}

}