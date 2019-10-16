package jks.tools2d.parallax.editor.gvars;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.VisUI;

public class GVars_UI implements Runnable 
{
	public static Skin baseSkin;
	public static Stage mainUi;
	public static Table interaction;
	
	public static FreeTypeFontGenerator generator ;
	public static FreeTypeFontParameter parameter ;
	public static BitmapFont mainFont ; 
	
	public static void init() 
	{
		baseSkin = new Skin(Gdx.files.internal("skins/uis/uiskin.json"));
		baseSkin.getFont("default-font") ; 
		parameter = new FreeTypeFontParameter() ;
		generator = new FreeTypeFontGenerator(Gdx.files.internal("ui/fonts/OpenSansRegular.ttf")) ;
		resize() ;
		VisUI.load(GVars_UI.baseSkin);
		mainUi = new Stage();
		Gdx.input.setInputProcessor(mainUi);
		
		
	}

	public static void resize()
	{
//		BitmapFont bitmapFont = VisUI.getSkin().getFont("default-font") ;
		System.out.println(baseSkin.getFont("default-font"));
		parameter.size = 30 ;
		mainFont = generator.generateFont(parameter);
		mainFont.getRegion().getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
		baseSkin.add("default-font", mainFont);
		System.out.println(baseSkin.getFont("default-font"));
	}
	
	public static void reset()
	{
		mainUi = new Stage();
		Gdx.input.setInputProcessor(mainUi);
	}

	@Override
	public void run() 
	{init();}
}