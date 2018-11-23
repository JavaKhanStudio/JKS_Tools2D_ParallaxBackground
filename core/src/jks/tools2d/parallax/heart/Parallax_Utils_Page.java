package jks.tools2d.parallax.heart;

import jks.tools2d.parallax.ParallaxPage;

public class Parallax_Utils_Page 
{

	public static void setPage(ParallaxPageModel pageModel) 
	{
		Parallax_Heart.currentPage = pageModel ; 
		Parallax_Heart.parallaxMainPage.addLayers(Parallax_Heart.currentPage.howToDraw(Parallax_Heart.worldWidth,Parallax_Heart.worldHeight));
		Parallax_Utils_Background.setBackground(Parallax_Heart.currentPage) ;
	
	}

	/*
	static void computeNextBackground()
	{
		if(!Parrallax_Heart.parallaxBackground.isInTransfer())
		{
			if(Parrallax_Heart.nextParallax.size() > 0)
			{
				Parralax_Utils_Page.computeNextPage_parralax(Parrallax_Heart.parallaxBackground) ; 
				Parralax_Utils_Background.computeNextBackground_Color(Parrallax_Heart.parallaxBackground) ;
			}
			else if(Parrallax_Heart.parallax_Sequence_ShowOrder.size() > 0)
			{
				Parrallax_Heart.nextParallax.addAll(Parrallax_Heart.parallax_Sequence_ShowOrder) ;
				computeNextBackground() ;
			}
		}
	}
*/
	static void computeNextPage_parralax(ParallaxPage background)
	{
		Parallax_Heart.currentPage = Parallax_Heart.nextParallax.get(0) ;
		
		Parallax_Heart.parallaxMainPage.addLayersTransfert(Parallax_Heart.parralx_Sequence_Index.get(Parallax_Heart.currentPage),Parallax_Heart.transfertTime * Parallax_Heart.currentPage.timeToTransfertInto);
		Parallax_Heart.nextParallax.remove(0) ; 
		Parallax_Heart.square.transfertInto(Parallax_Heart.currentPage.top, Parallax_Heart.currentPage.bottom, Parallax_Heart.transfertTime * Parallax_Heart.currentPage.timeToTransfertInto);
		
		if(Parallax_Heart.parallaxSecondePage != null)
		{
			Parallax_Heart.parallaxSecondePage.addColorTransfert(Parallax_Heart.currentPage.colorSurronding, Parallax_Heart.transfertTime * Parallax_Heart.currentPage.timeToTransfertInto);
			Parallax_Heart.parallaxSecondePage.set_newLayer_Color(Parallax_Heart.currentPage.colorSurronding);
		}
			
	}

	
	static void drawPage(float delta)
	{
		Parallax_Heart.parallaxMainPage.act(delta) ; 
		Parallax_Heart.parallaxMainPage.draw(Parallax_Heart.worldCamera, Parallax_Heart.batch);
	}
	
	static void drawSecondePage(float delta)
	{
		if(Parallax_Heart.parallaxSecondePage != null)
		{
			Parallax_Heart.parallaxSecondePage.act(delta) ; 
			Parallax_Heart.parallaxSecondePage.draw(Parallax_Heart.worldCamera, Parallax_Heart.batch);
		}
	}


}
