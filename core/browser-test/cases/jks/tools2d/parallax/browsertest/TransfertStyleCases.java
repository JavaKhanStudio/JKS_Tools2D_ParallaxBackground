package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isFalse;
import static jks.tools2d.parallax.browsertest.Check.isTrue;
import static jks.tools2d.parallax.browsertest.Check.same;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;

import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.TransfertStyle;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * ParallaxPageReader's cases on transfert styles: the depth stagger, the dissolve, through a colour and the fog creep.
 * Shared builders in {@link ReaderFixtures}.
 */
final class TransfertStyleCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: depthStaggerFadesEachSlotInItsOwnWindowInEveryRepeatMode", () -> new TransfertStyleCases().depthStaggerFadesEachSlotInItsOwnWindowInEveryRepeatMode()),
				new BrowserCase("reader: depthStaggerMatchesPagesOfUnequalLayerCountsFromTheFront", () -> new TransfertStyleCases().depthStaggerMatchesPagesOfUnequalLayerCountsFromTheFront()),
				new BrowserCase("reader: aTransfertWithoutAStyleFadesEverySlotAtOnce", () -> new TransfertStyleCases().aTransfertWithoutAStyleFadesEverySlotAtOnce()),
				new BrowserCase("reader: dissolveMasksEachSlotOnlyWhileInTransferInEveryRepeatMode", () -> new TransfertStyleCases().dissolveMasksEachSlotOnlyWhileInTransferInEveryRepeatMode()),
				new BrowserCase("reader: dissolveFadesWhatTheEngineCannotMask", () -> new TransfertStyleCases().dissolveFadesWhatTheEngineCannotMask()),
				new BrowserCase("reader: dissolveKeepsEachPagesFogAndTheTint", () -> new TransfertStyleCases().dissolveKeepsEachPagesFogAndTheTint()),
				new BrowserCase("reader: throughColorGradesOneSideOfEachSlotInEveryRepeatMode", () -> new TransfertStyleCases().throughColorGradesOneSideOfEachSlotInEveryRepeatMode()),
				new BrowserCase("reader: throughColorFadesWhatTheEngineCannotGrade", () -> new TransfertStyleCases().throughColorFadesWhatTheEngineCannotGrade()),
				new BrowserCase("reader: throughColorKeepsEachPagesFogAndTheTint", () -> new TransfertStyleCases().throughColorKeepsEachPagesFogAndTheTint()),
				new BrowserCase("reader: throughColorTakesTheGradientsThroughItsColor", () -> new TransfertStyleCases().throughColorTakesTheGradientsThroughItsColor()),
				new BrowserCase("reader: fogCreepSinksTheFarLayersFirstAndSwapsAtTheMiddleInEveryRepeatMode", () -> new TransfertStyleCases().fogCreepSinksTheFarLayersFirstAndSwapsAtTheMiddleInEveryRepeatMode()),
				new BrowserCase("reader: fogCreepTakesTheGradientsAtTheFarLayersPace", () -> new TransfertStyleCases().fogCreepTakesTheGradientsAtTheFarLayersPace()));
	}

	/** {@code n} layers told apart by their width: layer i is {@code base + 0.05 i} of the 40-wide world. */
	private static List<ParallaxLayer> widened(float base, int n)
	{
		List<ParallaxLayer> page = new ArrayList<>();
		for (int i = 0; i < n; i++)
		{
			ParallaxLayer layer = layer(0);
			layer.setSizeRatio(base + 0.05f * i);
			page.add(layer);
		}
		return page;
	}

	/** The transfert lab's rampOf (TransfertLab, r220), written out again: what slot k of n is at when the whole is at t. */
	private static float labRamp(float t, float stagger, int slot, int total)
	{
		float k = total > 1 ? slot / (float) (total - 1) : 0;
		return Math.max(0, Math.min(1, t * (1 + stagger) - stagger * k));
	}

	/**
	 * Every draw of the layer {@code base + 0.05 i} wide is at {@code alpha}, and there is at least one; none at alpha 0
	 * (a layer at alpha 0 is not drawn).
	 */
	private void drawnAt(float base, int i, float alpha, String what)
	{
		float width = 40 * (base + 0.05f * i);
		int found = 0;
		for (float[] draw : draws)
			if (Math.abs(draw[2] - width) < 1e-3f)
			{
				found++;
				equal(alpha, draw[5], 1e-5f, what + ": alpha");
			}
		if (alpha > 0)
			isTrue(found > 0, what + ": drawn");
		else
			equal(0, found, what + ": drawn at alpha 0");
	}

	void depthStaggerFadesEachSlotInItsOwnWindowInEveryRepeatMode()
	{
		float stagger = 1;
		for (int mode = 0; mode < 4; mode++)
		{
			String repeat = new String[] { "X", "Y", "XY", "none" }[mode];
			ParallaxPageReader reader = reader(mode != 1 && mode != 3, mode == 1 || mode == 2);
			reader.addLayers(widened(0.3f, 3));
			reader.addLayersTransfert(page(widened(0.5f, 3)), null, 1, TransfertStyle.depthStagger(stagger));
			same(TransfertStyle.Kind.DEPTH_STAGGER, reader.getTransfertStyle().getKind(), "the style under way");
			for (float t : new float[] { 0.25f, 0.5f, 0.75f })
			{
				// A quarter of the 1 s transfert a step: the fade is at t exactly.
				reader.act(0.25f, 0, 0);
				draws.clear();
				reader.draw(camera, batch);
				for (int slot = 0; slot < 3; slot++)
				{
					float ramp = labRamp(t, stagger, slot, 3);
					drawnAt(0.3f, slot, 1 - ramp, repeat + " at " + t + ", outgoing slot " + slot);
					drawnAt(0.5f, slot, ramp, repeat + " at " + t + ", incoming slot " + slot);
				}
			}
			reader.act(0.25f, 0, 0);
			isFalse(reader.isInTransfer(), repeat + ": every slot is in when the transfert ends");
			same(TransfertStyle.FADE, reader.getTransfertStyle(), "no style outside a transfert");
		}
	}

	void depthStaggerMatchesPagesOfUnequalLayerCountsFromTheFront()
	{
		float stagger = 2;
		for (int more = 0; more < 2; more++)
		{
			// 2 layers into 4, then 4 into 2: the page with fewer fills the front slots, 2 and 3 of 4.
			int oldCount = more == 0 ? 2 : 4, newCount = more == 0 ? 4 : 2;
			ParallaxPageReader reader = reader(true, false);
			reader.addLayers(widened(0.3f, oldCount));
			reader.addLayersTransfert(page(widened(0.6f, newCount)), null, 1, TransfertStyle.depthStagger(stagger));
			reader.act(0.5f, 0, 0);
			draws.clear();
			reader.draw(camera, batch);
			for (int slot = 0; slot < 4; slot++)
			{
				float ramp = labRamp(0.5f, stagger, slot, 4);
				if (slot >= 4 - oldCount)
					drawnAt(0.3f, slot - (4 - oldCount), 1 - ramp, oldCount + " into " + newCount + ", outgoing slot " + slot);
				if (slot >= 4 - newCount)
					drawnAt(0.6f, slot - (4 - newCount), ramp, oldCount + " into " + newCount + ", incoming slot " + slot);
			}
		}
	}

	void aTransfertWithoutAStyleFadesEverySlotAtOnce()
	{
		same(TransfertStyle.FADE, TransfertStyle.depthStagger(0), "a stagger of 0 is FADE");
		equal(TransfertStyle.MAX_STAGGER, TransfertStyle.depthStagger(5).getStagger(), 0, "the stagger is clamped");
		for (int call = 0; call < 3; call++)
		{
			ParallaxPageReader reader = reader(true, true);
			reader.addLayers(widened(0.3f, 3));
			if (call == 0)
				reader.addLayersTransfert(page(widened(0.5f, 3)), 1);
			else
				reader.addLayersTransfert(page(widened(0.5f, 3)), null, 1, call == 1 ? null : TransfertStyle.FADE);
			reader.act(0.25f, 0, 0);
			draws.clear();
			reader.draw(camera, batch);
			for (int slot = 0; slot < 3; slot++)
			{
				drawnAt(0.3f, slot, 0.75f, "call " + call + ", outgoing slot " + slot);
				drawnAt(0.5f, slot, 0.25f, "call " + call + ", incoming slot " + slot);
			}
		}
	}

	/**
	 * Stands for an engine that draws a dissolve, or one that grades ({@code grades}): records each layer begun, the
	 * dissolve it was begun under (side, ramp, softness, drift, cells across and up) or the grade (r, g, b, amount), and
	 * its fog; checks every begin is ended, and the dissolve or the grade changes between layers only. It draws the one
	 * and not the other, as the engines written before the other did.
	 */
	private final class TransfertEffects implements LayerEffects
	{
		final boolean grades;
		final List<ParallaxLayer> layers = new ArrayList<>();
		final List<float[]> states = new ArrayList<>();
		final List<Float> fogs = new ArrayList<>();
		/** The dissolve or the grade set last. */
		final float[] state = new float[6];
		int setCalls, pageFogs;
		private boolean open;

		TransfertEffects(boolean grades)
		{this.grades = grades;}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{throw new AssertionError("the reader calls the begin that takes the fog");}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fog, Color fogColor)
		{
			isFalse(open, "a layer begun inside another");
			open = true;
			layers.add(layer);
			states.add(Arrays.copyOf(state, state.length));
			fogs.add(fog);
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{
			isTrue(open, "ended without a begin");
			open = false;
		}

		@Override
		public boolean beginPageFog(Batch batch, Color tint, Color fog, Color incomingFog)
		{
			pageFogs++;
			return false;
		}

		@Override
		public boolean setDissolve(int side, float ramp, float softness, float drift, float cellsX, float cellsY)
		{
			if (grades)
				return false;
			set("the dissolve", side, ramp, softness, drift, cellsX, cellsY);
			return true;
		}

		@Override
		public boolean setGrade(float r, float g, float b, float amount)
		{
			if (!grades)
				return false;
			set("the grade", r, g, b, amount);
			return true;
		}

		private void set(String what, float... values)
		{
			isFalse(open, what + " changed inside a layer");
			setCalls++;
			System.arraycopy(values, 0, state, 0, values.length);
		}

		/** The dissolve or grade {@code layer} was last begun under; null when it was not begun. */
		float[] stateOf(ParallaxLayer layer)
		{
			int i = layers.lastIndexOf(layer);
			return i < 0 ? null : states.get(i);
		}

		void clear()
		{
			layers.clear();
			states.clear();
			fogs.clear();
			setCalls = 0;
		}
	}

	private static final String[] REPEATS = { "X", "Y", "XY", "none" };

	/** A reader repeating as {@code REPEATS[mode]} says. */
	private ParallaxPageReader inRepeatMode(int mode)
	{return reader(mode != 1 && mode != 3, mode == 1 || mode == 2);}

	/** Acts {@code seconds} and draws, what {@code effects} and the batch recorded before cleared. */
	private void stepAndDraw(ParallaxPageReader reader, TransfertEffects effects, float seconds)
	{
		reader.act(seconds, 0, 0);
		effects.clear();
		draws.clear();
		reader.draw(camera, batch);
	}

	/** Draws {@code from} under {@code effects}, sees nothing begun, and starts the 1 s transfert into {@code into}. */
	private void startsFromNothingBegun(ParallaxPageReader reader, TransfertEffects effects, List<ParallaxLayer> from,
			List<ParallaxLayer> into, TransfertStyle style, String repeat)
	{
		reader.setLayerEffects(effects);
		reader.addLayers(from);
		reader.draw(camera, batch);
		equal(0, effects.layers.size() + effects.setCalls, repeat + ": nothing begun before the transfert");
		reader.addLayersTransfert(page(into), null, 1, style);
		same(style, reader.getTransfertStyle(), "the style under way");
	}

	/** The transfert's last step of {@code seconds}: it is over, nothing is begun, the new page is drawn plain. */
	private void endsPlain(ParallaxPageReader reader, TransfertEffects effects, float seconds, String repeat)
	{
		stepAndDraw(reader, effects, seconds);
		isFalse(reader.isInTransfer(), repeat + ": the transfert is over");
		equal(0, effects.layers.size() + effects.setCalls, repeat + ": nothing begun after the transfert");
		drawnAt(0.5f, 0, 1, repeat + ": the new page, plain");
	}

	/** A hook on the EMPTY layer "slot": the batch alpha it was last called with is in the array's one cell, -1 before. */
	private static float[] alphaHook(ParallaxPageReader reader)
	{
		final float[] alpha = { -1 };
		reader.setLayerHook("slot", new LayerHook()
		{
			@Override
			public void draw(Batch batch, ParallaxLayer layer, float x, float y, float width, float height)
			{alpha[0] = batch.getColor().a;}
		});
		return alpha;
	}

	/**
	 * A page of a back and a front layer, in a fog of 0.1 and the tint (0.5, 1, 1), transferring over 2 s with
	 * {@code style} into a page of a back layer, a SHADER one and a front one in a fog of 0.02. Returns the old back and
	 * front, the new back, the SHADER layer, the new front.
	 */
	private ParallaxLayer[] fogPages(ParallaxPageReader reader, TransfertEffects effects, TransfertStyle style)
	{
		reader.setLayerEffects(effects);
		reader.setFog(0.1f, Color.WHITE);
		reader.addColorTransfert(new Color(0.5f, 1, 1, 1), 0);
		ParallaxLayer oldBack = atSpeed(0.01f), oldFront = atSpeed(0.1f);
		reader.addLayers(list(oldBack, oldFront));
		ParallaxLayer newBack = atSpeed(0.01f), newFront = atSpeed(0.2f), wave = shaded(0.5f, 3, 1);
		WholePage_Model next = page(list(newBack, wave, newFront));
		next.setFogStrength(0.02f);
		reader.addLayersTransfert(next, null, 2, style);
		return new ParallaxLayer[] { oldBack, oldFront, newBack, wave, newFront };
	}

	/** Every draw in the tint (0.5, 1, 1) at full opacity ({@code why}), and the batch white after the draw. */
	private void drawnInTheTint(String why)
	{
		for (float[] draw : draws)
		{
			equal(0.5f, draw[4], 0, "drawn in the tint");
			equal(1, draw[5], 0, "at full opacity: " + why);
		}
		isTrue(batch.getColor().equals(Color.WHITE), "the batch white after the draw");
	}

	/**
	 * r250: a dissolve begins every layer of both pages, slot by slot, under its own mask: the outgoing side then the
	 * incoming one, at the slot's ramp in the depth stagger's windows, at full opacity, the mask set back to none after
	 * each; a layer whose share is none is not drawn. Outside the transfert, nothing is begun and no mask set.
	 */
	void dissolveMasksEachSlotOnlyWhileInTransferInEveryRepeatMode()
	{
		float stagger = 1;
		TransfertStyle style = TransfertStyle.dissolve(5, 0.08f, stagger);
		same(TransfertStyle.Kind.DISSOLVE, style.getKind(), "a dissolve");
		for (int mode = 0; mode < 4; mode++)
		{
			String repeat = REPEATS[mode];
			ParallaxPageReader reader = inRepeatMode(mode);
			TransfertEffects effects = new TransfertEffects(false);
			List<ParallaxLayer> from = widened(0.3f, 3), into = widened(0.5f, 3);
			startsFromNothingBegun(reader, effects, from, into, style, repeat);
			for (float t : new float[] { 0.25f, 0.5f, 0.75f })
			{
				// A quarter of the 1 s transfert a step: the fade is at t exactly.
				stepAndDraw(reader, effects, 0.25f);
				isTrue(reader.isInTransfer(), repeat + " at " + t + ": in transfer");
				int shown = 0;
				for (int slot = 0; slot < 3; slot++)
				{
					float ramp = labRamp(t, stagger, slot, 3);
					for (int side = 0; side < 2; side++)
					{
						ParallaxLayer layer = (side == 0 ? from : into).get(slot);
						float share = side == 0 ? 1 - ramp : ramp;
						String what = repeat + " at " + t + (side == 0 ? ", outgoing" : ", incoming") + " slot " + slot;
						float[] mask = effects.stateOf(layer);
						if (share <= 0)
						{
							isTrue(mask == null, what + ": a layer whose share is none is not begun");
							drawnAt(side == 0 ? 0.3f : 0.5f, slot, 0, what);
							continue;
						}
						shown++;
						isTrue(mask != null, what + ": begun");
						equal(side == 0 ? LayerEffects.DISSOLVE_OUTGOING : LayerEffects.DISSOLVE_INCOMING, mask[0], 0, what + ": side");
						equal(ramp, mask[1], 1e-6f, what + ": the slot's ramp");
						equal(0.08f, mask[2], 0, what + ": softness");
						equal(TransfertStyle.drift(t), mask[3], 1e-6f, what + ": drift on the reader's clock");
						equal(5, mask[4], 0, what + ": patches across");
						equal(5 * 22.5f / 40 * 2.5f, mask[5], 1e-5f, what + ": cells up the view");
						drawnAt(side == 0 ? 0.3f : 0.5f, slot, 1, what);
					}
				}
				equal(shown, effects.layers.size(), repeat + " at " + t + ": one begin per layer shown");
				equal(2 * shown, effects.setCalls, repeat + " at " + t + ": a mask set, and set back to none, per layer");
				equal(0, effects.pageFogs, repeat + ": no page fog during a dissolve");
			}
			endsPlain(reader, effects, 0.25f, repeat);
		}
	}

	/**
	 * An engine that draws no dissolve (setDissolve false, as a LayerEffects written before it), and an EMPTY layer in
	 * any engine: each layer fades to its share of the slot, as the depth stagger does.
	 */
	void dissolveFadesWhatTheEngineCannotMask()
	{
		ParallaxPageReader reader = reader(true, false);
		RecordingEffects before = new RecordingEffects();
		reader.setLayerEffects(before);
		reader.addLayers(widened(0.3f, 3));
		reader.addLayersTransfert(page(widened(0.5f, 3)), null, 1, TransfertStyle.dissolve(5, 0.1f, 2));
		reader.act(0.5f, 0, 0);
		draws.clear();
		reader.draw(camera, batch);
		for (int slot = 0; slot < 3; slot++)
		{
			float ramp = labRamp(0.5f, 2, slot, 3);
			drawnAt(0.3f, slot, 1 - ramp, "no dissolve in the engine, outgoing slot " + slot);
			drawnAt(0.5f, slot, ramp, "no dissolve in the engine, incoming slot " + slot);
		}
		equal(0, before.runs.size(), "nothing begun");

		ParallaxPageReader hooked = reader(true, false);
		TransfertEffects effects = new TransfertEffects(false);
		hooked.setLayerEffects(effects);
		float[] alpha = alphaHook(hooked);
		ParallaxLayer back = layer(0);
		hooked.addLayers(list(back));
		hooked.addLayersTransfert(page(list(ParallaxLayer.empty("slot", 0.3f))), null, 1, TransfertStyle.dissolve(5, 0.1f, 0));
		hooked.act(0.25f, 0, 0);
		hooked.draw(camera, batch);
		equal(0.25f, alpha[0], 1e-6f, "the hook fades in");
		isTrue(effects.stateOf(back) != null, "the image layer is masked");
		equal(1, effects.layers.size(), "the EMPTY layer is not begun");
	}

	/**
	 * Each page's layers keep their own fog under the mask, begun one by one (no page fog's shared shader), and they
	 * are drawn in the tint; the batch is white after the draw.
	 */
	void dissolveKeepsEachPagesFogAndTheTint()
	{
		ParallaxPageReader reader = reader(true, false);
		TransfertEffects effects = new TransfertEffects(false);
		ParallaxLayer[] l = fogPages(reader, effects, TransfertStyle.dissolve(8, 0.05f, 0));
		ParallaxLayer oldBack = l[0], newBack = l[2], wave = l[3], newFront = l[4];
		stepAndDraw(reader, effects, 1);
		equal(fog(0.1f, 0.01f, 0.1f), effects.fogs.get(effects.layers.indexOf(oldBack)), 1e-6f, "the outgoing page: its own fog");
		equal(fog(0.02f, 0.01f, 0.2f), effects.fogs.get(effects.layers.indexOf(newBack)), 1e-6f, "the incoming page: its own");
		equal(0, effects.fogs.get(effects.layers.indexOf(newFront)), 0, "a front layer begun with no fog");
		isTrue(effects.stateOf(wave) != null, "a SHADER layer through its effect and the mask");
		equal(0, effects.pageFogs, "no page fog during a dissolve");
		drawnInTheTint("the mask does the rest");
	}

	/** How much of the colour a slot at {@code ramp} is: up to 1 halfway through its window, back to 0 at its end. */
	private static float labGrade(float ramp)
	{return 1 - Math.abs(2 * ramp - 1);}

	/**
	 * r251: a transfert through a colour begins, in each slot, the outgoing layer until halfway through the slot's window
	 * and the incoming one after, never both, at full opacity, under the colour by as much as labGrade says, the grade
	 * set back to 0 after each; no page fog's shared shader. Outside the transfert, nothing begun and no grade set.
	 */
	void throughColorGradesOneSideOfEachSlotInEveryRepeatMode()
	{
		float stagger = 1;
		TransfertStyle style = TransfertStyle.throughColor(new Color(1, 0.6f, 2, 0.1f), stagger);
		same(TransfertStyle.Kind.THROUGH_COLOR, style.getKind(), "through a colour");
		equal(1, style.getColorB(), 0, "a channel clamped to 1");
		for (int mode = 0; mode < 4; mode++)
		{
			String repeat = REPEATS[mode];
			ParallaxPageReader reader = inRepeatMode(mode);
			TransfertEffects effects = new TransfertEffects(true);
			List<ParallaxLayer> from = widened(0.3f, 3), into = widened(0.5f, 3);
			startsFromNothingBegun(reader, effects, from, into, style, repeat);
			for (float t : new float[] { 0.25f, 0.5f, 0.75f })
			{
				stepAndDraw(reader, effects, 0.25f);
				isTrue(reader.isInTransfer(), repeat + " at " + t + ": in transfer");
				int shown = 0;
				for (int slot = 0; slot < 3; slot++)
				{
					float ramp = labRamp(t, stagger, slot, 3);
					boolean incoming = ramp >= 0.5f;
					for (int side = 0; side < 2; side++)
					{
						ParallaxLayer layer = (side == 0 ? from : into).get(slot);
						String what = repeat + " at " + t + (side == 0 ? ", outgoing" : ", incoming") + " slot " + slot;
						float[] grade = effects.stateOf(layer);
						if (incoming != (side == 1))
						{
							isTrue(grade == null, what + ": the other side of the slot is not begun");
							drawnAt(side == 0 ? 0.3f : 0.5f, slot, 0, what);
							continue;
						}
						shown++;
						isTrue(grade != null, what + ": begun");
						equal(1, grade[0], 0, what + ": red");
						equal(0.6f, grade[1], 0, what + ": green");
						equal(1, grade[2], 0, what + ": blue");
						equal(labGrade(ramp), grade[3], 1e-6f, what + ": the slot's grade");
						drawnAt(side == 0 ? 0.3f : 0.5f, slot, 1, what);
					}
				}
				equal(3, shown, repeat + " at " + t + ": one side of each slot");
				equal(shown, effects.layers.size(), repeat + " at " + t + ": one begin per layer shown");
				equal(2 * shown, effects.setCalls, repeat + " at " + t + ": a grade set, and set back to 0, per layer");
				equal(0, effects.state[3], 0, repeat + " at " + t + ": no grade left on after the draw");
				equal(0, effects.pageFogs, repeat + ": no page fog through a colour");
			}
			endsPlain(reader, effects, 0.25f, repeat);
		}
	}

	/**
	 * An engine that draws no grade (setGrade false, as a LayerEffects written before it), and an EMPTY layer in any
	 * engine: the side of the slot shown fades out by the grade, to the gradients going through the colour.
	 */
	void throughColorFadesWhatTheEngineCannotGrade()
	{
		ParallaxPageReader reader = reader(true, false);
		RecordingEffects before = new RecordingEffects();
		reader.setLayerEffects(before);
		reader.addLayers(widened(0.3f, 3));
		reader.addLayersTransfert(page(widened(0.5f, 3)), null, 1, TransfertStyle.throughColor(Color.WHITE, 2));
		reader.act(0.5f, 0, 0);
		draws.clear();
		reader.draw(camera, batch);
		for (int slot = 0; slot < 3; slot++)
		{
			float ramp = labRamp(0.5f, 2, slot, 3);
			boolean incoming = ramp >= 0.5f;
			drawnAt(0.3f, slot, incoming ? 0 : 1 - labGrade(ramp), "no grade in the engine, outgoing slot " + slot);
			drawnAt(0.5f, slot, incoming ? 1 - labGrade(ramp) : 0, "no grade in the engine, incoming slot " + slot);
		}
		equal(0, before.runs.size(), "nothing begun");

		ParallaxPageReader hooked = reader(true, false);
		TransfertEffects effects = new TransfertEffects(true);
		hooked.setLayerEffects(effects);
		float[] alpha = alphaHook(hooked);
		ParallaxLayer back = layer(0);
		hooked.addLayers(list(ParallaxLayer.empty("slot", 0.3f), back));
		hooked.addLayersTransfert(page(list(layer(0), layer(0))), null, 1, TransfertStyle.throughColor(Color.WHITE, 0));
		hooked.act(0.25f, 0, 0);
		hooked.draw(camera, batch);
		equal(0.5f, alpha[0], 1e-6f, "the hook fades out by the grade");
		isTrue(effects.stateOf(back) != null, "the image layer is graded");
		equal(1, effects.layers.size(), "the EMPTY layer is not begun");
	}

	/**
	 * Each page's layers keep their own fog under the grade (the shaders mix the colour in after it), begun one by one
	 * (no page fog's shared shader), a SHADER layer through its effect; drawn in the tint; the batch white after.
	 */
	void throughColorKeepsEachPagesFogAndTheTint()
	{
		ParallaxPageReader reader = reader(true, false);
		TransfertEffects effects = new TransfertEffects(true);
		ParallaxLayer[] l = fogPages(reader, effects, TransfertStyle.throughColor(Color.WHITE, 0));
		ParallaxLayer oldBack = l[0], oldFront = l[1], newBack = l[2], wave = l[3];
		stepAndDraw(reader, effects, 0.5f);
		equal(fog(0.1f, 0.01f, 0.1f), effects.fogs.get(effects.layers.indexOf(oldBack)), 1e-6f, "the outgoing page: its own fog");
		equal(0, effects.fogs.get(effects.layers.indexOf(oldFront)), 0, "a front layer begun with no fog");
		equal(0.5f, effects.stateOf(oldBack)[3], 1e-6f, "a quarter in: half the colour");
		isTrue(effects.stateOf(newBack) == null, "the incoming page not drawn yet");
		stepAndDraw(reader, effects, 1);
		equal(fog(0.02f, 0.01f, 0.2f), effects.fogs.get(effects.layers.indexOf(newBack)), 1e-6f, "the incoming page: its own");
		equal(0.5f, effects.stateOf(newBack)[3], 1e-6f, "three quarters in: half the colour");
		isTrue(effects.stateOf(wave) != null, "a SHADER layer through its effect and the grade");
		isTrue(effects.stateOf(oldBack) == null, "the outgoing page no longer drawn");
		equal(0, effects.pageFogs, "no page fog through a colour");
		drawnInTheTint("the grade does the rest");
	}

	/**
	 * The gradients: straight from the old colour to the new with any style but THROUGH_COLOR, which goes to its colour
	 * by the middle, at the back slot's pace (stagger 1: twice as fast), and out of it into the new one.
	 */
	void throughColorTakesTheGradientsThroughItsColor()
	{
		Color from = new Color(0, 0, 0.5f, 1), to = new Color(0.4f, 0.2f, 0, 1), out = new Color();
		TransfertStyle white = TransfertStyle.throughColor(Color.WHITE, 0);
		white.gradient(from, to, 0.25f, out);
		isTrue(out.equals(new Color(0.5f, 0.5f, 0.75f, 1)), "a quarter in: halfway from the old colour to white, " + out);
		white.gradient(from, to, 0.5f, out);
		isTrue(out.equals(Color.WHITE), "the middle: white, " + out);
		white.gradient(from, to, 0.75f, out);
		isTrue(out.equals(new Color(0.7f, 0.6f, 0.5f, 1)), "three quarters in: halfway from white to the new colour, " + out);
		white.gradient(from, to, 1, out);
		isTrue(out.equals(to), "the end: the new colour, " + out);
		TransfertStyle.throughColor(Color.WHITE, 1).gradient(from, to, 0.25f, out);
		isTrue(out.equals(Color.WHITE), "stagger 1: the back slot's pace, white a quarter in, " + out);
		TransfertStyle.FADE.gradient(from, to, 0.5f, out);
		isTrue(out.equals(new Color(0.2f, 0.1f, 0.25f, 1)), "a plain fade: straight, " + out);
		TransfertStyle.depthStagger(1).gradient(from, to, 0.5f, out);
		isTrue(out.equals(new Color(0.2f, 0.1f, 0.25f, 1)), "a depth stagger: straight too, " + out);
	}

	/**
	 * How much mist a fog creep's slot is under at {@code t}: its window's ramp over 2t going in, 2 - 2t coming out,
	 * smoothstepped; the far slots ahead by {@code stagger}.
	 */
	private static float labCreep(float t, float stagger, int slot, int total)
	{
		float r = labRamp(t < 0.5f ? 2 * t : 2 - 2 * t, stagger, slot, total);
		return r * r * (3 - 2 * r);
	}

	/**
	 * r252: a fog creep begins the outgoing page's layers until the transfert's middle and the incoming page's after, every
	 * slot at once, never both; each under the mist by as much as labCreep says, the far slots deeper going in and still
	 * deeper coming out; every slot all mist at the middle. No page fog's shared shader, the grade set back after each.
	 */
	void fogCreepSinksTheFarLayersFirstAndSwapsAtTheMiddleInEveryRepeatMode()
	{
		float stagger = 1;
		TransfertStyle style = TransfertStyle.fogCreep(new Color(0.9f, 0.95f, 1.5f, 0.2f), stagger);
		same(TransfertStyle.Kind.FOG_CREEP, style.getKind(), "a fog creep");
		equal(1, style.getColorB(), 0, "a channel clamped to 1");
		for (int mode = 0; mode < 4; mode++)
		{
			String repeat = REPEATS[mode];
			ParallaxPageReader reader = inRepeatMode(mode);
			TransfertEffects effects = new TransfertEffects(true);
			reader.setLayerEffects(effects);
			List<ParallaxLayer> from = widened(0.3f, 3), into = widened(0.5f, 3);
			reader.addLayers(from);
			reader.addLayersTransfert(page(into), null, 1, style);
			for (float t : new float[] { 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f })
			{
				stepAndDraw(reader, effects, 0.125f);
				boolean incoming = t >= 0.5f;
				float deeper = 2;
				for (int slot = 0; slot < 3; slot++)
				{
					String what = repeat + " at " + t + " slot " + slot;
					isTrue(effects.stateOf((incoming ? from : into).get(slot)) == null, what + ": the other page not begun");
					drawnAt(incoming ? 0.3f : 0.5f, slot, 0, what);
					float[] grade = effects.stateOf((incoming ? into : from).get(slot));
					isTrue(grade != null, what + ": begun");
					equal(0.9f, grade[0], 0, what + ": red");
					equal(0.95f, grade[1], 0, what + ": green");
					equal(1, grade[2], 0, what + ": blue");
					equal(labCreep(t, stagger, slot, 3), grade[3], 1e-6f, what + ": the slot's mist");
					isTrue(grade[3] <= deeper, what + ": no deeper in the mist than the slot behind it");
					if (t == 0.5f)
						equal(1, grade[3], 0, what + ": all mist at the middle");
					deeper = grade[3];
					drawnAt(incoming ? 0.5f : 0.3f, slot, 1, what);
				}
				equal(3, effects.layers.size(), repeat + " at " + t + ": one begin per slot");
				equal(0, effects.state[3], 0, repeat + " at " + t + ": no grade left on after the draw");
				equal(0, effects.pageFogs, repeat + ": no page fog in a fog creep");
			}
			endsPlain(reader, effects, 0.125f, repeat);
		}
		// The far slot is under some mist before the near one is, and still is after the near one has cleared.
		isTrue(labCreep(0.125f, stagger, 0, 3) > 0 && labCreep(0.125f, stagger, 2, 3) == 0, "the far layers first");
		isTrue(labCreep(0.875f, stagger, 0, 3) > 0 && labCreep(0.875f, stagger, 2, 3) == 0, "the near layers clear first");
	}

	/** A fog creep's gradients: into the mist at the far slot's pace, all mist at the middle, out of it into the new colour. */
	void fogCreepTakesTheGradientsAtTheFarLayersPace()
	{
		Color from = new Color(0, 0, 0.5f, 1), to = new Color(0.4f, 0.2f, 0, 1), out = new Color();
		TransfertStyle mist = TransfertStyle.fogCreep(Color.WHITE, 0);
		mist.gradient(from, to, 0.25f, out);
		isTrue(out.equals(new Color(0.5f, 0.5f, 0.75f, 1)), "a quarter in: halfway from the old colour to the mist, " + out);
		mist.gradient(from, to, 0.5f, out);
		isTrue(out.equals(Color.WHITE), "the middle: the mist, " + out);
		mist.gradient(from, to, 0.75f, out);
		isTrue(out.equals(new Color(0.7f, 0.6f, 0.5f, 1)), "three quarters in: halfway from the mist to the new colour, " + out);
		mist.gradient(from, to, 0.125f, out);
		isTrue(out.b > 0.5f && out.b < 0.6f, "an eighth in: eased, under a fifth of the way to the mist, " + out);
		mist.gradient(from, to, 1, out);
		isTrue(out.equals(to), "the end: the new colour, " + out);
		TransfertStyle.fogCreep(Color.WHITE, 1).gradient(from, to, 0.25f, out);
		isTrue(out.equals(Color.WHITE), "stagger 1: the far slot's pace, all mist a quarter in, " + out);
	}
}
