package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.currentlySelectedParallax;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.heart.Parallax_Heart; 


public class Vue_Edition_SideBar_TextureConfig extends Tab
{

	Table mainTable ; 
	
//	private TextureRegion texRegion;
//	private float pad_X = 0, pad_Y = 0 ;
//	private float region_Width,region_Height;
//	private float sizeRatio =  1;
	
	Slider padX_Slider, padY_Slider ; 
	TextField padX_tf,padY_tf ; 
	
	Vue_Edition_SideBar_TextureConfig()
	{
		super(false, false);
		mainTable = new Table() ; 
		
		currentlySelectedParallax = (TextureRegionParallaxLayer) Parallax_Heart.parallaxMainPage.layers.get(5) ; 

		padX_Slider = new Slider(-30, 30, 1, false,baseSkin); 
		padX_Slider.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{
				touchDragged(event, x,y,pointer) ;
				return true ;
			}
			
			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer)
			{
				currentlySelectedParallax.setDecalX(padX_Slider.getValue());
				padX_tf.setText(padX_Slider.getValue() + "");
			}
		}) ; 
		
		padY_tf = new TextField("",baseSkin) ; 
		padY_Slider.setValue(0) ; 
		padY_tf.setText(padX_Slider.getValue() + "");
		
		padY_Slider = new Slider(-30, 30, 1, false,baseSkin); 
		padY_Slider.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{
				touchDragged(event, x,y,pointer) ;
				return true ;
			}
			
			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer)
			{
				currentlySelectedParallax.setDecalY(padY_Slider.getValue());
				padY_tf.setText(padX_Slider.getValue() + "");
			}
		}) ; 
		
		padX_tf = new TextField("",baseSkin) ; 
		padX_Slider.setValue(0) ; 
		padX_tf.setText(padX_Slider.getValue() + "");
		
		mainTable.add(new VisLabel("Pad X")).row();
		mainTable.add(padX_Slider).pad(10).expandX().fillX() ; 
		mainTable.add(padX_tf).width(40) ; 
		
//		.width(200) 
		;
//		currentlySelectedParallax.act(1); ; 
	}

	@Override
	public String getTabTitle()
	{
		return "Texture Config";
	}

	@Override
	public Table getContentTable()
	{
		return mainTable;
	}
	
	
}
