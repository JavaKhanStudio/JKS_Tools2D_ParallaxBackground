package jks.tools2d.parallax.editor.gvars;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import jks.tools2d.parallax.heart.GVars_Serialization;
import jks.tools2d.parallax.pages.Json_MixIns;

/** Kryo for .plax exports (shared with the runtime) and Jackson for .jplax exports and .plaxpj projects. */
public final class GVars_Serialization_Editor
{
	public static ObjectMapper objectMapper;

	private GVars_Serialization_Editor()
	{}

	public static void init()
	{
		GVars_Serialization.init();
		if (objectMapper == null)
			objectMapper = prepareJson();
	}

	private static ObjectMapper prepareJson()
	{
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		Json_MixIns.MIX_INS.forEach(mapper::addMixIn);
		mapper.addMixIn(TextureRegion.class, MyMixInForIgnoreType.class);
		return mapper;
	}
}
