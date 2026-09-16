package jks.tools2d.parallax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

class ParallaxLayerTest
{
	private static TextureRegion region(int width, int height)
	{
		return new TextureRegion()
		{
			@Override
			public int getRegionWidth()
			{return width;}

			@Override
			public int getRegionHeight()
			{return height;}
		};
	}

	/** clone() copies field by field (GWT has no Object.clone): a field it forgets fails here. */
	@Test
	void cloneCopiesEveryField() throws IllegalAccessException
	{
		List<TextureRegion> regions = new ArrayList<>(List.of(region(1920, 1080), region(10, 10)));
		ParallaxLayer layer = new ParallaxLayer(regions, false, 25, 0.3f, 0.4f, 2);

		// A distinct value in every field the constructor does not set, so a forgotten one keeps its default.
		float next = 1000;
		for (Field field : ParallaxLayer.class.getDeclaredFields())
		{
			if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()))
				continue;
			field.setAccessible(true);
			if (field.getType() == float.class)
				field.setFloat(layer, next++);
			else if (field.getType() == boolean.class)
				field.setBoolean(layer, true);
		}

		ParallaxLayer copy = layer.clone();

		assertSame(ParallaxLayer.class, copy.getClass());
		assertEquals(regions, copy.getTexRegion());
		assertNotSame(regions, copy.getTexRegion(), "the copy's regions must be a list of its own");
		for (Field field : ParallaxLayer.class.getDeclaredFields())
		{
			if (Modifier.isStatic(field.getModifiers()))
				continue;
			field.setAccessible(true);
			if (field.getType().isPrimitive())
				assertEquals(field.get(layer), field.get(copy), field.getName());
			else if (!field.getName().equals("texRegion"))
				assertSame(field.get(layer), field.get(copy), field.getName());
		}
	}
}
