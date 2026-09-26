package jks.tools2d.parallax.browsertest;

/** One test of the browser suite: a name and a body that throws when it fails. */
public final class BrowserCase
{
	public final String name;
	public final Runnable body;

	public BrowserCase(String name, Runnable body)
	{
		this.name = name;
		this.body = body;
	}
}
