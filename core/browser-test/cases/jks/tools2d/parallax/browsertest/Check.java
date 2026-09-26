package jks.tools2d.parallax.browsertest;

/** JUnit's assertions, the few the browser suite needs: JUnit itself does not translate to JavaScript. */
public final class Check
{
	private Check()
	{}

	public static void isTrue(boolean condition, String message)
	{
		if (!condition)
			throw new AssertionError(message);
	}

	public static void isFalse(boolean condition, String message)
	{isTrue(!condition, message);}

	public static void equal(Object expected, Object actual, String message)
	{
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected <" + expected + "> but was <" + actual + ">");
	}

	public static void equal(float expected, float actual, float delta, String message)
	{
		if (!(Math.abs(expected - actual) <= delta))
			throw new AssertionError(message + ": expected <" + expected + "> but was <" + actual + "> (within " + delta + ")");
	}

	public static void same(Object expected, Object actual, String message)
	{
		if (expected != actual)
			throw new AssertionError(message + ": expected the same object");
	}

	public static void notSame(Object unexpected, Object actual, String message)
	{
		if (unexpected == actual)
			throw new AssertionError(message + ": expected another object");
	}
}
