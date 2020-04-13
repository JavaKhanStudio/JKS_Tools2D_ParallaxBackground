package jks.tools2d.parallax.heart;

import java.io.FileNotFoundException;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;

import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.Utils_Parralax;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model;
import jks.tools2d.parallax.side.SquareBackground;

public class Parallax_Heart 
{
	public OrthographicCamera worldCamera;
	public SpriteBatch batch;
	public ParallaxPageReader parallaxReader;
	
	// Background
	public ShapeRenderer shapeRender ; 
	public SquareBackground topSquare ;
	public SquareBackground bottomSquare ;
	
	public WholePage_Model currentPage ; 
	public WholePage_Model currentTransfertPage ; 
	
	public float screenSpeedConsumableX ; 
	public float screenSpeedConstantX ; 
	
	public float screenSpeedConsumableY ; 
	public float screenSpeedConstantY ; 
	
	public String relativePath = "";
	
	
	private static int defaultWidth = 40 ;
	
	public Parallax_Heart() 
	{	
		Gvars_Parallax.setWorldWidth(defaultWidth);
		Gvars_Parallax.setWorldHeight(Utils_Parralax.calculateOtherDimension(true, defaultWidth, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
	
		worldCamera = new OrthographicCamera() ;
		worldCamera.setToOrtho(false,Gvars_Parallax.getWorldWidth(),Gvars_Parallax.getWorldHeight());
		
		batch = new SpriteBatch();

		shapeRender = new ShapeRenderer() ;
		parallaxReader = new ParallaxPageReader();
		Gvars_Parallax.getManager() ; 
	}
	
	public Parallax_Heart(String internalPath) throws FileNotFoundException 
	{
		this() ; 
		WholePage_Model page = Utils_Page.loadPage(internalPath) ; 
		Parallax_Utils_Page.setPage(this,page) ; 
	}
	
	public Parallax_Heart(
			OrthographicCamera worldCamera,
			OrthographicCamera staticCamera,
			SpriteBatch batch,
			float worldWidth,
			float worldHeight) 
	{
		this.worldCamera = worldCamera ;
		Gvars_Parallax.setWorldWidth(worldWidth);
		Gvars_Parallax.setWorldHeight(worldHeight);
		
		this.batch = batch;

		shapeRender = new ShapeRenderer() ;
		parallaxReader = new ParallaxPageReader(); 
	}
	
	public Parallax_Heart(
			OrthographicCamera worldCamera,
			OrthographicCamera staticCamera,
			SpriteBatch batch,
			WholePage_Model pageModel,
			float worldWidth,
			float worldHeight
			)
	{
		this(worldCamera,staticCamera,batch,worldWidth,worldHeight) ;
		Parallax_Utils_Page.setPage(this,pageModel) ; 
	}
	
	public void setPage(WholePage_Model model)
	{Parallax_Utils_Page.setPage(this,model);}
	
	public void transfertIntoPage(WholePage_Model model, float intoXSec)
	{
		Parallax_Utils_Page.transfertIntoPage(this,model, intoXSec);
	}
	
	public void act(float delta)
	{
		if(topSquare != null)
			topSquare.act(delta);
		
		if(bottomSquare != null)
			bottomSquare.act(delta);
		
		if(parallaxReader != null)
			parallaxReader.act(delta,screenSpeedConsumableX + screenSpeedConstantX,screenSpeedConsumableY + screenSpeedConstantY);	
		
		screenSpeedConsumableX = 0 ; 
		screenSpeedConsumableY = 0 ; 
	}

	public void render()
	{
		worldCamera.update();
		batch.setProjectionMatrix(worldCamera.combined);
		drawBackGround() ; 
		batch.enableBlending();
		batch.begin() ;
		parallaxReader.draw(worldCamera, batch);

		batch.end();
	}
	
	public void drawBackGround()
	{
		shapeRender.begin(ShapeType.Filled);
		
		if(topSquare != null)
			topSquare.draw(shapeRender);

		if(bottomSquare != null)
			bottomSquare.draw(shapeRender);
		
		shapeRender.end();
	}
	
	public String getAtlasName()
	{
		return currentPage.pageModel.getAtlasName() ; 
	}
}