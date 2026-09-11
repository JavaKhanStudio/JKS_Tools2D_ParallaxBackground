package jks.tools2d.parallax.editor.vue.edition.data;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.WholePage_Model;

/** Page saved in a project (.plaxpj): layers may also use loose images, flagged by {@link #inside} = false. */
public class WholePage_Editor extends WholePage_Model
{
	/** For each layer of {@link #pageModel}: true when its image comes from the atlas. */
	public ArrayList<Boolean> inside;

	public WholePage_Editor()
	{
		super();
		inside = new ArrayList<>();
	}

	@Override
	protected List<ParallaxLayer> load(float worldWidth, float worldHeight, TextureAtlas atlas)
	{
		List<ParallaxLayer> layers = new ArrayList<>();
		ArrayList<Parallax_Model> models = pageModel.pageList;

		for (int i = 0; i < models.size(); )
		{
			Parallax_Model model = models.get(i);
			boolean fromAtlas = i >= inside.size() || inside.get(i);
			ParallaxLayer layer = fromAtlas ? buildLayer(model, atlas, worldWidth) : buildOutsideLayer(model, worldWidth);

			if (layer == null)
			{
				// Missing loose image: drop the layer from the model too, so layers and models stay aligned.
				Gdx.app.error("WholePage_Editor", "Image not found, layer removed: " + model.regionName);
				models.remove(i);
				if (i < inside.size())
					inside.remove(i);
				continue;
			}

			layers.add(layer);
			i++;
		}

		return layers;
	}

	protected ParallaxLayer buildOutsideLayer(Parallax_Model parallax, float worldWidth)
	{
		TextureRegion texture = GVars_Vue_Edition.outsideTextureReserve.get(parallax.regionName);
		if (texture == null)
			return null;

		ParallaxLayer layer = new ParallaxLayer(
				texture,
				true,
				worldWidth,
				parallax.parallaxScalingSpeedX, parallax.parallaxScalingSpeedY,
				parallax.sizeRatio);

		layer.setUpEverything(parallax);
		return layer;
	}
}
