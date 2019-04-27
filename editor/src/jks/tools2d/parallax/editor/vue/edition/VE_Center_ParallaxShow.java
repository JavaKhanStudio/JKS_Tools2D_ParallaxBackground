package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.gvars.GVars_Ui.mainUi;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.isPause;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.parr_Pos_X;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.parr_Pos_Y;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.parr_Size_X;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.parr_Size_Y;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.size_Bloc_Parallax;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.size_Bloc_Selection;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.size_Height_Bloc_Parallax_Controle;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import jks.tools2d.libgdxutils.JksCheckBox;
import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.editor.gvars.GVars_Ui;
import jks.tools2d.parallax.pages.WholePage_Model; 

public class VE_Center_ParallaxShow
{
	float decalX = 2 ; 
	float buttonSize ;
	
	public static VE_Center_ParallaxShow build(Object ref)
	{
		VE_Center_ParallaxShow show = null ;
		if(ref instanceof TextureAtlas)
		{
			TextureAtlas atlas = (TextureAtlas)ref ; 
			show = new VE_Center_ParallaxShow(atlas) ; 
		}
		else if(ref instanceof WholePage_Model)
		{
			WholePage_Model page = (WholePage_Model)ref ; 
			show =  new VE_Center_ParallaxShow(page) ; 
		}

		return show ; 
	}
	
	
	VE_Center_ParallaxShow(TextureAtlas atlas)
	{
		initShow() ; 
		
		WholePage_Model page = new WholePage_Model(); 
		page.forceLoad(atlas);
		GVars_Vue_Edition.setPage(page) ; 
			
		buildOptions() ; 
	}
	
	VE_Center_ParallaxShow(WholePage_Model page)
	{
		initShow() ; 
		GVars_Vue_Edition.setPage(page) ; 
		buildOptions() ; 
	}
	
	public void initShow()
	{
		parr_Size_X = (int)(((Gdx.graphics.getWidth()/3) * 2) - decalX * 2) ; 
		parr_Size_Y = (parr_Size_X/16) * 9 ; 
		parr_Pos_X = size_Bloc_Selection + (Gdx.graphics.getWidth() - size_Bloc_Selection - parr_Size_X)/2; 
		parr_Pos_Y = size_Height_Bloc_Parallax_Controle ;
		
		buttonSize = size_Height_Bloc_Parallax_Controle/1.5f ; 
	}
	
	Table buildOptionsTable ; 
	public void buildOptions()
	{
		buildOptionsTable = new Table() ;
		buildOptionsTable.setBounds(size_Bloc_Selection, 0, size_Bloc_Parallax, size_Height_Bloc_Parallax_Controle);
		
		// TODO Effacer et mettre dans le UISKIN
		CheckBoxStyle checkBox = new CheckBoxStyle() ;
		checkBox.checkboxOff = baseSkin.newDrawable(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/button_play.png"));
		checkBox.checkboxOn = baseSkin.newDrawable(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/button_pause.png"));
		checkBox.font = baseSkin.getFont("default-font");
		
		JksCheckBox startStop = new JksCheckBox("",checkBox,false) ; 
		startStop.addListener(new ChangeListener() 
		{
			public void changed (ChangeEvent event, Actor actor) 
			{isPause = !startStop.isChecked();}
		});

		startStop.setSize(buttonSize, buttonSize);
//		startStop.setSize(300, 300);
		startStop.setPosition(parr_Size_X/2, size_Height_Bloc_Parallax_Controle/2 - buttonSize/2);
		
		Slider parallaxSpeedSlider = new Slider(-10, 10, 1, false, GVars_Ui.baseSkin) ; 
		parallaxSpeedSlider.setValue(0) ; 
		parallaxSpeedSlider.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{
				GVars_Vue_Edition.screenSpeed = parallaxSpeedSlider.getValue() ; 
				return true ;
			}
			
			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer)
			{
				GVars_Vue_Edition.screenSpeed = parallaxSpeedSlider.getValue() ; 
			}
		}) ; 
		
		parallaxSpeedSlider.setSize(buildOptionsTable.getWidth()/2 - buttonSize, buttonSize);
		parallaxSpeedSlider.setPosition(buttonSize/3, buildOptionsTable.getHeight()/2 - parallaxSpeedSlider.getHeight()/2);
		
		
		buildOptionsTable.addActor(startStop) ;
		buildOptionsTable.addActor(parallaxSpeedSlider) ; 
		mainUi.addActor(buildOptionsTable);
	}
}