package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_UI.baseSkin;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.allImage;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.imageRef;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.isPause;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.optionsControl;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.outsideTextureReserve;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.parr_Pos_X;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.parr_Pos_Y;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.parr_Size_X;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.parr_Size_Y;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.showParallaxFullScreen;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Bloc_Parallax;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Bloc_Selection_Parallax_Width;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Height_Bloc_Parallax_Controle;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.tabControl;
import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;
import static jks.tools2d.parallax.editor.vue.edition.VE_Options.parallaxName;
import static jks.tools2d.parallax.editor.vue.edition.VE_Options.parallaxPath;

import org.apache.commons.lang3.StringUtils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.util.dialog.OptionDialogAdapter;
import com.kotcrab.vis.ui.util.dialog.Dialogs.OptionDialogType;
import com.kotcrab.vis.ui.widget.VisTextButton;

import jks.tools2d.filewatch.FileWatching_Image;
import jks.tools2d.libgdxutils.JksCheckBox;
import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.Vue_Edition;
import jks.tools2d.parallax.editor.vue.Vue_Selection;
import jks.tools2d.parallax.editor.vue.edition.data.Outside_Source;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Data;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_Saving;
import jks.tools2d.parallax.pages.WholePage_Model; 

public class VE_Center_ParallaxShow extends Table
{
	float decalX = 2 ; 
	float buttonSize ;
	
	Slider parallaxSpeedXSlider ; 
	Slider parallaxSpeedYSlider ; 
	
	VisTextButton resetSpeedX,resetSpeedY, resetPosition ; 
	
	public static VE_Center_ParallaxShow build(Object ref)
	{
		VE_Center_ParallaxShow show = null ;
		TextureAtlas atlas = null;
		
		if(ref instanceof TextureAtlas)
		{
			atlas = (TextureAtlas)ref ; 
			
			WholePage_Model page = new WholePage_Model(); 
			page.pageModel.atlasName = GVars_Vue_Edition.projectInfos.projectName + "." + FVars_Extensions.ATLAS ; 
			page.forceLoad(atlas);
			GVars_Vue_Edition.setPage(page) ; 
			
			show = new VE_Center_ParallaxShow() ;
		}
		else if(ref instanceof WholePage_Model)
		{
			WholePage_Model page = (WholePage_Model)ref ; 
			atlas = new TextureAtlas(new FileHandle(GVars_Vue_Edition.relativePath + "/" + page.pageModel.atlasName));
			GVars_Vue_Edition.setPage(page) ;
			
			show =  new VE_Center_ParallaxShow() ; 
		}
		else if(ref instanceof Project_Data)
		{
			Project_Data project = null;
			try
			{
				project = (Project_Data)ref ; 
				GVars_Vue_Edition.projectDatas = project ;
				
				buildOutsideValues() ; 
				
				if(project.saving.pageModel.atlasName != null)
					atlas = new TextureAtlas(new FileHandle(GVars_Vue_Edition.relativePath + "/" + project.saving.pageModel.atlasName));
				else
					atlas = new TextureAtlas() ; 
				
				GVars_Vue_Edition.setPage(project.saving) ; 
					
				show =  new VE_Center_ParallaxShow() ;
			}
			catch(Exception errors)
			{
				String errorMessage ;
				
				if(project == null)
				{
					
				}
				else
				{
					
				}
				
				Dialogs.showErrorDialog(GVars_UI.mainUi, "Loading not possible", "Impossible de trouv� l'atlas avec le path : " 
						+ GVars_Vue_Edition.relativePath + "/" + project.saving.pageModel.atlasName);
			}
		}
		else 
		{
			WholePage_Model page = new WholePage_Model(); 
			page.pageModel.atlasName = null ; 
			GVars_Vue_Edition.setPage(page) ; 
			show = new VE_Center_ParallaxShow() ;
		}
		
		GVars_Vue_Edition.atlas = atlas ; 
		
		return show ; 
	}
	
	private static void buildOutsideValues()
	{
		String errors = "" ; 
		
		if(GVars_Vue_Edition.projectDatas.outsideInfos != null)
		{
			for(Outside_Source outsideInfos : GVars_Vue_Edition.projectDatas.outsideInfos)
			{
				try 
				{
					TextureRegion region = new TextureRegion(new Texture(new FileHandle(outsideInfos.url))) ; 
					allImage.add(region) ;
					imageRef.put(region, new Position_Infos(false, outsideInfos.url,0)) ;

					new FileWatching_Image(outsideInfos.url,region) ; 
					outsideTextureReserve.put(outsideInfos.url, region) ; 
				}
				catch(Exception e)
				{
					errors += "Could not load " + outsideInfos.url + "\n" ; 
				}
			}
		}
		
		if(!StringUtils.isBlank(errors))
			Dialogs.showErrorDialog(GVars_UI.mainUi, "Loading not possible", errors);
	}
	
	VE_Center_ParallaxShow()
	{
		resize() ; 
		buildOptions() ; 
	}
	
