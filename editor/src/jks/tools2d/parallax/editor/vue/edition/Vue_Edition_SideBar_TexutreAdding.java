package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.allImage;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.*;

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
import jks.tools2d.parallax.heart.Parallax_Heart; 

public class Vue_Edition_SideBar_TexutreAdding extends Tab
{

	Vue_Edition_SideBar_TexutreAdding()
	{
		super(false, false);
		mainTable = new Table() ; 
		buildTextureSelector() ; 
	}
	
	private JksTextureList imageList ; 
	private Table mainTable ; 
	
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
				ParallaxLayer layer = new ParallaxLayer(
						text,
						Parallax_Heart.worldWidth, 
						new Vector2(.01f,.01f), 
						1,
						true) ; 
				
				
				Parallax_Heart.parallaxMainPage.layers.add(layer);
				
				GVars_Vue_Edition.selectLayer(layer) ; 
				
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
//				button_changeData.draw(batch, 1);
			}
	
		};		
		
		TextureRegion[] stockArr = new TextureRegion[allImage.size()];
		for(int x=0 ; x < allImage.size() ; x++)
		{
			stockArr[x] = allImage.get(x) ; 
		}

		imageList.setItems(stockArr);
		
		contentsPane = new ScrollPane(imageList,baseSkin) ; 
		contentsPane.setFadeScrollBars(false);
		contentsPane.setWidth(size_Bloc_Selection);
		contentsPane.setHeight(Gdx.graphics.getHeight() - sizeTabsBar);
		
		mainTable.setSize(contentsPane.getWidth(),contentsPane.getHeight());
		mainTable.addActor(contentsPane);
		mainTable.addActor(button_changeData);
		mainTable.addActor(button_addData);
		
	}

	@Override
	public String getTabTitle()
	{
		return "Add texture";
	}

	@Override
	public Table getContentTable()
	{
		return mainTable;
	}
}