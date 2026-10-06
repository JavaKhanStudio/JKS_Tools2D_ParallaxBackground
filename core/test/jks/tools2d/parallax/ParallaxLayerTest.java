package jks.tools2d.parallax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;

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
			else if (field.getType() == int.class)
				field.setInt(layer, (int) next++);
			else if (field.getType() == String.class)
				field.set(layer, "field " + next++);
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

	@Test
	void anEmptyLayerClonesAsEmpty()
	{
		ParallaxLayer layer = ParallaxLayer.empty("birds", 0.5f);
		layer.setWorldSize(40, 22.5f);
		layer.setParallaxSpeedRatioX(0.03f);
		layer.setCurrentDistanceX(7);

		ParallaxLayer copy = layer.clone();

		assertSame(Enum_LayerKind.EMPTY, copy.getKind());
		assertEquals("birds", copy.getName());
		assertNull(copy.getTexRegion());
		assertEquals(20, copy.getWidth());
		assertEquals(11.25f, copy.getHeight());
		assertEquals(0.03f, copy.getParallaxSpeedRatioX());
		assertEquals(7, copy.getCurrentDistanceX());
	}

	@Test
	void aShaderLayerClonesAsAShaderLayer()
	{
		ParallaxLayer layer = ParallaxLayer.shader(region(1920, 1080), 40, 0.5f, Enum_ShaderEffect.FOG, 0.4f, 6, -2);
		layer.setShaderHaze(0.25f);
		layer.setCurrentDistanceX(7);

		ParallaxLayer copy = layer.clone();

		assertSame(Enum_LayerKind.SHADER, copy.getKind());
		assertEquals(layer.getTexRegion(), copy.getTexRegion());
		assertEquals(20, copy.getWidth());
		assertEquals(11.25f, copy.getHeight());
		assertSame(Enum_ShaderEffect.FOG, copy.getShaderEffect());
		assertEquals(0.4f, copy.getShaderAmplitude());
		assertEquals(6, copy.getShaderWavelength());
		assertEquals(-2, copy.getShaderSpeed());
		assertEquals(0.25f, copy.getShaderHaze());
		assertEquals(7, copy.getCurrentDistanceX());
	}

	/** A SEQUENCE layer's copy draws the same cycle, from arrays of its own: a game's seed redraws one in place. */
	@Test
	void aSequenceLayerClonesItsCycle()
	{
		ParallaxLayer layer = ParallaxLayer.sequence(new ArrayList<>(List.of(region(200, 100), region(100, 100), region(300, 100))),
				new int[] { 1, 2, 3 }, 42, 10, 40, 0.15f);
		layer.setPadX(0.5f);
		layer.setCurrentDistanceX(7);

		ParallaxLayer copy = layer.clone();

		assertSame(Enum_LayerKind.SEQUENCE, copy.getKind());
		assertEquals(10, copy.getSequenceLength());
		assertEquals(42, copy.getSequenceSeed());
		assertEquals(layer.getWidth(), copy.getWidth());
		assertEquals(layer.getTotalWidth(), copy.getTotalWidth());
		int[] picks = new int[10];
		for (int slot = 0; slot < 10; slot++)
			assertEquals(picks[slot] = layer.getCycleSegment(slot), copy.getCycleSegment(slot), "slot " + slot);

		copy.drawCycleFrom(7);
		for (int slot = 0; slot < 10; slot++)
			assertEquals(picks[slot], layer.getCycleSegment(slot), "the original keeps its cycle, slot " + slot);
		int[] copied = new int[10];
		for (int slot = 0; slot < 10; slot++)
			copied[slot] = copy.getCycleSegment(slot);
		assertNotEquals(Arrays.toString(picks), Arrays.toString(copied), "seed 7 draws another cycle");
	}
}
