package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.getDefaults;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.setItems;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.sizeTabsBar;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Bloc_Selection_Parallax_Width;
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
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_Texture;
import jks.tools2d.parallax.heart.Gvars_Parallax; 

public class VE_Tab_TextureList_Adding extends Tab
{

	static public JksTextureList imageList ; 
	private Table mainTable ;
	private static final int divisonPower_small = 5 ; 
	private static final int divisonPower_large = 4 ; 
	
	float buttonSize_small,buttonSize_large ; 
	
	boolean inChangeselection = false ; 
	
	boolean baseButtonShow,switchButtonShow ; 
	
	ImageButton button_addData, button_changeData, button_removeData ; 
	ImageButton button_switchFor, button_cancel ;
	
	TextureRegion changingRegion ; 
	
	VE_Tab_TextureList_Adding()
	{
		super(false, false);
		mainTable = new Table() ; 
		buildTextureSelector() ; 
	}
	
	public void buildTextureSelector()
	{	
		init() ; 
		resize();
		
		buildBaseInteraction() ;
		buildSwitchInteraction() ;
		imageList = buildImageList() ; 
		setItems() ;
		
		ScrollPane scrollPane;
		scrollPane = new ScrollPane(imageList,baseSkin) ; 
		scrollPane.setFadeScrollBars(false);
		scrollPane.setWidth(size_Bloc_Selection_Parallax_Width);
		scrollPane.setHeight(Gdx.graphics.getHeight() - sizeTabsBar * 2);
		
			
		mainTable.setSize(scrollPane.getWidth(),scrollPane.getHeight());
		mainTable.addActor(scrollPane);
		mainTable.addActor(button_addData);	
		mainTable.addActor(button_changeData);	
		mainTable.addActor(button_removeData);
		
		mainTable.addActor(button_switchFor);
		mainTable.addActor(button_cancel);
	}

	private void init() 
	{
		//button_addData = new ImageButton(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/button_add.png")) ; 
		button_addData = Utils_Interface.buildSquareButton("editor/interfaces/button_add.png",buttonSize_small) ; 
		button_changeData = Utils_Interface.buildSquareButton("editor/interfaces/button_transform.png",buttonSize_small) ; 
		button_removeData = Utils_Interface.buildSquareButton("editor/interfaces/delete.png",buttonSize_small) ; 
	
		button_switchFor = Utils_Interface.buildSquareButton("editor/interfaces/button_transform.png",buttonSize_large) ;
		button_cancel = Utils_Interface.buildSquareButton("editor/interfaces/delete.png",buttonSize_large) ;
	}

	
	private JksTextureList buildImageList() 
	{
		return new JksTextureList(baseSkin,size_Bloc_Selection_Parallax_Width,size_Bloc_Selection_Parallax_Width/2) 
		{
			@Override
			public void choiceAction(TextureRegion item)
			{
				if(!baseButtonShow && !switchButtonShow )
					showBaseButton(true);
			}
			
			@Override
			public void drawOnSelected(Batch batch, float x, float y, float width, float itemHeight)
			{
				if(y > Gdx.graphics.getHeight() - size_Bloc_Selection_Parallax_Width/2.3f)
				{
					hideAll(true) ;
				}
				else
				{
					hideAll(false) ; 
				}
					
				
				button_addData.setPosition(x + buttonSize_small * 0.5f, y + size_Bloc_Selection_Parallax_Width/4 - buttonSize_small/2);
				button_changeData.setPosition(x + buttonSize_small * 2.0f, y + size_Bloc_Selection_Parallax_Width/4 - buttonSize_small/2);
				button_removeData.setPosition(x + buttonSize_small * 3.5f, y + size_Bloc_Selection_Parallax_Width/4 - buttonSize_small/2);
				
				button_switchFor.setPosition(x + buttonSize_large * 0.5f, y + size_Bloc_Selection_Parallax_Width/4 - buttonSize_large/2);
				button_cancel.setPosition(x + buttonSize_large * 2.0f, y + size_Bloc_Selection_Parallax_Width/4 - buttonSize_large/2);
			}
			
		
	
		};			
	}

