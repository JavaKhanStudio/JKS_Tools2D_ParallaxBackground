package jks.tools2d.parallax;

/**
 * Draws a SEQUENCE layer's cycle from its seed and weights (docs/sequence-layers.md). Integers only, so the JVM, the
 * browser (GWT keeps Java's int overflow) and Godot's plax_page.gd (which masks each step to 32 bits) pick the same
 * segments from the same seed: a float would round differently in each.
 */
public final class SequenceCycle
{
	/** What a seed of 0 starts from: xorshift never leaves 0. */
	public static final int ZERO_SEED = 0x6D2B79F5;

	private SequenceCycle()
	{}

	/** The 32-bit xorshift step (13, 17, 5): the state after {@code state}. */
	public static int next(int state)
	{
		state ^= state << 13;
		state ^= state >>> 17;
		state ^= state << 5;
		return state;
	}

	/**
	 * Fills {@code cycle} with segment indexes picked from {@code seed}: for each slot the state steps once, and
	 * {@code (state >>> 1) % totalWeight} walks the weights. A weight of 0 or less is never picked; when none is above
	 * 0, each weighs 1.
	 */
	public static void draw(int seed, int[] weights, int[] cycle)
	{
		int total = 0;
		for (int weight : weights)
			if (weight > 0)
				total += weight;

		int state = seed == 0 ? ZERO_SEED : seed;
		for (int slot = 0; slot < cycle.length; slot++)
		{
			state = next(state);
			int pick = (state >>> 1) % (total > 0 ? total : weights.length);
			int segment = 0;
			for (; segment < weights.length - 1; segment++)
			{
				int weight = total > 0 ? Math.max(weights[segment], 0) : 1;
				if (pick < weight)
					break;
				pick -= weight;
			}
			cycle[slot] = segment;
		}
	}
}
