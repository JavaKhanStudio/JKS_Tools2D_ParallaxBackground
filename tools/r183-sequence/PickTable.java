import java.util.Random;

import jks.tools2d.parallax.SequenceCycle;

/**
 * java -cp core/build/classes/java/main tools/r183-sequence/PickTable.java > engines/godot/tests/sequence/picks.json:
 * SequenceCycle's picks for edge seeds (0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE) and random ones, over weights of
 * every shape (zero, negative, one segment, large), so tests/sequence_cycle.gd holds Godot's port to the JVM's, bit
 * for bit, beyond ReaderCases' pinned lists (r183).
 */
public class PickTable
{
	public static void main(String[] args)
	{
		int[][] weightSets = { { 1, 2, 3 }, { 25, 50, 25 }, { 1, 1 }, { 5 }, { 0, 5, -3 }, { 0, 0 }, { 7, 0, 1, 1000, 3 },
				{ 1000000, 1, 2000000 }, { 3, -2, 4, 1 }, { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 } };
		int[] seeds = new int[24];
		seeds[0] = 0;
		seeds[1] = -1;
		seeds[2] = Integer.MIN_VALUE;
		seeds[3] = Integer.MAX_VALUE;
		seeds[4] = SequenceCycle.ZERO_SEED;
		Random random = new Random(183);
		for (int i = 5; i < seeds.length; i++)
			seeds[i] = random.nextInt();

		StringBuilder out = new StringBuilder("{\"next\": [");
		for (int i = 0; i < seeds.length; i++)
			out.append(i == 0 ? "" : ", ").append("[").append(seeds[i]).append(", ").append(SequenceCycle.next(seeds[i])).append("]");
		out.append("],\n \"cycles\": [\n");
		boolean first = true;
		for (int[] weights : weightSets)
			for (int seed : seeds)
			{
				int[] cycle = new int[64];
				SequenceCycle.draw(seed, weights, cycle);
				out.append(first ? "" : ",\n").append("  {\"seed\": ").append(seed).append(", \"weights\": ").append(list(weights))
						.append(", \"cycle\": ").append(list(cycle)).append("}");
				first = false;
			}
		System.out.println(out.append("\n ]}"));
	}

	private static String list(int[] values)
	{
		StringBuilder out = new StringBuilder("[");
		for (int i = 0; i < values.length; i++)
			out.append(i == 0 ? "" : ", ").append(values[i]);
		return out.append("]").toString();
	}
}
