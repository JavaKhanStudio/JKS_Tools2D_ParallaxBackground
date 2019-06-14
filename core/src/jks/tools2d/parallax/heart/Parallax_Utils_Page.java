package jks.tools2d.parallax.heart;

import jks.tools2d.parallax.pages.WholePage_Model;

public class Parallax_Utils_Page 
{

	public static void setPage(Parallax_Heart ref, WholePage_Model pageModel) 
	{
		ref.currentPage = pageModel ; 
		ref.parallaxPage.layers.clear();
		ref.parallaxPage.addLayers(ref.currentPage.getDrawing(ref.relativePath));
		
		ref.topSquare = pageModel.buildTopSquareBackground(pageModel.topHalfSize) ;
		ref.bottomSquare = pageModel.buildBottomSquareBackground(pageModel.bottomHalfSize) ;
	}
	
	public static void transfertIntoPage(Parallax_Heart ref, WholePage_Model pageModel, float inXSecondes) 
	{
		ref.currentTransfertPage = pageModel ; 
	
		ref.parallaxPage.addLayersTransfert(pageModel,inXSecondes); 
		ref.topSquare.transfertInto(pageModel.topHalf_top, pageModel.topHalf_bottom, inXSecondes );
		ref.bottomSquare.transfertInto(pageModel.bottomHalf_top, pageModel.bottomHalf_top, inXSecondes );
	}

}