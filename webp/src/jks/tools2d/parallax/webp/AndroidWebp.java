package jks.tools2d.parallax.webp;

import java.lang.reflect.Method;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * WebP on Android: the system's BitmapFactory, which has decoded it since Android 4.3 (alpha and lossless included).
 * Reached by reflection, so this jar builds without an Android SDK and a desktop game never loads android.*. The
 * bitmap is decoded not premultiplied, as libGDX's own reader gives a PNG: premultiplied, a half-transparent texel
 * would lose bits on the way back.
 */
final class AndroidWebp
{
	private AndroidWebp()
	{}

	static Pixmap decode(FileHandle file)
	{
		byte[] bytes = file.readBytes();
		try
		{
			Class<?> factory = Class.forName("android.graphics.BitmapFactory");
			Class<?> options = Class.forName("android.graphics.BitmapFactory$Options");
			Class<?> bitmapClass = Class.forName("android.graphics.Bitmap");
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Object argb8888 = Enum.valueOf((Class) Class.forName("android.graphics.Bitmap$Config"), "ARGB_8888");
			Object opts = options.getConstructor().newInstance();
			options.getField("inPremultiplied").setBoolean(opts, false);
			options.getField("inScaled").setBoolean(opts, false);
			options.getField("inPreferredConfig").set(opts, argb8888);
			Object bitmap = factory.getMethod("decodeByteArray", byte[].class, int.class, int.class, options)
					.invoke(null, bytes, 0, bytes.length, opts);
			if (bitmap == null)
				throw new GdxRuntimeException("Android could not decode WebP " + file);
			int width = (Integer) bitmapClass.getMethod("getWidth").invoke(bitmap);
			int height = (Integer) bitmapClass.getMethod("getHeight").invoke(bitmap);
			int[] argb = new int[width * height];
			Method getPixels = bitmapClass.getMethod("getPixels", int[].class, int.class, int.class, int.class, int.class,
					int.class, int.class);
			getPixels.invoke(bitmap, argb, 0, width, 0, 0, width, height);
			bitmapClass.getMethod("recycle").invoke(bitmap);
			return ParallaxWebp.fromArgb(argb, width, height);
		}
		catch (ReflectiveOperationException e)
		{throw new GdxRuntimeException("Couldn't decode WebP " + file, e);}
	}
}
