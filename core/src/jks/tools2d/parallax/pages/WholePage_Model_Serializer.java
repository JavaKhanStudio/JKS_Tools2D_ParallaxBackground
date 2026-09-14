package jks.tools2d.parallax.pages;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * .plax layout (Kryo, references on, so Kryo writes a reference marker before each object):
 * <ul>
 * <li>version 1 (2019-2023 editor): the page starts directly with its first color, whose reference marker is always
 * {@code 0x01}. No version number, and flipY is not stored.</li>
 * <li>version 2: {@link #VERSION_MARKER} and a version number, then the version 1 layout with flipY stored.</li>
 * <li>version 3: the version 2 layout with each layer's mirror stored after padYFactor.</li>
 * </ul>
 */
public class WholePage_Model_Serializer extends Serializer<WholePage_Model>
{
	/** Never a valid first byte in version 1, where the first color's reference marker is always 0x01. */
	static final byte VERSION_MARKER = (byte) 0xF2;
	static final int CURRENT_VERSION = 3;
	static final String VERSION_KEY = "plaxFormatVersion";

	private final int writeVersion;

	public WholePage_Model_Serializer()
	{this(CURRENT_VERSION);}

	/** Writes an older format (2 or more), so tests can produce the files earlier releases wrote. */
	WholePage_Model_Serializer(int writeVersion)
	{this.writeVersion = writeVersion;}

	@Override
	public void write(Kryo kryo, Output output, WholePage_Model page)
	{
		output.writeByte(VERSION_MARKER);
		output.writeVarInt(writeVersion, true);
		setVersion(kryo, writeVersion);

		kryo.writeObject(output, page.topHalf_top);
		kryo.writeObject(output, page.topHalf_bottom);
		kryo.writeObject(output, page.bottomHalf_top);
		kryo.writeObject(output, page.bottomHalf_bottom);

		output.writeFloat(page.topHalfSize);
		output.writeFloat(page.bottomHalfSize);

		output.writeBoolean(page.repeatOnX);
		output.writeBoolean(page.repeatOnY);

		kryo.writeObject(output, page.pageModel);
	}

	@Override
	public WholePage_Model read(Kryo kryo, Input input, Class<? extends WholePage_Model> type)
	{
		WholePage_Model page = new WholePage_Model();

		int version = 1;
		if (input.readByte() == VERSION_MARKER)
		{
			version = input.readVarInt(true);
			if (version > CURRENT_VERSION)
				throw new KryoException("This .plax file was written by a newer version (format " + version + ")");
		}
		else
		{
			// Version 1: that byte was the first color's reference marker, give it back to Kryo.
			input.setPosition(input.position() - 1);
		}
		setVersion(kryo, version);

		page.topHalf_top = kryo.readObject(input, Color.class);
		page.topHalf_bottom = kryo.readObject(input, Color.class);
		page.bottomHalf_top = kryo.readObject(input, Color.class);
		page.bottomHalf_bottom = kryo.readObject(input, Color.class);

		page.topHalfSize = input.readFloat();
		page.bottomHalfSize = input.readFloat();

		page.repeatOnX = input.readBoolean();
		page.repeatOnY = input.readBoolean();

		page.pageModel = kryo.readObject(input, Page_Model.class);
		return page;
	}

	/** Format version of the page currently being read or written, 1 when a layer is serialized on its own. */
	@SuppressWarnings("unchecked")
	static int currentVersion(Kryo kryo)
	{
		Object version = kryo.getGraphContext().get(VERSION_KEY);
		return version instanceof Integer ? (Integer) version : 1;
	}

	/** Kryo clears its graph context after each top-level read/write, so this only lasts for the current page. */
	@SuppressWarnings("unchecked")
	private static void setVersion(Kryo kryo, int version)
	{kryo.getGraphContext().put(VERSION_KEY, version);}
}
