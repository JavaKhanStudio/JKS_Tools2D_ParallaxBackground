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

	/** A LURKER layer's copy is a lurker, with its seed, its odds and its cover. */
	@Test
	void aLurkerLayerClonesAsALurker()
	{
		ParallaxLayer layer = new ParallaxLayer(Enum_LayerKind.LURKER, new ArrayList<>(List.of(region(200, 400))), true, 40, 0.032f, 0, 0.1f);
		float[] cover = { 1, 0.25f, 3.5f };
		layer.setLurker(-7, 5, cover);
		layer.setCurrentDistanceX(7);

		ParallaxLayer copy = layer.clone();

		assertSame(Enum_LayerKind.LURKER, copy.getKind());
		assertEquals(layer.getTexRegion(), copy.getTexRegion());
		assertEquals(layer.getWidth(), copy.getWidth());
		assertEquals(-7, copy.getLurkerSeed());
		assertEquals(5, copy.getLurkerOdds());
		assertSame(cover, copy.getLurkerCover());
		assertEquals(7, copy.getCurrentDistanceX());
	}

	/** A layer's copy keeps its animated patch: its frames, its place, its rate and the frame it is on. */
	@Test
	void anAnimatedPatchClonesWithItsLayer()
	{
		ParallaxLayer layer = ParallaxLayer.shader(region(1920, 1080), 40, 0.5f, Enum_ShaderEffect.WAVE, 0.4f, 6, 1);
		List<TextureRegion> frames = new ArrayList<>(List.of(region(200, 100), region(200, 100), region(200, 100)));
		layer.setAnim("fronds", 412, -3, 7.5f);
		layer.setAnimFrames(frames);
		layer.setAnimTime(0.3);

		ParallaxLayer copy = layer.clone();

		assertEquals("fronds", copy.getAnimRegion());
		assertEquals(412, copy.getAnimX());
		assertEquals(-3, copy.getAnimY());
		assertEquals(7.5f, copy.getAnimFps());
		assertSame(frames, copy.getAnimFrames());
		assertEquals(2, copy.getAnimFrame(), "floor(0.3 x 7.5) = 2");
	}

	/** Frame floor(time x fps) modulo the count; a null name is none, and a NaN or negative rate never picks outside. */
	@Test
	void anAnimatedPatchPicksItsFrameFromTheClock()
	{
		ParallaxLayer layer = new ParallaxLayer(region(1920, 1080), true, 40, 0.02f, 0.02f, 1);
		layer.setAnim(null, 0, 0, 12);
		assertEquals("", layer.getAnimRegion());
		layer.setAnimFrames(new ArrayList<>(List.of(region(20, 10), region(20, 10), region(20, 10), region(20, 10))));
		layer.setAnimTime(0);
		assertEquals(0, layer.getAnimFrame());
		layer.setAnimTime(1 / 12.0 * 3.25);
		assertEquals(3, layer.getAnimFrame());
		layer.setAnimTime(1 / 12.0 * 5.25);
		assertEquals(1, layer.getAnimFrame(), "the fifth frame past the last is the second");
		layer.setAnim("fronds", 0, 0, -12);
		layer.setAnimTime(1 / 12.0 * 0.25);
		assertEquals(3, layer.getAnimFrame(), "backwards");
		layer.setAnim("fronds", 0, 0, Float.NaN);
		layer.setAnimTime(2);
		assertEquals(0, layer.getAnimFrame());
		layer.setAnimFrames(null);
		assertNull(layer.getAnimFrames());
	}

	/** Odds below 1 would never show the figure, or divide by 0: kept at 1. No cover is an empty one, never null. */
	@Test
	void lurkerOddsAreOneAtLeast()
	{
		ParallaxLayer layer = new ParallaxLayer(Enum_LayerKind.LURKER, new ArrayList<>(List.of(region(200, 400))), true, 40, 0.032f, 0, 0.1f);
		layer.setLurker(0, 0, null);
		assertEquals(1, layer.getLurkerOdds());
		assertEquals(0, layer.getLurkerCover().length);
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
