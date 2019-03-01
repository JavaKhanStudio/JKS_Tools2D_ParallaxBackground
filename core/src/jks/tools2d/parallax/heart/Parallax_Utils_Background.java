package jks.tools2d.parallax.heart;

import static jks.tools2d.parallax.heart.Parallax_Heart.bottomSquare;
import static jks.tools2d.parallax.heart.Parallax_Heart.shapeRender;
import static jks.tools2d.parallax.heart.Parallax_Heart.topSquarePercent;
import static jks.tools2d.parallax.heart.Parallax_Heart.topSquare;

import jks.tools2d.parallax.pages.WholePage_Model;

public class Parallax_Utils_Background 
{

	public static void drawBackground_TopColor()
	{
		if(topSquare != null)
			topSquare.draw(shapeRender);
	}
	
	public static void drawBackground_BottomColor() 
	{
		if(bottomSquare != null)
			bottomSquare.draw(shapeRender);
	}

	public static void setBackground(WholePage_Model model)
	{
		topSquare = model.buildTopSquareBackground(topSquarePercent) ;
		bottomSquare = model.buildBottomSquareBackground(topSquarePercent) ;
	}

}
