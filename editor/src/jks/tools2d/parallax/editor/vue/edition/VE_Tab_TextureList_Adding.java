package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart ;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.*;

import java.util.ArrayList;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
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
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;
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
				System.out.println("test me good");
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
				Position_Infos infoss = GVars_Vue_Edition.imageRef.get(imageList.getSelected()) ; 
				GVars_Vue_Edition.imageRef.put(text, GVars_Vue_Edition.imageRef.get(imageList.getSelected())) ;
				ParallaxLayer layer = new ParallaxLayer(
						text,
						true, 
						Gvars_Parallax.getWorldWidth(), 
						new Vector2(.01f,.01f),
						1) ; 
				
				layer.setFlipX(getDefaults().defaultModel.flipX,getDefaults().defaultModel.flipX);
				layer.setFlipY(getDefaults().defaultModel.flipY,getDefaults().defaultModel.flipY);
				layer.setDecalPercentX(getDefaults().defaultModel.decal_X_Ratio);
				layer.setDecalPercentY(getDefaults().defaultModel.decal_Y_Ratio);
				layer.setSizeRatio(getDefaults().defaultModel.sizeRatio);
				layer.setSpeedAtRest(getDefaults().defaultModel.speed);
				layer.getParallaxSpeedRatio().x = getDefaults().defaultModel.parallaxScalingSpeedX ; 
				layer.getParallaxSpeedRatio().y = getDefaults().defaultModel.parallaxScalingSpeedY ; 
				parallax_Heart.parallaxPage.layers.add(layer);
				
				if(getDefaults().increment)
					getDefaults().doIncrement(true) ; 
				
				GVars_Vue_Edition.selectLayer(layer) ; 
				
				ArrayList<ParallaxLayer> linkList = textureLink.get(text) ; 
				if(linkList == null)
				{
					linkList = new ArrayList<ParallaxLayer>() ; 
					textureLink.put(text, linkList) ; 
				}
				
				linkList.add(layer) ; 
				
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

	

	@Override
	public String getTabTitle()
	{return "Adding new";}

	@Override
	public Table getContentTable()
	{return mainTable;}
}