package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.getDefaults;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.setItems;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.sizeTabsBar;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Bloc_Selection;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Height_Bloc_Parallax_Controle;
import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart ;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.util.dialog.Dialogs.OptionDialog;
import com.kotcrab.vis.ui.util.dialog.Dialogs.OptionDialogType;
import com.kotcrab.vis.ui.util.dialog.OptionDialogAdapter;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.libgdxutils.JksTextureList;
import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.gvars.GVars_Ui;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_LoadingImages;
import jks.tools2d.parallax.heart.Gvars_Parallax; 

public class VE_Tab_TextureList_Adding extends Tab
{

	static public JksTextureList imageList ; 
	private Table mainTable ;
	private static final int divisonPower = 5 ; 
	float midButtonHeight ; 
	
	ImageButton button_removeData,button_addData ; 
	
	VE_Tab_TextureList_Adding()
	{
		super(false, false);
		mainTable = new Table() ; 
		buildTextureSelector() ; 
	}
	
	public void buildTextureSelector()
	{
		ScrollPane contentsPane;
		float buttonSize = size_Bloc_Selection/divisonPower; 
		midButtonHeight = buttonSize/2 ; 
		
		button_removeData = Utils_Interface.buildSquareButton("editor/interfaces/delete.png",buttonSize) ; 
		button_removeData.setSize(buttonSize, buttonSize);
		button_removeData.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{
				TextureRegion text = imageList.getSelected() ; 
				Position_Infos position = GVars_Vue_Edition.imageRef.get(text) ; 
				
				String message ; 
				
				if(position.fromAtlas)
					message= "Do you really want to delete this part of the atlas? You wont be able to add it back " ;
				else
					message = "Do you really want to delete this reference and all its occurences ? " ; 
				
					
				
				OptionDialog deleteDialog = Dialogs.showOptionDialog(GVars_Ui.mainUi, "Delete segment", message, OptionDialogType.YES_NO, new OptionDialogAdapter() 
				{
					@Override
					public void yes () 
					{
						delete(text) ;
						imageList.clearSelected();
						update();
						showButton(false) ; 
					}

					@Override
					public void no () 
					{}

					@Override
					public void cancel () 
					{}
				});
				
				// deleteDialog.setPosition(Mouse.getX(), Mouse.getY());
				return true ; 
			}
			
		}) ;
		
	
		button_addData = new ImageButton(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/button_add.png")) ; 
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
				showButton(true) ; 
//				size_Bloc_Selection
				button_removeData.setPosition(x , y + size_Height_Bloc_Parallax_Controle - midButtonHeight);
				button_addData.setPosition(x , y + size_Height_Bloc_Parallax_Controle - midButtonHeight);
			}
	
		};		
	
		setItems() ;
		
		contentsPane = new ScrollPane(imageList,baseSkin) ; 
		contentsPane.setFadeScrollBars(false);
		contentsPane.setWidth(size_Bloc_Selection);
		contentsPane.setHeight(Gdx.graphics.getHeight() - sizeTabsBar * 2);
		
		button_removeData.setVisible(false);
		button_addData.setVisible(false);
		
		mainTable.setSize(contentsPane.getWidth(),contentsPane.getHeight());
		mainTable.addActor(contentsPane);
		mainTable.addActor(button_removeData);
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
	
	public void update()
	{
		if(imageList.getItems() != null && imageList.getItems().size > 0)
		{
			showButton(true) ; 
		}
		else
		{
			showButton(false) ; 
		}
		
	}
	
	public void showButton(boolean show)
	{
		if(show)
		{
			button_removeData.setVisible(true);
			button_addData.setVisible(true);
		}
		else
		{
			button_removeData.setVisible(false);
			button_addData.setVisible(false);
		}
	}
	
	public static void delete(TextureRegion text) 
	{
		Utils_LoadingImages.removeFile(text);
	}
	
	@Override
	public String getTabTitle()
	{return "Adding new";}

	@Override
	public Table getContentTable()
	{
		update() ;	
		return mainTable;
	}
}