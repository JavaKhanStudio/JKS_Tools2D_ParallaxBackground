package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;


public class TextureRegionParallaxLayer extends ParallaxLayer
{

	private TextureRegion texRegion;
	private float pad_Left=0, pad_Right=0, pad_Bottom=0, pad_Top=0;
	private float region_Width,region_Height;
	
	
	/**
	 * Creates a TextureRegionParallaxLayer with regionWidth and regionHeight equal that of the texRegion. Paddings are set to 0.
	 * @param texRegion the texture region
	 * @param parallaxScrollRatio the parallax ratio in x and y direction
	 */
	public TextureRegionParallaxLayer(TextureRegion texRegion, Vector2 parallaxScrollRatio)
	{
		this.texRegion = texRegion;
		setRegionWidth(texRegion.getRegionWidth());
		setRegionHeight(texRegion.getRegionHeight());
		setParallaxRatio(parallaxScrollRatio);
	}
	
	/**
	 * Creates a TextureRegionParallaxLayer with regionWidth and regionHeight equal to parameters width and height. Paddings are set to 0.
	 * @param texRegion the texture region
	 * @param width width to be used as regionWidth
	 * @param height height to be used as regionHeight
	 * @param parallaxScrollRatio the parallax ratio in x and y direction
	 */
	public TextureRegionParallaxLayer(TextureRegion texRegion, float regionWidth, float regionHeight, Vector2 parallaxScrollRatio)
	{
		this.texRegion = texRegion;
		setRegionWidth(regionWidth);
		setRegionHeight(regionHeight);
		setParallaxRatio(parallaxScrollRatio);
	}
	
	/**
	 * Creates a TextureRegionParallaxLayer with either regionWidth or regionHeight equal oneDimen specified, while the other is calculated maintaining the aspect ratio of the region. Paddings are set to 0.
	 * @param texRegion texRegion the texture region
	 * @param oneDimen either regionWidth of regionHeight
	 * @param parallaxScrollRatio the parallax ratio in x and y direction
	 * @param wh what does oneDimen represent
	 */
	public TextureRegionParallaxLayer(TextureRegion texRegion, float oneDimen, Vector2 parallaxScrollRatio, boolean isWidth)
	{
		this.texRegion = texRegion;
		
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
	
	
	
	/**
	 * draws the texture region at x y ,with left and bottom padding 
	 * <p>
	 * You might be wondering that why are topPadding and rightPadding not used , what is their use then . Well they are used by ParallaxBackground when it renders this layer . During rendering it pings the {@link #getWidth()}/{@link #getHeight()} method of this layer which in {@link TextureRegionParallaxLayer} implementation return the sum of regionWidth/regionHeight and paddings.
	 */
	@Override
	public void draw(Batch batch, float x, float y) 
	{
		batch.draw(texRegion, x + pad_Left, y + pad_Bottom, getRegionWidth(), getRegionHeight());
	}
	
	@Override
	public void act(float delta) 
	{
		decalX += delta * speed ;
		
		if(Math.abs(decalX) >= region_Width) 
			decalX = 0;
	}

	/**
	 * returns the width of this layer (regionWidth+padLeft+padRight)
	 */
	@Override
	public float getWidth() 
	{return getPadLeft()+getRegionWidth()+getPadRight();}

	/**
	 * returns the height of this layer (regionHeight+padTop+padBottom)
	 */
	@Override
	public float getHeight() 
	{return getPadTop()+getRegionHeight()+getPadBottom();}
	
	/**
	 * sets left right top bottom padding to same value
	 * @param pad padding
	 */
	public void setAllPad(float pad)
	{
		setPadLeft(pad);
		setPadRight(pad);
		setPadTop(pad);
		setPadBottom(pad);
	}

	/**
	 * returns texture region of this layer
	 * @return texture region
	 */
	public TextureRegion getTexRegion()
	{return texRegion;}

	/**
	 * get left padding
	 * @return left padding
	 */
	public float getPadLeft() 
	{return pad_Left;}

	/**
	 * sets the left padding
	 * @param left padding
	 */
	public void setPadLeft(float padLeft) 
	{this.pad_Left = padLeft;}

	/**
	 * get right padding
	 * @return right padding
	 */
	public float getPadRight() 
	{return pad_Right;}

	/**
	 * sets the right padding
	 * @param right padding
	 */
	public void setPadRight(float padRight) 
	{this.pad_Right = padRight;}

	/**
	 * get bottom padding
	 * @return bottom padding
	 */
	public float getPadBottom() 
	{return pad_Bottom;}

	/**
	 * sets the bottom padding
	 * @param bottom padding
	 */
	public void setPadBottom(float padBottom)
	{this.pad_Bottom = padBottom;}

	/**
	 * get top padding
	 * @return top padding
	 */
	public float getPadTop() 
	{return pad_Top;}

	/**
	 * sets the top padding
	 * @param top padding
	 */
	public void setPadTop(float padTop) 
	{this.pad_Top = padTop;}


	/**
	 * return the region width of this layer
	 * @return region width
	 */
	public float getRegionWidth() 
	{return region_Width;}
	
	/**
	 * return the region height of this layer
	 * @return region height
	 */
	public float getRegionHeight() 
	{return region_Height;}

	
	private void setRegionWidth(float width)
	{this.region_Width = width;}
	
	private void setRegionHeight(float height)
	{this.region_Height = height;}

}
