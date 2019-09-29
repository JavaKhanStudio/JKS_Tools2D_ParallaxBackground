package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.vue.edition.VE_Options.parallaxName;
import static jks.tools2d.parallax.editor.vue.edition.VE_Options.parallaxPath;
import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;

import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.util.dialog.Dialogs.OptionDialogType;
import com.kotcrab.vis.ui.util.dialog.OptionDialogAdapter;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_Ui;
import jks.tools2d.parallax.editor.vue.Vue_Selection;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_Saving; 

public class VE_Tab_ParallaxConfig_Configuration extends Tab
{

	private Table mainTable ; 
	VisCheckBox repeatOnX, repeatOnY ; 
	VisCheckBox vSynch ;
	
	public IntSpinnerModel nbSampleSpinner ; 
	Spinner nbSampleSpinnerBody ; 

	VisTextButton changeAtlas, showOptionDialog, packUpTextures ; 
	VisLabel atlasNameLabel ;  
	
	
	VE_Tab_ParallaxConfig_Configuration()
	{
		super(false, false);
		mainTable = new Table() ; 
		buildTextureSelector() ; 
	}
	
	public void buildTextureSelector()
	{
		mainTable = new Table() ; 
		
		repeatOnX = new VisCheckBox("Repeat On X") ;
		repeatOnX.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				parallax_Heart.parallaxReader.setRepeatOnX(repeatOnX.isChecked());
				parallax_Heart.parallaxReader.resetPositions();
			}
		}) ; 
		
		repeatOnY = new VisCheckBox("Repeat On Y") ;
		repeatOnY.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				parallax_Heart.parallaxReader.setRepeatOnY(repeatOnY.isChecked());
				parallax_Heart.parallaxReader.resetPositions();
			}
		}) ; 
		
		atlasNameLabel = new VisLabel() ; 
		changeAtlas = new VisTextButton("Change Atlas");
		changeAtlas.addListener(new ChangeListener() 
		{
			@Override
			public void changed (ChangeEvent event, Actor actor) 
			{
				Dialogs.showOptionDialog(GVars_Ui.mainUi, "option dialog", "Do you want to save the project before leaving?", OptionDialogType.YES_NO_CANCEL, new OptionDialogAdapter() 
				{
					@Override
					public void yes () 
					{
						Utils_Saving.saving_Parallax_Project(parallaxPath.getText(), parallaxName.getText());
						GVars_Heart_Editor.changeVue(new Vue_Selection(),true) ; 
					}

					@Override
					public void no () 
					{
						GVars_Heart_Editor.changeVue(new Vue_Selection(),true) ; 
					}

					@Override
					public void cancel () 
					{}
				});
			}
		});
		
		
		showOptionDialog = new VisTextButton("Return to selection");
		showOptionDialog.addListener(new ChangeListener() 
		{
			@Override
			public void changed (ChangeEvent event, Actor actor) 
			{
				Dialogs.showOptionDialog(GVars_Ui.mainUi, "option dialog", "Do you want to save the project before leaving?", OptionDialogType.YES_NO_CANCEL, new OptionDialogAdapter() 
				{
					@Override
					public void yes () 
					{
						Utils_Saving.saving_Parallax_Project(parallaxPath.getText(), parallaxName.getText());
						GVars_Heart_Editor.changeVue(new Vue_Selection(),true) ; 
					}

					@Override
					public void no () 
					{
						GVars_Heart_Editor.changeVue(new Vue_Selection(),true) ; 
					}

					@Override
					public void cancel () 
					{}
				});
			}
		});
		

		packUpTextures = new VisTextButton("Pack external texture for moving");
		packUpTextures.addListener(new ChangeListener() 
		{
			@Override
			public void changed (ChangeEvent event, Actor actor) 
			{
				Dialogs.showOptionDialog(GVars_Ui.mainUi, "option dialog", "Are you sure you want to pack ? \nTexture will be put in : \"packing_" + parallaxName.getText() + "\"" , OptionDialogType.YES_NO, new OptionDialogAdapter() 
				{
					@Override
					public void yes () 
					{
						Utils_Saving.packTextures() ; 						
					}

					@Override
					public void no () 
					{
					}

					@Override
					public void cancel () 
					{}
				});
			}
		});
		
		vSynch = new VisCheckBox("VSynch") ;
		vSynch.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				Vue_Edition.isVSynch = vSynch.isChecked() ;
				Gdx.graphics.setVSync(vSynch.isChecked());
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
		
		mainTable.add(new VisLabel("-- Configuration --")).colspan(2).row();
		mainTable.add(repeatOnX) ; 
		mainTable.add(repeatOnY) ; 
		mainTable.row();
		
		mainTable.add(atlasNameLabel) ; 
		mainTable.add(changeAtlas) ;
		mainTable.row();
		
		mainTable.add(packUpTextures).colspan(2) ;
		mainTable.row();
		
		mainTable.add(new VisLabel("-- Parameter --")).colspan(2).row();
		mainTable.add(showOptionDialog).colspan(2) ;
		mainTable.row();
		
		/*// must check if it works
		mainTable.add(new VisLabel("-- Visuel --")).colspan(2) ;
		mainTable.row() ; 
		mainTable.add(vSynch) ; 
		mainTable.add(nbSampleSpinnerBody) ; 
		*/

	}
	
	public void update()
	{
		repeatOnX.setChecked(parallax_Heart.parallaxReader.isRepeatOnX());
		repeatOnY.setChecked(parallax_Heart.parallaxReader.isRepeatOnY());
		String atlasName = "Current atlas : " ; 
		
		if(StringUtils.isEmpty(parallax_Heart.getAtlasName()))
			atlasName += "none selected" ; 
		else
			atlasName += parallax_Heart.getAtlasName() ; 
		
		atlasNameLabel.setText(atlasName);
		vSynch.setChecked(Vue_Edition.isVSynch);
	}

	@Override
	public String getTabTitle()
	{return "Configuration";}

	@Override
	public Table getContentTable()
	{
		update();
		return mainTable;
	}	
	
}