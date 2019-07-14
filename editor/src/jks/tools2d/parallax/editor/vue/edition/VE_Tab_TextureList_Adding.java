package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart ;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.getDefaults;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.setItems;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.sizeTabsBar;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.size_Bloc_Selection;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.textureLink;

import java.util.ArrayList;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.libgdxutils.JksTextureList;
import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition;
import jks.tools2d.parallax.heart.Gvars_Parallax; 

public class VE_Tab_TextureList_Adding extends Tab
{

	static public JksTextureList imageList ; 
	private Table mainTable ;
	
	
	VE_Tab_TextureList_Adding()
	{
		super(false, false);
		mainTable = new Table() ; 
		buildTextureSelector() ; 
	}
	
	public void buildTextureSelector()
	{
		ScrollPane contentsPane;
		float buttonSize = size_Bloc_Selection/4; 
		ImageButton button_changeData = new ImageButton(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/button_transform.png")) ; 
		button_changeData.setSize(buttonSize, buttonSize);
		button_changeData.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{
				TextureRegion text = imageList.getSelected() ; 
				System.out.println(GVars_Vue_Edition.textureLink.size());
				
				return true ; 
			}
			
		}) ;
		
		ImageButton button_addData = new ImageButton(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/button_add.png")) ; 
		button_addData.setSize(buttonSize, buttonSize);
		button_addData.addListener(new InputListener()
		{

			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{
				TextureRegion text = imageList.getSelected() ; 
				GVars_Vue_Edition.imageRef.put(text, GVars_Vue_Edition.imageRef.get(imageList.getSelected())) ;
				ParallaxLayer layer = new ParallaxLayer(
						text,
						true, 
						Gvars_Parallax.getWorldWidth(), 
						.01f,.01f,
						1) ; 

				layer.setUpEverything(getDefaults().defaultModel);
				addItem(layer) ; 
				
				return true ; 
			}
			
		}) ;
		
		imageList = new JksTextureList(baseSkin,size_Bloc_Selection,size_Bloc_Selection/2) 
		{
			@Override
			public void choiceAction(TextureRegion item)
			{
				
			}
			
			@Override
			public void drawOnSelected(Batch batch, float x, float y, float width, float itemHeight)
			{
				button_changeData.setPosition(x + buttonSize/2, y + buttonSize/2);
				button_addData.setPosition(x + buttonSize * 2.f, y + buttonSize/2);
			}
	
		};		
	
		setItems() ;
		
		contentsPane = new ScrollPane(imageList,baseSkin) ; 
		contentsPane.setFadeScrollBars(false);
		contentsPane.setWidth(size_Bloc_Selection);
		contentsPane.setHeight(Gdx.graphics.getHeight() - sizeTabsBar * 2);
		
		mainTable.setSize(contentsPane.getWidth(),contentsPane.getHeight());
		mainTable.addActor(contentsPane);
		mainTable.addActor(button_changeData);
		mainTable.addActor(button_addData);		
	}

	public static void addItem(ParallaxLayer layer)
	{
		if(getDefaults().addInFront)
			addItem(layer,-1) ; 
		else
			addItem(layer,0) ; 
	}
	
	public static void addItem(ParallaxLayer layer, int position)
	{
		if(position == -1)
			parallax_Heart.parallaxReader.layers.add(layer);
		else
			parallax_Heart.parallaxReader.layers.add(position,layer);
		
		if(getDefaults().increment)
			getDefaults().doIncrement(true) ; 
		
		GVars_Vue_Edition.selectLayer(layer) ; 
		
		GVars_Vue_Edition.addToLinks(layer);
	}
	

	@Override
	public String getTabTitle()
	{return "Adding new";}

	@Override
	public Table getContentTable()
	{return mainTable;}
}