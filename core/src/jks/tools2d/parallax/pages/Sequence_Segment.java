package jks.tools2d.parallax.pages;

/**
 * One image a SEQUENCE layer chains (docs/sequence-layers.md): an atlas region, named as a layer names its region, and
 * how often it is picked. Stored inside its layer since .plax format 8.
 */
public class Sequence_Segment
{
	public String regionName;
	/** Position of the region among the atlas regions sharing {@link #regionName}. */
	public int regionPosition;
	/**
	 * How often the segment is picked, out of the sum of its layer's weights, whatever segment came before it. A weight
	 * of 0 or less is never picked; when no segment's is above 0, each weighs 1.
	 */
	public int weight = 1;

	public Sequence_Segment()
	{}

	public Sequence_Segment(String regionName, int regionPosition, int weight)
	{
		this.regionName = regionName;
		this.regionPosition = regionPosition;
		this.weight = weight;
	}

	public Sequence_Segment copy()
	{return new Sequence_Segment(regionName, regionPosition, weight);}
}
