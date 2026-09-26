package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.graphics.Color;

import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * Every stored field of a page, one per line: what PlaxFormatTest.assertPageEquals compares. The JVM writes it for the
 * page Kryo reads, the browser for the page Utils_Page_Json reads, and the two must be equal. A float is compared by its
 * bits: JavaScript prints 6f as "6" where the JVM prints "6.0". Its value follows a '#', for the reader only.
 */
public final class PageDump
{
	private PageDump()
	{}

	public static String of(WholePage_Model page)
	{
		StringBuilder out = new StringBuilder();
		line(out, "topHalf_top", page.topHalf_top);
		line(out, "topHalf_bottom", page.topHalf_bottom);
		line(out, "bottomHalf_top", page.bottomHalf_top);
		line(out, "bottomHalf_bottom", page.bottomHalf_bottom);
		line(out, "topHalfSize", page.topHalfSize);
		line(out, "bottomHalfSize", page.bottomHalfSize);
		line(out, "repeatOnX", page.repeatOnX);
		line(out, "repeatOnY", page.repeatOnY);
		line(out, "useOriginalSize", page.useOriginalSize);
		line(out, "atlasName", page.pageModel.atlasName);
		line(out, "outside", page.pageModel.outside);
		line(out, "layers", (Object) page.pageModel.pageList.size());
		for (int i = 0; i < page.pageModel.pageList.size(); i++)
		{
			Parallax_Model layer = page.pageModel.pageList.get(i);
			String at = "layer " + i + " ";
			line(out, at + "regionName", layer.regionName);
			line(out, at + "regionPosition", (Object) layer.regionPosition);
			line(out, at + "flipX", layer.flipX);
			line(out, at + "flipY", layer.flipY);
			line(out, at + "mirror", layer.mirror);
			line(out, at + "parallaxScalingSpeedX", layer.parallaxScalingSpeedX);
			line(out, at + "parallaxScalingSpeedY", layer.parallaxScalingSpeedY);
			line(out, at + "speedXAtRest", layer.speedXAtRest);
			line(out, at + "sizeRatio", layer.sizeRatio);
			line(out, at + "decal_X_Ratio", layer.decal_X_Ratio);
			line(out, at + "decal_Y_Ratio", layer.decal_Y_Ratio);
			line(out, at + "padX", layer.padX);
			line(out, at + "padXFactor", layer.padXFactor);
			line(out, at + "padY", layer.padY);
			line(out, at + "padYFactor", layer.padYFactor);
		}
		return out.toString();
	}

	private static void line(StringBuilder out, String name, Object value)
	{out.append(name).append(" = ").append(value).append('\n');}

	/** Color.equals compares the rgba8888 int, which is what Color.toString prints. */
	private static void line(StringBuilder out, String name, Color value)
	{line(out, name, (Object) (value == null ? "null" : value.toString()));}

	private static void line(StringBuilder out, String name, float value)
	{line(out, name, (Object) (Integer.toHexString(Float.floatToIntBits(value)) + " # " + value));}

	/** The first line where the two dumps differ, for a failure message that says which field. */
	public static String firstDifference(String expected, String actual)
	{
		String[] want = expected.split("\n"), got = actual.split("\n");
		for (int i = 0; i < Math.max(want.length, got.length); i++)
		{
			String a = i < want.length ? want[i] : "(nothing)", b = i < got.length ? got[i] : "(nothing)";
			if (!compared(a).equals(compared(b)))
				return "expected <" + a + "> but was <" + b + ">";
		}
		return null;
	}

	private static String compared(String line)
	{
		int comment = line.indexOf(" # ");
		return comment < 0 ? line : line.substring(0, comment);
	}
}
