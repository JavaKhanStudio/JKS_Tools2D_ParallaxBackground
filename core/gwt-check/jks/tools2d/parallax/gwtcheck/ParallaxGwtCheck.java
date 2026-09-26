package jks.tools2d.parallax.gwtcheck;

import java.util.ArrayList;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.google.gwt.core.client.EntryPoint;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/** Never run: it only has to reach what a browser game calls, so the GWT compiler translates it. */
public class ParallaxGwtCheck implements EntryPoint
{
	@Override
	public void onModuleLoad()
	{
		Parallax_Model layer = new Parallax_Model();
		layer.regionName = "ground";
		WholePage_Model page = new WholePage_Model("page.atlas", Color.WHITE, Color.WHITE, Color.BLACK, Color.BLACK);
		page.pageModel.pageList = new ArrayList<>();
		page.pageModel.pageList.add(layer);
		page.forceLoad(new TextureAtlas());

		Parallax_Heart heart = new Parallax_Heart(new OrthographicCamera(), new SpriteBatch(), page, 40, 30);
		heart.transfertIntoPage(page, 1);
		heart.resize(800, 600);
		heart.act(1 / 60f);
		heart.render();
		for (ParallaxLayer drawn : page.getDrawing())
			drawn.clone().resetPosition();
		heart.dispose();

		WholePage_Model saved = Utils_Page_Json.readPage("{\"pageModel\":{\"atlasName\":\"page.atlas\",\"pageList\":[]}}");
		Parallax_Heart.fromJson("page.jplax").setPage(saved);
	}
}
