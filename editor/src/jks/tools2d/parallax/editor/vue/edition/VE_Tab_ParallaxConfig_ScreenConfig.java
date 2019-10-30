package jks.tools2d.parallax.editor.vue.edition;

import java.util.ArrayList;
import java.util.HashMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_UI; 

public class VE_Tab_ParallaxConfig_ScreenConfig extends Tab
{
	// TODO
	private Table mainTable ; 
	
	VisLabel graphicLabel,resolutionLabel,fpsLabel ;
	VisLabel leftDecalX , rightDecalX ; 
	VisCheckBox vSynchCheckBox ; 
	VisCheckBox fullScreenCheckBox ;
	SelectBox<String> selectBox_Resolution ; 
	SelectBox<String> selectBox_FPS ;
	
	HashMap<String,DisplayMode> displayMap ;
	ArrayList<String> displayList ;
	
	Cell<VisLabel> leftDecalXCell, rightDecalXCell ; 
	
	VE_Tab_ParallaxConfig_ScreenConfig()
	{
		super(false, false);
		mainTable = new Table() ; 
		buildTextureSelector() ; 
	}
	
	public Table buildGraphicModifier()
	{
		Table graphicTable = new Table(); 
		leftDecalX = new VisLabel("    ") ; rightDecalX = new VisLabel("    ") ;
		graphicLabel = new VisLabel("Graphics",GVars_UI.labelStyle_Title) ;
		graphicLabel.setAlignment(Align.center);
		resolutionLabel = new VisLabel("Resolution:",GVars_UI.labelStyle_Second) ; 
		fpsLabel = new VisLabel("Frame Per Sec:",GVars_UI.labelStyle_Second) ; 
		
		selectBox_Resolution = buildResolutionBox() ; 	
		selectBox_FPS = buildFpsBox() ;
		
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
		apply.getLabel().setStyle(GVars_UI.labelStyle_Title);
		apply.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{applyNewResolution() ;}
		}) ; 
		
		graphicTable.add(graphicLabel).colspan(4) ;
		graphicTable.row() ; 
		
		leftDecalXCell = graphicTable.add(leftDecalX) ; 
		graphicTable.add(resolutionLabel).align(Align.left).expandX() ; 
		graphicTable.add(selectBox_Resolution).align(Align.right) ;
		rightDecalXCell = graphicTable.add(rightDecalX) ; 
		graphicTable.row() ; 
		
		graphicTable.add() ; 
		graphicTable.add(fpsLabel).align(Align.left) ; 
		graphicTable.add(selectBox_FPS).align(Align.right).expandX() ;
		graphicTable.row() ;
		
		graphicTable.add() ; 
		graphicTable.add(fullScreenCheckBox).colspan(2).align(Align.left).expandX() ; 
		graphicTable.add() ; 
		graphicTable.row() ;
		
		graphicTable.add() ; 
		graphicTable.add(vSynchCheckBox).colspan(2).align(Align.left).expandX() ; 
		graphicTable.add() ; 
		graphicTable.row() ;
		
		graphicTable.add(apply).colspan(4).align(Align.center) ; 
		return graphicTable ; 
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
	}
	
	
	public SelectBox<String> buildResolutionBox() 
	{
		buildDisplayList() ; 
		SelectBox<String> selectBox = new SelectBox<String>(GVars_UI.baseSkin);
		String[] resolutions = new String[displayMap.size()];
		
		int a = 0; 
		for(String value : displayList)
		{resolutions[a++] = value ;}
		
		selectBox.setItems(resolutions);
		
		return selectBox ;
	}
	
	public SelectBox<String> buildFpsBox() 
	{
		SelectBox<String> returning = new SelectBox<String>(GVars_UI.baseSkin);
		
		String[] fpsChoice = new String[2];
		fpsChoice[0] = "30" ; fpsChoice[1] = "60" ; 
		returning.setItems(fpsChoice);
		
		return returning ; 
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
	
	public void buildTextureSelector()
	{
		final VisTable container = new VisTable();
		TabbedPane tabbedPane = new TabbedPane(GVars_UI.baseSkin.get("default", TabbedPaneStyle.class));
		tabbedPane.setAllowTabDeselect(false);
		tabbedPane.addListener(new TabbedPaneAdapter()
		{
			@Override
			public void switchedTab(Tab tab)
			{
				container.clearChildren();
				container.add(tab.getContentTable()).expand().fill();
			}
		});
		

		mainTable = new Table() ; 
		mainTable.add(tabbedPane.getTable()).expandX().fillX();
		mainTable.row();
		mainTable.add(container).expand().fill();
		
	}

	@Override
	public String getTabTitle()
	{return "Transfert";}

	@Override
	public Table getContentTable()
	{
		return mainTable;
	}	
}