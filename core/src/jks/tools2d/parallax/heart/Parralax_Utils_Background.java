package jks.tools2d.parallax.heart;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;

import jks.tools2d.parallax.ParallaxPage;

public class Parralax_Utils_Background 
{

	static void drawBackgroundColor(float delta)
	{
		if(Parrallax_Heart.square != null)
		{
			Parrallax_Heart.shapeRender.begin(ShapeType.Filled);
			Parrallax_Heart.square.act(delta);
			Parrallax_Heart.square.draw(Parrallax_Heart.shapeRender);
			Parrallax_Heart.shapeRender.end();
		}
	}

	static void setBackground(ParralaxPageModel model)
	{
		Parrallax_Heart.square = model.buildSquareBackground(0.5f) ;
	}

	/*
	static void computeNextBackground_Color(ParallaxPage background)
	{
		if(Parrallax_Heart.nextColor.size() > 0)
		{
			Parrallax_Heart.parallaxBackground.set_newLayer_Color(Parrallax_Heart.nextColor.get(0));
			Parrallax_Heart.nextColor.remove(0) ; 
		}
	}
	*/
}
