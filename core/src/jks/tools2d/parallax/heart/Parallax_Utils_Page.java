package jks.tools2d.parallax.heart;

public class Parallax_Utils_Page 
{

	public static void setPage(ParallaxPageModel pageModel) 
	{
		Parallax_Heart.currentPage = pageModel ; 
		Parallax_Heart.parallaxMainPage.layers.clear();
		Parallax_Heart.parallaxMainPage.addLayers(Parallax_Heart.currentPage.howToDraw(Parallax_Heart.worldWidth,Parallax_Heart.worldHeight));
		Parallax_Utils_Background.setBackground(Parallax_Heart.currentPage) ;
	}
	
	public static void transfertIntoPage(ParallaxPageModel pageModel, float inXSecondes) 
	{
		Parallax_Heart.currentTransfertPage = pageModel ; 
	
		Parallax_Heart.parallaxMainPage.addLayersTransfert(pageModel,inXSecondes); 
		Parallax_Heart.square.transfertInto(pageModel.top, pageModel.bottom, inXSecondes );
		
		if(Parallax_Heart.parallaxSecondePage != null)
		{
			Parallax_Heart.parallaxSecondePage.addColorTransfert(Parallax_Heart.currentPage.colorSurronding, inXSecondes);
			Parallax_Heart.parallaxSecondePage.set_newLayer_Color(Parallax_Heart.currentPage.colorSurronding);
		}
			
	
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
