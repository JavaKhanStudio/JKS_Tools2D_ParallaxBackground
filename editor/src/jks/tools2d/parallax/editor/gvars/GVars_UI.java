package jks.tools2d.parallax.editor.gvars;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.kotcrab.vis.ui.widget.VisTextField.VisTextFieldStyle;

public class GVars_UI implements Runnable 
{
	public static Skin baseSkin;
	public static Stage mainUi;
	public static Table interaction;
	
	public static FreeTypeFontGenerator generator ;
	public static FreeTypeFontParameter parameter ;
	public static BitmapFont mainFont ; 
	public static BitmapFont areaTextFont ; 
	
	public static BitmapFont font_Title ; 
	
	public static LabelStyle labelStyle_Title ;
	public static LabelStyle labelStyle_Second ;
	public static LabelStyle labelStyle_OptionsTitle ;
	public static VisTextFieldStyle areaStyle ;
	
	public static void init() 
	{
		mainUi = new Stage();
		baseSkin = new Skin(Gdx.files.internal("skins/uis/uiskin.json"));
		baseSkin.getFont("default-font") ; 
		initFonts() ; 
		parameter = new FreeTypeFontParameter() ;
		generator = new FreeTypeFontGenerator(Gdx.files.internal("ui/fonts/OpenSansRegular.ttf")) ;
		resize() ;
		VisUI.load(GVars_UI.baseSkin);
		Gdx.input.setInputProcessor(mainUi);
	}
	
	public static void initFonts()
	{
		labelStyle_Title = new LabelStyle(baseSkin.get("default", LabelStyle.class)) ; 
		labelStyle_Second = new LabelStyle(baseSkin.get("default", LabelStyle.class)) ;
		labelStyle_OptionsTitle = new LabelStyle(baseSkin.get("default", LabelStyle.class)) ; 
		areaStyle = new VisTextFieldStyle( baseSkin.get("default", VisTextFieldStyle.class)) ;
	}

	public static void resize()
	{
		parameter.color = Color.BLACK ; 
		parameter.size = 30 ;
		mainFont = generator.generateFont(parameter);
		mainFont.getRegion().getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
		
		parameter.size = Gdx.graphics.getWidth()/70 ;
		parameter.color = Color.WHITE ; 
		areaTextFont = generator.generateFont(parameter);
		areaStyle.font = areaTextFont ; 
		areaStyle.messageFont = areaTextFont ; 
		labelStyle_OptionsTitle.font = areaTextFont ; 
		
		massResize(mainUi.getActors()) ; 
	}
	
	public static void massResize(Array<Actor> actorList)
	{
		for(int i = 0 ; i < mainUi.getActors().size; i++)
		{
			Actor actor = mainUi.getActors().get(i) ; 
			workOnActor(actor) ;			
		}
	}
	
	public static void workOnActor(Actor actor)
	{
		if(actor instanceof Label)
		{
			Label label = (Label)actor ;
			label.setStyle(label.getStyle());
		}
		else if(actor instanceof TextButton)
		{
			TextButton button = (TextButton)actor ;
			button.getLabel().setStyle(button.getLabel().getStyle());
			button.invalidate();
		}
		else if(actor instanceof SelectBox)
		{
			SelectBox box = (SelectBox)actor ;
			box.invalidate();
		}
		else if(actor instanceof VisCheckBox)
		{
			VisCheckBox checkBox = (VisCheckBox)actor ;
			checkBox.getLabel().setStyle(checkBox.getLabel().getStyle());
			checkBox.setSize(300, 300);
			checkBox.invalidate();
		}
		else if(actor instanceof VisTextField)
		{
			VisTextField textField = (VisTextField)actor ;
			textField.getStyle().messageFont = areaTextFont ;
			textField.getStyle().font = areaTextFont ;
			textField.setStyle(textField.getStyle());
			textField.invalidate();
			System.out.println("resize text");
		}
		else if(actor instanceof WidgetGroup)
		{
			WidgetGroup group = (WidgetGroup)actor ;
			Array<Actor> subGroup = group.getChildren() ; 
			for(int i = 0 ; i < subGroup.size; i++)
				workOnActor(subGroup.get(i)) ; 
		}
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