package jks.tools2d.parallax.editor.gvars;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.VisUI;

public class GVars_Ui implements Runnable 
{

	public static Skin baseSkin;
	public static Stage mainUi;
	public static Table interaction;
	
	
	
	public static void init() 
	{
//		Skin skin = VisUI.load(Gdx.files.internal("skins/uis/uiskin.json")) ;
		baseSkin = new Skin(Gdx.files.internal("skins/uis/uiskin.json"));
		VisUI.load(GVars_Ui.baseSkin);
		mainUi = new Stage();
		Gdx.input.setInputProcessor(mainUi);
	}

	public static void reset()
	{
		mainUi = new Stage();
		Gdx.input.setInputProcessor(mainUi);
	}

	@Override
	public void run() {
		init();
	}
	

	

}