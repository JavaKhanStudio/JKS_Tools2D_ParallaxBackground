package jks.tools2d.parallax;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

/**
 * libGDX without a window, for core/test: a texture page with no GL behind it, and the Gdx globals as
 * java.lang.reflect.Proxy stubs (core/browser-test cannot use it: GWT has no reflection).
 */
public final class TestGdx
{
	private TestGdx()
	{}

	/** A {@code width} x {@code height} texture page with no GL texture behind it: an atlas's regions can sit on it. */
	public static Texture unbackedTexture(final int width, final int height)
	{
		return new Texture()
		{
			@Override
			public int getWidth()
			{return width;}

			@Override
			public int getHeight()
			{return height;}
		};
	}

	/** A {@code type} whose every method is {@code handler}. */
	public static <T> T stub(Class<T> type, InvocationHandler handler)
	{return type.cast(Proxy.newProxyInstance(TestGdx.class.getClassLoader(), new Class<?>[] { type }, handler));}

	/** A {@code type} whose every method does nothing and returns {@link #defaultValue}. */
	public static <T> T stub(Class<T> type)
	{return stub(type, (proxy, method, args) -> defaultValue(method.getReturnType()));}

	/** What a method of that return type answers when it is stubbed: false, zero or null. */
	public static Object defaultValue(Class<?> type)
	{
		if (type == boolean.class)
			return false;
		if (type == float.class)
			return 0f;
		if (type == long.class)
			return 0L;
		if (type == double.class)
			return 0d;
		if (type.isPrimitive() && type != void.class)
			return 0;
		return null;
	}

	/** Puts back the Gdx globals a test stubbed: app, graphics, files, gl and gl20. */
	public static void restore()
	{
		Gdx.app = null;
		Gdx.graphics = null;
		Gdx.files = null;
		Gdx.gl = Gdx.gl20 = null;
	}
}
