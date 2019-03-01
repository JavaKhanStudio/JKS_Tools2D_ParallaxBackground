package jks.tools2d.parallax.heart;

import jks.tools2d.parallax.ParallaxPage;
import jks.tools2d.parallax.pages.WholePage_Model;

public class Parallax_Utils_Page 
{

	public static void setPage(WholePage_Model pageModel) 
	{
		Parallax_Heart.currentPage = pageModel ; 
		Parallax_Heart.parallaxMainPage.layers.clear();
		Parallax_Heart.parallaxMainPage.addLayers(Parallax_Heart.currentPage.getDrawing());
		Parallax_Utils_Background.setBackground(Parallax_Heart.currentPage) ;
	}
	
	public static void setSecondPage(WholePage_Model pageModel) 
	{
		if(Parallax_Heart.parallaxSecondePage == null)
			Parallax_Heart.parallaxSecondePage = new ParallaxPage() ; 
		
		Parallax_Heart.parallaxSecondePage.layers.clear();
		Parallax_Heart.parallaxSecondePage.addLayers(pageModel.getDrawing());
	}
	
	public static void transfertIntoPage(WholePage_Model pageModel, float inXSecondes) 
	{
		Parallax_Heart.currentTransfertPage = pageModel ; 
	
		Parallax_Heart.parallaxMainPage.addLayersTransfert(pageModel,inXSecondes); 
		Parallax_Heart.topSquare.transfertInto(pageModel.topHalf_top, pageModel.topHalf_bottom, inXSecondes );
		Parallax_Heart.bottomSquare.transfertInto(pageModel.bottomHalf, pageModel.bottomHalf, inXSecondes );
		
		if(Parallax_Heart.parallaxSecondePage != null)
		{
			Parallax_Heart.parallaxSecondePage.addColorTransfert(Parallax_Heart.currentPage.colorSurronding, inXSecondes);
			Parallax_Heart.parallaxSecondePage.set_newLayer_Color(Parallax_Heart.currentPage.colorSurronding);
		}
	}
		
	static void act(float delta)
	{
		Parallax_Heart.parallaxMainPage.act(delta) ; 
		if(Parallax_Heart.astres != null)
			Parallax_Heart.astres.act(delta);
	}
	
	static void drawPage(ParallaxPage page)
	{
		page.draw(Parallax_Heart.worldCamera, Parallax_Heart.batch);

	}
	
	static void drawSecondePage()
	{
		if(Parallax_Heart.parallaxSecondePage != null)
		{
			Parallax_Heart.parallaxSecondePage.draw(Parallax_Heart.worldCamera, Parallax_Heart.batch);
		}
	}
}