	public void resize()
	{
		
		float sizeRation = (float)Gdx.graphics.getHeight() / (float)Gdx.graphics.getWidth()  ; 
		decalX = Gdx.graphics.getWidth()/30 ; 
//		parr_Size_X = (int)(((Gdx.graphics.getWidth()/3) * 2) - decalX * 2) ; 
		parr_Size_X = (int) (Gdx.graphics.getWidth() - size_Bloc_Selection_Parallax_Width- (decalX * 2)) ; 
		
//		parr_Size_Y = (parr_Size_X/16) * 9 ; 
		
		parr_Size_Y = (int) (parr_Size_X * sizeRation) ;
//		parr_Pos_X = size_Bloc_Selection_Parallax + (Gdx.graphics.getWidth() - size_Bloc_Selection_Parallax - parr_Size_X)/2; 
		parr_Pos_X = (int) (size_Bloc_Selection_Parallax_Width + decalX) ; 
		parr_Pos_Y = size_Height_Bloc_Parallax_Controle ;
		
		buttonSize = size_Height_Bloc_Parallax_Controle/1.5f ; 
	}
	
	
	public void buildOptions()
	{
		setBounds(size_Bloc_Selection_Parallax_Width, 1, size_Bloc_Parallax, Gdx.graphics.getHeight() - parr_Pos_Y/2 - parr_Size_Y);

		// TODO Effacer et mettre dans le UISKIN
		CheckBoxStyle checkBoxStyle = new CheckBoxStyle() ;
		checkBoxStyle.checkboxOff = baseSkin.newDrawable(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/button_play.png"));
		checkBoxStyle.checkboxOn = baseSkin.newDrawable(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/button_pause.png"));
		checkBoxStyle.font = baseSkin.getFont("default-font");
		
		CheckBoxStyle extendScreenStyle = new CheckBoxStyle() ;
		extendScreenStyle.checkboxOff = baseSkin.newDrawable(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/expand.png"));
		extendScreenStyle.checkboxOn = baseSkin.newDrawable(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/contract.png"));
		extendScreenStyle.font = baseSkin.getFont("default-font");
		
		JksCheckBox startStop = new JksCheckBox("",checkBoxStyle,false) ; 
		startStop.addListener(new ChangeListener() 
		{
			public void changed (ChangeEvent event, Actor actor) 
			{isPause = !startStop.isChecked();}
		});

		startStop.setSize(buttonSize, buttonSize);
		startStop.setPosition(parr_Size_X/2, size_Height_Bloc_Parallax_Controle/2 - buttonSize/2);
		
		
		JksCheckBox extendScreen = new JksCheckBox("",extendScreenStyle,false) ; 
		extendScreen.addListener(new ChangeListener() 
		{
			public void changed (ChangeEvent event, Actor actor) 
			{
				showParallaxFullScreen = extendScreen.isChecked();
				
				if(showParallaxFullScreen)
				{
					tabControl.setVisible(false);
					optionsControl.setVisible(false);
				}
				else
				{
					tabControl.setVisible(true);
					optionsControl.setVisible(true);
				}
			}
		});


		extendScreen.setSize(buttonSize, buttonSize);
		extendScreen.setPosition(
				this.getWidth() - extendScreen.getWidth() ,
				this.getHeight() - extendScreen.getHeight()/2);
		extendScreen.setChecked(false);
		
		Table speedSlider = new Table() ; 
		speedSlider.setSize(this.getWidth()/2 - buttonSize, buttonSize);
		speedSlider.setPosition(buttonSize/3, this.getHeight()/2 - speedSlider.getHeight()/2);
		
		
		parallaxSpeedXSlider = new Slider(-15, 15, 0.05f, false, GVars_UI.baseSkin) ; 
		parallaxSpeedXSlider.setValue(0) ; 
		parallaxSpeedXSlider.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer)
			{
				Vue_Edition.parallax_Heart.screenSpeedConstantX = parallaxSpeedXSlider.getValue() * 100 ; 
			}
		}) ; 
		
		resetSpeedX = new VisTextButton("X = 0") ; 
		resetSpeedX.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{
				parallaxSpeedXSlider.setValue(0) ; 
				Vue_Edition.parallax_Heart.screenSpeedConstantX = 0 ; 
				return true ;
			}

		}) ; 
		
		parallaxSpeedYSlider = new Slider(-20, 20, 0.2f, false, GVars_UI.baseSkin) ; 
		parallaxSpeedYSlider.setValue(0) ; 
		parallaxSpeedYSlider.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer)
			{
				Vue_Edition.parallax_Heart.screenSpeedConstantY = parallaxSpeedYSlider.getValue() * 100 ; 
			}
		}) ; 
		
		resetSpeedY = new VisTextButton("Y = 0") ; 
		resetSpeedY.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{
				parallaxSpeedYSlider.setValue(0) ; 
				Vue_Edition.parallax_Heart.screenSpeedConstantY = 0 ; 
				return true ;
			}
			
		}) ; 
		
		resetPosition = new VisTextButton("Reset position") ; 
		resetPosition.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{
				parallax_Heart.parallaxReader.resetPositions();
				return true ;
			}
			
		}) ; 
		
		speedSlider.add(new Label("Speed X",GVars_UI.baseSkin)) ; 
		speedSlider.add(parallaxSpeedXSlider) ; 
		speedSlider.add(resetSpeedX) ; 
		speedSlider.row() ; 
		speedSlider.add(new Label("Speed Y",GVars_UI.baseSkin)) ; 
		speedSlider.add(parallaxSpeedYSlider) ; 
		speedSlider.add(resetSpeedY) ; 
		speedSlider.row() ; 
		speedSlider.add(resetPosition).colspan(3) ; 
		
		
		this.addActor(startStop) ;
		this.addActor(speedSlider) ;
		this.addActor(extendScreen) ; 
	}
}