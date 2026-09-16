package jks.tools2d.parallax.heart;

import java.util.ArrayList;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;

import jks.tools2d.parallax.pages.Color_Serializer;
import jks.tools2d.parallax.pages.Page_Model;
import jks.tools2d.parallax.pages.Page_Model_Serializer;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Parallax_Model_Serializer;
import jks.tools2d.parallax.pages.WholePage_Model;
import jks.tools2d.parallax.pages.WholePage_Model_Serializer;

/** Kryo setup for reading and writing {@code .plax} files. */
public final class GVars_Serialization
{
	public static Kryo kryo;

	private GVars_Serialization()
	{}

	public static void init()
	{
		if (kryo == null)
			kryo = prepareKryo();
	}

	/**
	 * Registration order defines the class ids stored in {@code .plax} files: never reorder these, only append.
	 * Tools that need more classes should call this and register theirs afterwards.
	 * <p>
	 * References must stay on: the files were written with Kryo 5.0.0-RC1, whose default was to track references
	 * (every object is preceded by a reference marker). Kryo 5.0 final turned that default off.
	 * <p>
	 * The models' serializers are default serializers rather than {@code @DefaultSerializer} annotations, so the models
	 * stay translatable by GWT: registering one of them again still picks its serializer.
	 */
	public static Kryo prepareKryo()
	{
		Kryo kryo = new Kryo();
		kryo.setReferences(true);
		kryo.addDefaultSerializer(Parallax_Model.class, Parallax_Model_Serializer.class);
		kryo.addDefaultSerializer(Page_Model.class, Page_Model_Serializer.class);
		kryo.addDefaultSerializer(WholePage_Model.class, WholePage_Model_Serializer.class);
		kryo.register(Color.class, new Color_Serializer());
		kryo.register(Parallax_Model.class);
		kryo.register(Page_Model.class);
		kryo.register(ArrayList.class);
		kryo.register(WholePage_Model.class);
		return kryo;
	}
}
