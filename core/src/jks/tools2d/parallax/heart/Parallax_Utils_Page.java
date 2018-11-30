package jks.tools2d.parallax.heart;

public class Parallax_Utils_Page 
{

	public static void setPage(ParallaxPageModel pageModel) 
	{
		Parallax_Heart.currentPage = pageModel ; 
		Parallax_Heart.parallaxMainPage.layers.clear();
		Parallax_Heart.parallaxMainPage.addLayers(Parallax_Heart.currentPage.getDrawing());
		Parallax_Utils_Background.setBackground(Parallax_Heart.currentPage) ;
	}
	
	public static void transfertIntoPage(ParallaxPageModel pageModel, float inXSecondes) 
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
	
	static void drawPage(float delta)
	{
		Parallax_Heart.parallaxMainPage.draw(Parallax_Heart.worldCamera, Parallax_Heart.batch);
		
		if(Parallax_Heart.astres != null)
			Parallax_Heart.astres.draw(Parallax_Heart.batch);
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
