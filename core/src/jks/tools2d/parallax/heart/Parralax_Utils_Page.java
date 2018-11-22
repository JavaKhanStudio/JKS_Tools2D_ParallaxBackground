package jks.tools2d.parallax.heart;

import jks.tools2d.parallax.ParallaxPage;

public class Parralax_Utils_Page 
{

	public static void setPage(ParralaxPageModel pageModel) 
	{
		Parrallax_Heart.currentPage = pageModel ; 
		Parrallax_Heart.parallaxMainPage.addLayers(Parrallax_Heart.currentPage.howToDraw(Parrallax_Heart.worldWidth,Parrallax_Heart.worldHeight));
		Parralax_Utils_Background.setBackground(Parrallax_Heart.currentPage) ;
	
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
		Parrallax_Heart.currentPage = Parrallax_Heart.nextParallax.get(0) ;
		
		Parrallax_Heart.parallaxMainPage.addLayersTransfert(Parrallax_Heart.parralx_Sequence_Index.get(Parrallax_Heart.currentPage),Parrallax_Heart.transfertTime * Parrallax_Heart.currentPage.timeToTransfertInto);
		Parrallax_Heart.nextParallax.remove(0) ; 
		Parrallax_Heart.square.transfertInto(Parrallax_Heart.currentPage.top, Parrallax_Heart.currentPage.bottom, Parrallax_Heart.transfertTime * Parrallax_Heart.currentPage.timeToTransfertInto);
		
		if(Parrallax_Heart.parallaxSecondePage != null)
		{
			Parrallax_Heart.parallaxSecondePage.addColorTransfert(Parrallax_Heart.currentPage.colorSurronding, Parrallax_Heart.transfertTime * Parrallax_Heart.currentPage.timeToTransfertInto);
			Parrallax_Heart.parallaxSecondePage.set_newLayer_Color(Parrallax_Heart.currentPage.colorSurronding);
		}
			
	}

	
	static void drawPage(float delta)
	{
		Parrallax_Heart.parallaxMainPage.act(delta) ; 
		Parrallax_Heart.parallaxMainPage.draw(Parrallax_Heart.worldCamera, Parrallax_Heart.batch);
	}
	
	static void drawSecondePage(float delta)
	{
		if(Parrallax_Heart.parallaxSecondePage != null)
		{
			Parrallax_Heart.parallaxSecondePage.act(delta) ; 
			Parrallax_Heart.parallaxSecondePage.draw(Parrallax_Heart.worldCamera, Parrallax_Heart.batch);
		}
	}


}
