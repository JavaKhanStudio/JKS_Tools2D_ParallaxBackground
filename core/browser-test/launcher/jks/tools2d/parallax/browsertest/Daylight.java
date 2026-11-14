package jks.tools2d.parallax.browsertest;

/**
 * The light of an hour, from the measured clear-sky day (docs/daylight.md; the tables are
 * tools/r425-daylight/daylight.py --java's). Lux: the sun's (its own and its sky's) by its height, the moon's 0.27 lux
 * full and high times its phase's share (Krisciunas and Schaefer) and its height's, and the night's 0.001. The
 * world's brightness is the lux on a log scale between a moonless night and the sun {@code fullFrom} degrees up,
 * raised to {@code curve}, from {@code 1 - nightDark} to 1 (Simon at 11:08: "higher when there is no sun, way lower
 * when the sun is at the zenith [...] not in a linear fashion"). Its colour: the sun's direct light (red low down),
 * the moon's (the sun's, a little redder), the sky's (blue), each by its share, white-balanced to a high sun; at low
 * light, toward a grey blue, as eyes see in the dark (Purkinje). A cast shadow lacks a light's direct share, times
 * {@code darkness}: what is left is the sky's.
 */
final class Daylight
{
	/** A lunar month, days: the moon's age runs over it. */
	static final float MONTH = 29.53f;
	static final float[] LIGHT_HEIGHTS = { -18f, -15f, -12f, -9f, -6f, -4f, -2f, -1f, 0f, 1f, 2f, 3f, 5f, 7.5f, 10f, 15f, 20f, 30f, 45f, 60f, 90f };
	static final float[] LIGHT_LOG_LUX = { -2.80f, -2.50f, -2.05f, -0.78f, 0.53f, 1.27f, 2.01f, 2.38f, 2.75f, 2.89f, 3.22f, 3.43f, 3.71f, 3.94f, 4.11f, 4.34f, 4.49f, 4.70f, 4.88f, 4.99f, 5.06f };
	static final float[] LIGHT_DIRECT = { 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.13f, 0.22f, 0.30f, 0.45f, 0.56f, 0.64f, 0.73f, 0.79f, 0.84f, 0.88f, 0.90f, 0.91f };
	static final float[] LIGHT_FACING = { 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.93f, 0.93f, 0.93f, 0.93f, 0.93f, 0.93f, 0.92f, 0.91f, 0.89f, 0.85f, 0.78f, 0.00f };
	static final float[] LIGHT_SUN = { 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.438f, 0.000f, 1.000f, 0.560f, 0.000f, 1.000f, 0.645f, 0.000f, 1.000f, 0.753f, 0.323f, 1.000f, 0.828f, 0.496f, 1.000f, 0.873f, 0.608f, 1.000f, 0.923f, 0.746f, 1.000f, 0.949f, 0.825f, 1.000f, 0.976f, 0.912f, 1.000f, 0.992f, 0.972f, 1.000f, 1.000f, 1.000f, 0.983f, 0.988f, 1.000f };
	static final float[] LIGHT_SKY = { 0.140f, 0.504f, 1.000f, 0.142f, 0.508f, 1.000f, 0.146f, 0.513f, 1.000f, 0.152f, 0.523f, 1.000f, 0.164f, 0.539f, 1.000f, 0.276f, 0.590f, 1.000f, 0.393f, 0.655f, 1.000f, 0.460f, 0.694f, 1.000f, 0.536f, 0.740f, 1.000f, 0.536f, 0.740f, 1.000f, 0.536f, 0.740f, 1.000f, 0.536f, 0.740f, 1.000f, 0.505f, 0.704f, 1.000f, 0.508f, 0.692f, 1.000f, 0.510f, 0.685f, 1.000f, 0.512f, 0.678f, 1.000f, 0.513f, 0.674f, 1.000f, 0.514f, 0.670f, 1.000f, 0.514f, 0.668f, 1.000f, 0.514f, 0.667f, 1.000f, 0.514f, 0.666f, 1.000f };
	/** A moonless night's light, lux: stars and airglow. A full moon high up: lux. */
	static final float NIGHT_LUX = 0.001f, FULL_MOON_LUX = 0.27f;
	/** The moon's light is the sun's, a little redder (its soil reflects red more than blue). */
	static final float[] MOON_TINT = { 1, 0.93f, 0.84f };
	/** What eyes see in the dark: a grey blue (encoded), and the light's log lux over which they turn to it. */
	static final float[] NIGHT_SEEN = { 0.62f, 0.74f, 1 };
	static final float SEEN_DAY = 1, SEEN_NIGHT = -2;
	/** A moonless night's and the noon sun's log lux. */
	static final float LOG_NIGHT = -3;

