package jks.tools2d.parallax.side;

import java.util.List;

import jks.tools2d.parallax.TextureRegionParallaxLayer;

public interface DrawingInstruction 
{

	List<TextureRegionParallaxLayer> howToDraw(float worldWidth, float worldHeight) ;
	
}
