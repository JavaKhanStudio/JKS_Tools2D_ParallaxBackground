package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.*;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;

import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.editor.gvars.GVars_Ui;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_Saving;

public class VE_Options extends Table
{
	float decal = 3 ; 
	float size = 50 ;
	float textWidth, pathWidth,  textHeight, formatWidth ;
	
	public static TextField parallaxPath,parallaxName  ; 
	public static VisCheckBox  formatLibGDX,formatJson,forceExport ; 
	
	ImageButton savingExport, savingProject ;
	
	VE_Options()
	{
		textWidth = Gdx.graphics.getWidth()/4  ;
		pathWidth = textWidth * 2.f ; 
		textHeight = 22 ;
		
		savingProject = Utils_Interface.buildSquareButton("editor/interfaces/saveProject.png",size) ; 
		savingProject.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				Utils_Saving.saving_Parallax_Project(parallaxPath.getText(), parallaxName.getText());
			}
			
		}) ;
		
		savingExport = Utils_Interface.buildSquareButton("editor/interfaces/saveParallax.png",size) ; 
		savingExport.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				Utils_Saving.saving_Parallax(parallaxPath.getText(), parallaxName.getText());
			}
			
		}) ;
		
		
		savingProject.setBounds(Gdx.graphics.getWidth() - size * 3, Gdx.graphics.getHeight() - (size + decal) * 1 , size, size);
		savingExport.setBounds(Gdx.graphics.getWidth() - size * 3, Gdx.graphics.getHeight() - (size + decal) * 2, size, size);
	
		
		formatLibGDX = new VisCheckBox("LibGDX") ;
		formatLibGDX.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}		
		}) ; 
		
		formatLibGDX.setChecked(true);
		
		formatJson = new VisCheckBox("JSON") ;
		formatJson.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}		
		}) ; 
		
		forceExport = new VisCheckBox("F.Export") ;
		forceExport.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
		}) ; 
		
		// TABLES //
		Table projectPathTable = new Table() ; 
		projectPathTable.setBounds(savingProject.getX() - pathWidth - decal, savingProject.getY() + textHeight/2 - decal, pathWidth, textHeight);
		Table projectNameTable = new Table() ; 
		projectNameTable.setBounds(savingExport.getX() - textWidth - decal, savingExport.getY() + textHeight/2 - decal, textWidth, textHeight);
		
		parallaxPath = new TextField("", GVars_Ui.baseSkin)
		{
			@Override
			public float getPrefWidth()
			{return super.getPrefWidth() * 3;}
		}; 
		
		ImageButton pathSelection = Utils_Interface.buildSquareButton("editor/interfaces/open-folder.png",size - decal * 2) ; 
		
		
		projectPathTable.add(new VisLabel("Project Path : "));
		projectPathTable.add(parallaxPath);
		projectPathTable.add(pathSelection);
		
	
		parallaxName = new TextField("", GVars_Ui.baseSkin) ; 
		projectNameTable.add(new VisLabel("Project Name : "));
		projectNameTable.add(parallaxName).right() ;
		
		
		Table formatTable = new Table() ; 
		formatTable.setBounds(savingExport.getWidth() +  savingExport.getX(), savingExport.getY(), 100, savingExport.getHeight());
		
		formatTable.add(formatLibGDX).left().row() ; 
		formatTable.add(formatJson).left().row();
		formatTable.add(forceExport).left() ;
		
				
		setInfos() ; 
		
		this.addActor(savingProject); 
		this.addActor(savingExport); 
		this.addActor(projectPathTable);
		this.addActor(projectNameTable);
		this.addActor(formatTable); 
	}
	
	public void update ()
	{
		
	}
	
	public void setInfos()
	{
		parallaxPath.setText(projectInfos.projectPath);
		parallaxName.setText(projectInfos.projectName);
		
	}
}