	private void buildBaseInteraction() 
	{
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
		
		button_changeData.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{	

				changingRegion = imageList.getSelected() ; 
				showBaseButton(false) ; 
				showSwitchButton(true) ; 

				return true ; 
			}
			
		}) ;
		
	
		button_removeData.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{
				TextureRegion text = imageList.getSelected() ; 
				Position_Infos position = GVars_Vue_Edition.imageRef.get(text) ; 
				
				String message ; 
				
				if(position.fromAtlas)
					message= "Do you really want to delete this part of the atlas? You wont be able to add it back "
							+ "\n YES Delete from the parallax AND the selection"
							+ "\n NO  Delete from ONLY the parallax" ;
				else
					message = "Do you really want to delete this reference and all its occurences ? "
							+ "\n YES Delete from the parallax AND the selection"
							+ "\n NO  Delete from ONLY the parallax" ;
				
					
				OptionDialog deleteDialog = Dialogs.showOptionDialog(GVars_Ui.mainUi, "Delete segment", message, OptionDialogType.YES_NO_CANCEL, new OptionDialogAdapter() 
				{
					@Override
					public void yes () 
					{
						delete(text,true) ;
						imageList.clearSelected();
						update();
						showBaseButton(false) ; 
					}

					@Override
					public void no () 
					{
						delete(text,false) ;
						update();
					}

					@Override
					public void cancel () 
					{}
				});
				
				return true ; 
			}
			
		}) ;
		
		button_addData.setVisible(false);
		button_changeData.setVisible(false);
		button_removeData.setVisible(false);	
	}
	
	private void buildSwitchInteraction() 
	{
		button_switchFor.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{
				Utils_Texture.changeTextureInPage(changingRegion, imageList.getSelected());
				showBaseButton(true) ; 
				showSwitchButton(false) ; 
				
				return true ; 
			}
			
		}) ;
		
		button_cancel.addListener(new InputListener()
		{

			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{
				showBaseButton(true) ; 
				showSwitchButton(false) ; 
				
				return true ; 
			}
			
		}) ;
		
		button_switchFor.setVisible(false);
		button_cancel.setVisible(false);
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
			showBaseButton(true) ; 
		}
		else
		{
			showBaseButton(false) ; 
			showSwitchButton(false) ; 
		}
		
	}
	
	public void hideAll(boolean show)
	{
		if(show)
		{
			button_addData.setVisible(false);
			button_changeData.setVisible(false);
			button_removeData.setVisible(false);
			button_switchFor.setVisible(false);
			button_cancel.setVisible(false);
		}
		else
		{
			showBaseButton(baseButtonShow) ; 
			showSwitchButton(switchButtonShow) ; 
		}
		
	}
	
	public void showBaseButton(boolean show)
	{
		baseButtonShow = show ; 
		button_addData.setVisible(show);
		button_changeData.setVisible(show);
		button_removeData.setVisible(show);
	}
	
	public void showSwitchButton(boolean show)
	{
		switchButtonShow = show ; 
		button_switchFor.setVisible(show);
		button_cancel.setVisible(show);
		if(!show)
			changingRegion = null ; 
	}
	
	
	
	public static void delete(TextureRegion text, boolean hardClean) 
	{
		Utils_LoadingImages.removeFile(text, hardClean);
	}
	
	public void resize()
	{
		buttonSize_small = size_Bloc_Selection_Parallax_Width/divisonPower_small; 
		buttonSize_large = size_Bloc_Selection_Parallax_Width/divisonPower_large; 
		
		button_addData.setSize(buttonSize_small, buttonSize_small);
		button_changeData.setSize(buttonSize_small, buttonSize_small);
		button_removeData.setSize(buttonSize_small, buttonSize_small);	
		
		button_switchFor.setSize(buttonSize_large, buttonSize_large);	
		button_cancel.setSize(buttonSize_large, buttonSize_large);	
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