	/** The whole light, lux; the world's brightness, 0 to 1; the shares of it that come straight from the sun, the moon. */
	float lux, bright, sunShare, moonShare;
	/** How much darker a sun's, a moon's cast shadow is, 0 to 1 (its encoded luminance's loss), for the readout. */
	float sunCut, moonCut;
	float sunSide, moonSide;
	/** The layers' tint (encoded, the brightness in it); the sky's at its top and at the horizon, over the painted. */
	final float[] tint = new float[3], zenith = new float[3], horizon = new float[3], haze = new float[3];
	/** The glow of a low sun over the horizon (behind you, the pink band opposite it): its colour and amount. */
	final float[] glowColor = new float[3];
	float glow;
	/** What a sun's, a moon's shadow leaves of what is drawn there, per channel (encoded). */
	final float[] sunShade = new float[3], moonShade = new float[3];
	/** The sun's disc colour; the moon's, and its opacity (paler by day). */
	final float[] sunDisc = new float[3], moonDisc = new float[4];
	private final float[] a = new float[3], b = new float[3], c = new float[3], litLinear = new float[3], noonLinear = new float[3];

	float[] shadeOf(boolean moon)
	{return moon ? moonShade : sunShade;}

	void set(float sunHeight, float moonHeight, float moonAge, float nightDark, float curve, float fullFrom, float warmth, float nightBlue, float darkness, boolean behind)
	{
		float sunLux = sunLux(sunHeight), moonLux = moonLux(moonHeight, moonAge);
		lux = sunLux + moonLux + NIGHT_LUX;
		float sunDirect = sunLux * table(LIGHT_DIRECT, sunHeight), moonDirect = moonLux * table(LIGHT_DIRECT, moonHeight);
		sunShare = sunDirect / lux;
		moonShare = moonDirect / lux;
		// The light's colour, linear: each part by its lux, its colour of luminance 1. Ahead of you, the faces you see
		// take the sun's light as level ground does; behind you, they are turned to it, and take far more of it low
		// down (LIGHT_FACING: the golden hour), eased in over its first 3 degrees, as the disc clears the horizon's haze.
		float sunLit = behind ? sunLux * table(LIGHT_FACING, sunHeight) * smooth(sunHeight / 3) : sunDirect;
		float moonLit = behind ? moonLux * table(LIGHT_FACING, moonHeight) * smooth(moonHeight / 3) : moonDirect;
		lightLinear(litLinear, sunHeight, sunLit, sunLux - sunLit, moonHeight, moonLit, moonLux - moonLit, NIGHT_LUX, warmth);
		lightLinear(noonLinear, 60, 0.9f, 0.1f, 0, 0, 0, 0, warmth);
		float s = clamp((log10(lux) - LOG_NIGHT) / (LIGHT_LOG_LUX_AT(fullFrom) - LOG_NIGHT));
		bright = 1 - nightDark + nightDark * (float) Math.pow(s, curve);
		// Night eyes: from SEEN_DAY down to SEEN_NIGHT log lux, the colour turns grey blue.
		float seen = nightBlue * smooth((SEEN_DAY - log10(lux)) / (SEEN_DAY - SEEN_NIGHT));
		for (int i = 0; i < 3; i++)
			c[i] = litLinear[i] / noonLinear[i];
		normalise(c);
		encode(c);
		float grey = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2];
		for (int i = 0; i < 3; i++)
			tint[i] = bright * (c[i] + (grey * NIGHT_SEEN[i] - c[i]) * seen);
		// A cast shadow: the light without (darkness of) that body's direct part.
		cut(sunShade, sunHeight, darkness * sunDirect, false, moonHeight, warmth);
		cut(moonShade, moonHeight, darkness * moonDirect, true, sunHeight, warmth);
		sunCut = 1 - luminance(sunShade);
		moonCut = 1 - luminance(moonShade);
		// The sky: its colour at this sun's height over its colour at noon (the page's painted sky), then night eyes;
		// at the horizon, the glow of a low sun (red-gold) where it is, ahead; behind you, the pink band opposite it.
		tableColor(LIGHT_SKY, sunHeight, a);
		tableColor(LIGHT_SKY, 60, b);
		for (int i = 0; i < 3; i++)
		{
			a[i] = (float) Math.pow(a[i], 2.2) / (float) Math.pow(b[i], 2.2);
			a[i] = 1 + (a[i] - 1) * Math.min(1, warmth);
		}
		normalise(a);
		encode(a);
		grey = 0.2126f * a[0] + 0.7152f * a[1] + 0.0722f * a[2];
		for (int i = 0; i < 3; i++)
			zenith[i] = bright * (a[i] + (grey * NIGHT_SEEN[i] - a[i]) * seen);
		float glow = Math.min(1, warmth) * (float) Math.exp(-sunHeight * sunHeight / 30f) * (behind ? 0.4f : 0.8f);
		tableColor(LIGHT_SUN, Math.max(0, sunHeight), b);
		if (behind)
		{
			b[0] = 1;
			b[1] = 0.72f;
			b[2] = 0.78f;
		}
		for (int i = 0; i < 3; i++)
		{
			horizon[i] = zenith[i] + (bright * b[i] - zenith[i]) * glow;
			// The air between: lit as the sky, the glow faint in it (the far peaks are mostly fog).
			haze[i] = zenith[i] + (bright * b[i] - zenith[i]) * glow * 0.2f;
			glowColor[i] = bright * b[i];
		}
		this.glow = glow;
		// The discs: the sun in its own colour; the moon reddened as low, paler by day.
		tableColor(LIGHT_SUN, Math.max(0, sunHeight), sunDisc);
		tableColor(LIGHT_SUN, Math.max(0, moonHeight), b);
		for (int i = 0; i < 3; i++)
			moonDisc[i] = 0.35f + 0.65f * b[i];
		moonDisc[3] = 1 - 0.65f * clamp((log10(sunLux + 1e-6f) - 0.5f) / 2.5f);
	}

	/** Into {@code out}, linear: each light's lux times its colour of luminance 1. */
	private void lightLinear(float[] out, float sunHeight, float sunDirect, float sunSky, float moonHeight, float moonDirect, float moonSky, float night, float warmth)
	{
		out[0] = out[1] = out[2] = 0;
		add(out, LIGHT_SUN, Math.max(0, sunHeight), sunDirect, null, warmth);
		add(out, LIGHT_SKY, sunHeight, sunSky, null, warmth);
		add(out, LIGHT_SUN, Math.max(0, moonHeight), moonDirect, MOON_TINT, warmth);
		add(out, LIGHT_SKY, 30, moonSky + night, null, warmth);
	}

	private void add(float[] out, float[] colours, float height, float lux, float[] filter, float warmth)
	{
		if (lux <= 0)
			return;
		tableColor(colours, height, a);
		for (int i = 0; i < 3; i++)
		{
			a[i] = (float) Math.pow(a[i], 2.2) * (filter == null ? 1 : filter[i]);
			// warmth 0: white light; 1: as measured; over 1, more so.
			a[i] = (float) Math.pow(Math.max(1e-4f, a[i]), warmth);
		}
		float l = 0.2126f * a[0] + 0.7152f * a[1] + 0.0722f * a[2];
		for (int i = 0; i < 3; i++)
			out[i] += lux * a[i] / l;
	}

	/** What is left, per channel (encoded), once {@code direct} lux of one body's light is taken off the whole. */
	private void cut(float[] out, float height, float direct, boolean moon, float otherHeight, float warmth)
	{
		if (direct <= 0)
		{
			out[0] = out[1] = out[2] = 1;
			return;
		}
		c[0] = c[1] = c[2] = 0;
		add(c, LIGHT_SUN, Math.max(0, height), direct, moon ? MOON_TINT : null, warmth);
		for (int i = 0; i < 3; i++)
			out[i] = (float) Math.pow(clamp(1 - c[i] / litLinear[i]), 1 / 2.2);
	}

	static float sunLux(float height)
	{return height < -24 ? 0 : Math.max(0, (float) Math.pow(10, table(LIGHT_LOG_LUX, height)) - NIGHT_LUX);}

	/** The moon's lux at that height and age: a full high one's times its phase's share and its height's. */
	static float moonLux(float height, float age)
	{return height <= 0 ? 0 : FULL_MOON_LUX * phaseLight(age) * sunLux(height) / sunLux(90);}

	/** The share of a full moon's light a moon {@code age} days old gives (Krisciunas and Schaefer 1991). */
	static float phaseLight(float age)
	{
		float angle = Math.abs(180 - 360 * (age % MONTH) / MONTH);
		return (float) Math.pow(10, -0.4 * (0.026 * angle + 4e-9 * Math.pow(angle, 4)));
	}

	static String phaseName(float age)
	{
		float f = (age % MONTH) / MONTH;
		String[] names = { "new", "waxing crescent", "first quarter", "waxing gibbous", "full", "waning gibbous", "last quarter", "waning crescent" };
		return names[Math.round(f * 8) % 8] + " (" + Math.round(100 * phaseLight(age)) + "% of a full one's light)";
	}

	static String luxText(float lux)
	{
		if (lux >= 100)
			return String.valueOf(Math.round(lux / 10) * 10);
		if (lux >= 1)
			return String.valueOf(Math.round(lux * 10) / 10f);
		// Two significant digits.
		int places = (int) Math.ceil(-log10(lux)) + 1;
		double k = Math.pow(10, places);
		return String.valueOf(Math.round(lux * k) / k);
	}

	static float LIGHT_LOG_LUX_AT(float height)
	{return table(LIGHT_LOG_LUX, height);}

	static float table(float[] values, float height)
	{
		if (height <= LIGHT_HEIGHTS[0])
			return values[0];
		for (int i = 1; i < LIGHT_HEIGHTS.length; i++)
			if (height <= LIGHT_HEIGHTS[i])
			{
				float t = (height - LIGHT_HEIGHTS[i - 1]) / (LIGHT_HEIGHTS[i] - LIGHT_HEIGHTS[i - 1]);
				return values[i - 1] + (values[i] - values[i - 1]) * t;
			}
		return values[values.length - 1];
	}

	static void tableColor(float[] values, float height, float[] out)
	{
		int last = LIGHT_HEIGHTS.length - 1;
		int i = 1;
		while (i < last && height > LIGHT_HEIGHTS[i])
			i++;
		float t = clamp((height - LIGHT_HEIGHTS[i - 1]) / (LIGHT_HEIGHTS[i] - LIGHT_HEIGHTS[i - 1]));
		for (int k = 0; k < 3; k++)
			out[k] = values[3 * (i - 1) + k] + (values[3 * i + k] - values[3 * (i - 1) + k]) * t;
	}

	private static void normalise(float[] c)
	{
		float max = Math.max(c[0], Math.max(c[1], c[2]));
		for (int i = 0; i < 3; i++)
			c[i] /= max;
	}

	private static void encode(float[] c)
	{
		for (int i = 0; i < 3; i++)
			c[i] = (float) Math.pow(Math.max(0, c[i]), 1 / 2.2);
	}

	private static float luminance(float[] c)
	{return 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2];}

	static float log10(float x)
	{return (float) (Math.log(x) / Math.log(10));}

	static float clamp(float x)
	{return Math.max(0, Math.min(1, x));}

	static float smooth(float x)
	{
		float t = clamp(x);
		return t * t * (3 - 2 * t);
	}
}
