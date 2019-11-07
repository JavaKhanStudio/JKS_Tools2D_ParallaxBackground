package jks.tools2d.parallax.editor.vue.edition;

import java.util.ArrayList;
import java.util.HashMap;

import org.lwjgl.glfw.GLFW;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_UI; 

public class VE_Tab_Meta_ConfigApplication extends Tab
{

	private Table mainTable ; 
	VisCheckBox vSynchCheckBox, fullScreenCheckBox ;
	
	public IntSpinnerModel nbSampleSpinner ; 
	
	Spinner nbSampleSpinnerBody ; 
	
	SelectBox<String> selectBox_Resolution ; 
	HashMap<String,DisplayMode> displayMap ;
	ArrayList<String> displayList ;
	
	VE_Tab_Meta_ConfigApplication()
	{
		super(false, false);
		mainTable = new Table() ; 
		buildTextureSelector() ; 
	}
	
	public void buildTextureSelector()
	{
		mainTable = new Table() ; 
		
		
		vSynchCheckBox = new VisCheckBox("VSynch") ;
		vSynchCheckBox.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				Vue_Edition.isVSynch = vSynchCheckBox.isChecked() ;
				Gdx.graphics.setVSync(vSynchCheckBox.isChecked());
			}
		}) ; 
		
		nbSampleSpinner = new IntSpinnerModel(0,0,100); 
		nbSampleSpinnerBody = new Spinner("Nb Sample", nbSampleSpinner);
		nbSampleSpinnerBody.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{
				return true ;
			}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				System.out.println("chaning value");
				GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, nbSampleSpinner.getValue());
			//	Gdx.graphics.getBufferFormat().samples = nbSampleSpinner.getValue() ; 
			}
		}) ; 
		
		
//		graphicLabel = new VisLabel("Graphics",GVars_UI.labelStyle_OptionsTitle) ;
//		graphicLabel.setAlignment(Align.center);
//		resolutionLabel = new VisLabel("Resolution:",GVars_UI.labelStyle_Second) ; 
//		fpsLabel = new VisLabel("Frame Per Sec:",GVars_UI.labelStyle_Second) ; 
//		
//		selectBox_Resolution = buildResolutionBox() ; 	
//		selectBox_FPS = buildFpsBox() ;
		
		vSynchCheckBox = new VisCheckBox("Is VSynch") ; 
		vSynchCheckBox.getLabel().setStyle(GVars_UI.labelStyle_Second);
		
		fullScreenCheckBox = new VisCheckBox("Full screen") ; 
		fullScreenCheckBox.getLabel().setStyle(GVars_UI.labelStyle_Second);
		fullScreenCheckBox.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				setMaxResolution(fullScreenCheckBox.isChecked()) ;
			}

		}) ; 
		
		TextButton apply = new TextButton("Apply",GVars_UI.baseSkin) ;
		apply.getLabel().setStyle(GVars_UI.labelStyle_OptionsTitle);
		apply.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{applyNewResolution() ;}
		}) ; 


//		mainTable.add(new VisLabel("-- Configuration --")).colspan(2).row();
//		mainTable.add(graphicLabel).colspan(4) ;
//		mainTable.row() ; 
//		
//		mainTable.add(resolutionLabel).align(Align.left).expandX() ; 
//		mainTable.add(selectBox_Resolution).align(Align.right) ;
//		mainTable.row() ; 
//		
//		mainTable.add() ; 
//		mainTable.add(selectBox_FPS).align(Align.right).expandX() ;
//		mainTable.row() ;
		
		mainTable.add() ; 
		mainTable.add(fullScreenCheckBox).colspan(2).align(Align.left).expandX() ; 
		mainTable.add() ; 
		mainTable.row() ;
		
		mainTable.add() ; 
		mainTable.add(vSynchCheckBox).colspan(2).align(Align.left).expandX() ; 
		mainTable.add() ; 
		mainTable.row() ;
		
		mainTable.add(apply).colspan(4).align(Align.center) ; 


	}
	
	private void setMaxResolution(boolean settingMax) 
	{
		if(settingMax)
		{
			selectBox_Resolution.setSelectedIndex(selectBox_Resolution.getItems().size - 1);
			selectBox_Resolution.setDisabled(true);
		}
		else
		{
			selectBox_Resolution.setDisabled(false);
		}
	}
	
	public void applyNewResolution()
	{
		String resolution = selectBox_Resolution.getSelected() ;
		int width = Integer.parseInt(resolution.substring(0, resolution.indexOf("x"))) ; 
		int height = Integer.parseInt(resolution.substring(resolution.indexOf("x") + 1,resolution.length())) ; 
		
		Gdx.graphics.setVSync(vSynchCheckBox.isChecked());
		Monitor currMonitor = Gdx.graphics.getMonitor();
		
		if(fullScreenCheckBox.isChecked())
		{	
			
			DisplayMode displayMode = Gdx.graphics.getDisplayMode(currMonitor);
			System.out.println(displayMode);
			if(!Gdx.graphics.setFullscreenMode(displayMode)) 
			{}	
		}
		else
		{
			Gdx.graphics.setWindowedMode(width, height);
			Lwjgl3Graphics g = (Lwjgl3Graphics) Gdx.graphics;
			DisplayMode mode = g.getDisplayMode();
			Lwjgl3Window window = g.getWindow();
	        window.setPosition(mode.width / 2 - g.getWidth() / 2, mode.height / 2 - g.getHeight() / 2);	
		}

		GVars_UI.resize();
		GVars_Heart_Editor.vue.resize(width, height);
		
		//DO STUFF
		
		
	}
	
	public void buildDisplayList()
	{
		DisplayMode[] modes = Lwjgl3ApplicationConfiguration.getDisplayModes();
		displayMap = new HashMap<String,DisplayMode>() ;
		displayList = new ArrayList<>() ;
		
		for(DisplayMode mode : modes)
		{
			if(mode.width >= 800 
					&& mode.refreshRate == 60)
			{
				float ratio = (float)mode.width/(float)mode.height ; 
				if(ratio < 1.778 && ratio > 1.7)
				{
					String displaySize = mode.width + "x" + mode.height ; 
					displayMap.put(displaySize, mode) ; 
					displayList.add(displaySize) ;
				}	
			}
		} 
	}
	
	public void resize()
	{
		int decalX = Gdx.graphics.getWidth()/40; 
//		leftDecalXCell.minWidth(decalX) ;
//		rightDecalXCell.minWidth(decalX) ; 
//		this.invalidate();
	}
	
	public void update()
	{
		
		vSynchCheckBox.setChecked(Vue_Edition.isVSynch);
	}

	@Override
	public String getTabTitle()
	{return "Application";}

	@Override
	public Table getContentTable()
	{
		update();
		return mainTable;
	}	
	
}