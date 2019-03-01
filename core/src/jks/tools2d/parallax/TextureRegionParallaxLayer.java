package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;


public class TextureRegionParallaxLayer extends ParallaxLayer
{

	private TextureRegion texRegion;
	private float pad_X = 0, pad_Y = 0 ;
	private float region_Width,region_Height;
	private float sizeRatio =  1;
	
	
	public TextureRegionParallaxLayer(TextureRegion texRegion, float oneDimen, Vector2 parallaxScrollRatio, boolean isWidth)
	{
		this(texRegion, oneDimen, parallaxScrollRatio, 1f, isWidth) ; 
	}
	
	public TextureRegionParallaxLayer(TextureRegion texRegion, float oneDimen, Vector2 parallaxScrollRatio, float sizeRatio, boolean isWidth)
	{
		this.texRegion = texRegion ;
		this.sizeRatio = sizeRatio ;
		
		if(isWidth)
		{
			setRegionWidth(oneDimen);
	    	setRegionHeight(Utils_Parralax.calculateOtherDimension(true, oneDimen, this.texRegion));	
		}
		else
		{
	    	setRegionHeight(oneDimen);
	    	setRegionWidth(Utils_Parralax.calculateOtherDimension(false, oneDimen, this.texRegion));
		}
		
		setParallaxRatio(parallaxScrollRatio);
	}
	

	@Override
	public void draw(Batch batch, float x, float y) 
	{
		batch.draw(texRegion, x + pad_X, y + pad_Y, getRegionWidth(), getRegionHeight());
	}
	
	@Override
	public void act(float delta) 
	{
		decalX += delta * speed ;
		
		if(Math.abs(decalX) >= region_Width) 
			decalX = 0;
	}

	@Override
	public float getWidth() 
	{return getPadX()+getRegionWidth();}

	@Override
	public float getHeight() 
	{return getRegionHeight()+getPadY();}
	
	public void setAllPad(float pad)
	{
		setPadX(pad);
		setPadY(pad);
	}

	public TextureRegion getTexRegion()
	{return texRegion;}

	public float getPadX() 
	{return pad_X;}

	public void setPadX(float padX) 
	{this.pad_X = padX;}

	
	public float getPadY() 
	{return pad_Y;}

	public void setPadY(float padY)
	{this.pad_Y = padY;}

	public float getRegionWidth() 
	{return region_Width * sizeRatio;}

	public float getRegionHeight() 
	{return region_Height * sizeRatio;}
	
	private void setRegionWidth(float width)
	{this.region_Width = width;}
	
	private void setRegionHeight(float height)
	{this.region_Height = height;}
	
}