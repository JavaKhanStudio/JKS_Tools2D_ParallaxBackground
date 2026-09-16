package jks.tools2d.parallax.pages;

import java.util.List;
import java.util.Map;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreType;

import jks.tools2d.parallax.ParallaxLayer;

/**
 * What Jackson must skip in the models, kept out of them so they stay translatable by GWT. A mapper reading or writing
 * .jplax or .plaxpj adds all of them: {@code MIX_INS.forEach(mapper::addMixIn)}.
 */
public final class Json_MixIns
{
	/** Model class to the mix-in carrying its Jackson annotations. */
	public static final Map<Class<?>, Class<?>> MIX_INS = Map.of(
			ParallaxLayer.class, IgnoredType.class,
			WholePage_Model.class, WholePage.class,
			Parallax_Model.class, Parallax.class);

	private Json_MixIns()
	{}

	@JsonIgnoreType
	private static final class IgnoredType
	{}

	@JsonIgnoreProperties(value = { "preloadValue" }, ignoreUnknown = true)
	private abstract static class WholePage
	{
		@JsonIgnore
		public List<ParallaxLayer> preloadValue;

		@JsonIgnore
		public abstract List<ParallaxLayer> getDrawing();

		@JsonIgnore
		public abstract List<ParallaxLayer> getDrawing(String relativePath);

		@JsonIgnore
		public abstract List<ParallaxLayer> getDrawing(String relativePath, float worldWidth, float worldHeight);

		@JsonIgnore
		public abstract void forceLoad(TextureAtlas atlas);

		@JsonIgnore
		public abstract TextureAtlas getLoadedAtlas();
	}

	private abstract static class Parallax
	{
		@JsonIgnore
		public abstract float getSpeed();

		@JsonIgnore
		public abstract void setSpeed(float speed);

		@JsonIgnore
		public abstract String getCompleteRegionName();
	}
}
