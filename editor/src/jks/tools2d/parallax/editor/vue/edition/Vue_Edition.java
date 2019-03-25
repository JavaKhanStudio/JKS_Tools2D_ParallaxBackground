package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.allImage;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.imageRef;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.isPause;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.parr_Pos_X;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.parr_Pos_Y;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.parr_Size_X;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.parr_Size_Y;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.screenSize;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.screenSpeed;
import static jks.tools2d.parallax.heart.Parallax_Heart.batch;
import static jks.tools2d.parallax.heart.Parallax_Heart.worldCamera;

import java.util.ArrayList;
import java.util.HashMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.Gdx2DPixmap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ScreenUtils;

import jks.tools2d.libgdxutils.Utils_Scene2D;
import jks.tools2d.parallax.editor.gvars.GVars_Ui;
import jks.tools2d.parallax.editor.inputs.EditorInputProcessus;
import jks.tools2d.parallax.editor.inputs.GVars_Inputs;
import jks.tools2d.parallax.editor.vue.model.AVue_Model;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.WholePage_Model; 

public class Vue_Edition extends AVue_Model 
{

	private TextureAtlas atlas ; 

	public void preload()
	{
		Parallax_Heart.init(screenSize,new AssetManager());
		GVars_Vue_Edition.buildSizes();
	}
	
	public Vue_Edition(TextureAtlas atlas)
	{
		preload() ; 
		this.atlas = atlas ;
		new Vue_Edition_Center_ParallaxShow(atlas) ; 
	}

	public Vue_Edition(WholePage_Model parallax)
	{
		preload() ; 
		Parallax_Heart.manager.load(parallax.pageModel.atlasPath, TextureAtlas.class);
		Parallax_Heart.manager.finishLoadingAsset(parallax.pageModel.atlasPath);	
		TextureAtlas atlas = new TextureAtlas(parallax.pageModel.atlasPath);
		
		this.atlas = atlas ;
		new Vue_Edition_Center_ParallaxShow(parallax) ; 
	}


	@Override
	public void init()
	{
		allImage = new ArrayList<>() ; 
		imageRef = new HashMap<TextureRegion, TR_Infos>() ;
		
		for(AtlasRegion region : atlas.getRegions())
		{
			allImage.add(region) ; 
			imageRef.put(region, new TR_Infos(region)) ; 
		}
		
		GVars_Ui.mainUi.addActor(new Vue_Edition_SideBar_AControl()); 
		
		InputProcessor input = buildClickProcessor() ; 	
		Gdx.input.setInputProcessor(new InputMultiplexer(GVars_Ui.mainUi, new EditorInputProcessus(),input));
	}
	
	
	
	public void saveAllData()
	{	
		FileHandle handler ; 
		for(TextureRegion region : allImage)
		{
			handler = new FileHandle("../editor/Files/Parallax/test.png") ; 
			region.getTexture().getTextureData().prepare();
			PixmapIO.writePNG(handler, region.getTexture().getTextureData().consumePixmap()) ; 
		}
	}

	@Override
	public void destroy()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void restart()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void update(float delta)
	{
		GVars_Ui.mainUi.act(delta);
		
		GVars_Inputs.updateInput(delta);
		
		if(!isPause)
		{
			worldCamera.position.add(screenSpeed, 0, 0); 
			worldCamera.update();
			batch.setProjectionMatrix(worldCamera.combined);
			Parallax_Heart.act(delta);
		}	
	}

	InputProcessor buildClickProcessor()
	{
		
		
		return new InputProcessor()
		{
			
			@Override
			public boolean touchUp(int screenX, int screenY, int pointer, int button)
			{
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean touchDragged(int screenX, int screenY, int pointer)
			{
				return false;
			}
			
			@Override
			public boolean touchDown(int screenX, int screenY, int pointer, int button)
			{
				return false;
			}
			
			@Override
			public boolean scrolled(int amount)
			{
				return false;
			}
			
			@Override
			public boolean mouseMoved(int screenX, int screenY)
			{
				if(GVars_Vue_Edition.colorPicked != null)
				{
					Pixmap map = ScreenUtils.getFrameBufferPixmap(screenX, screenY, 1, 1) ; 
					GVars_Vue_Edition.colorPicked.setColor(new Color(map.getPixel(0, 0)));
				}
				return true;
			}
			
			@Override
			public boolean keyUp(int keycode)
			{
				
				return false;
			}
			
			@Override
			public boolean keyTyped(char character)
			{
				
				return false;
			}
			
			@Override
			public boolean keyDown(int keycode)
			{
				// TODO Auto-generated method stub
				return false;
			}
		};
	}
	
	@Override
	public void render()
	{
		Gdx.gl.glClearColor(0.15f, 0.05f, 0.1f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		Gdx.gl.glViewport(parr_Pos_X,parr_Pos_Y, parr_Size_X, parr_Size_Y);
		Parallax_Heart.renderMainPage() ; 
//		Parallax_Heart.renderSecondePage();
		Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		
		GVars_Ui.mainUi.draw() ;	
		
	}

	@Override
	public void reciveFiles(String[] files)
	{
		String extension = Utils_Scene2D.getExtension(files[0]) ; 
    	System.out.println(extension);
		FileHandle handle = new FileHandle("C:\\Users\\Simon\\Documents\\JKS_Tools2D_ParallaxBackground\\editor\\Files"); 
	}

}