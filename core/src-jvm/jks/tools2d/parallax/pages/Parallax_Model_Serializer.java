package jks.tools2d.parallax.pages;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Parallax_Model_Serializer extends Serializer<Parallax_Model>
{
	@Override
	public void write(Kryo kryo, Output output, Parallax_Model model)
	{
		output.writeString(model.regionName);
		output.writeInt(model.regionPosition);
		output.writeBoolean(model.flipX);
		if (WholePage_Model_Serializer.currentVersion(kryo) >= 2)
			output.writeBoolean(model.flipY);

		output.writeFloat(model.parallaxScalingSpeedX);
		output.writeFloat(model.parallaxScalingSpeedY);

		output.writeFloat(model.speedXAtRest);
		output.writeFloat(model.sizeRatio);
		output.writeFloat(model.decal_X_Ratio);
		output.writeFloat(model.decal_Y_Ratio);

		output.writeFloat(model.padX);
		output.writeFloat(model.padXFactor);

		output.writeFloat(model.padY);
		output.writeFloat(model.padYFactor);

		if (WholePage_Model_Serializer.currentVersion(kryo) >= 3)
			output.writeBoolean(model.mirror);

		if (WholePage_Model_Serializer.currentVersion(kryo) >= 5)
		{
			output.writeString(model.kind.name());
			output.writeString(model.name);
		}

		if (WholePage_Model_Serializer.currentVersion(kryo) >= 6)
		{
			output.writeString(model.particlesLibgdx);
			output.writeString(model.particlesGodot);
			output.writeString(model.particlesAnchor.name());
		}
	}

	@Override
	public Parallax_Model read(Kryo kryo, Input input, Class<? extends Parallax_Model> type)
	{
		Parallax_Model model = new Parallax_Model();
		model.regionName = input.readString();
		model.regionPosition = input.readInt();
		model.flipX = input.readBoolean();
		if (WholePage_Model_Serializer.currentVersion(kryo) >= 2)
			model.flipY = input.readBoolean();

		model.parallaxScalingSpeedX = input.readFloat();
		model.parallaxScalingSpeedY = input.readFloat();

		model.speedXAtRest = input.readFloat();
		model.sizeRatio = input.readFloat();
		model.decal_X_Ratio = input.readFloat();
		model.decal_Y_Ratio = input.readFloat();

		model.padX = input.readFloat();
		model.padXFactor = input.readFloat();

		model.padY = input.readFloat();
		model.padYFactor = input.readFloat();

		if (WholePage_Model_Serializer.currentVersion(kryo) >= 3)
			model.mirror = input.readBoolean();

		if (WholePage_Model_Serializer.currentVersion(kryo) >= 5)
		{
			String kind = input.readString();
			try
			{model.kind = Enum_LayerKind.valueOf(kind);}
			catch (IllegalArgumentException e)
			{throw new KryoException("Layer kind " + kind + " is not known here: the .plax was written by a newer version");}
			model.name = input.readString();
		}

		if (WholePage_Model_Serializer.currentVersion(kryo) >= 6)
		{
			model.particlesLibgdx = input.readString();
			model.particlesGodot = input.readString();
			String anchor = input.readString();
			try
			{model.particlesAnchor = Enum_ParticleAnchor.valueOf(anchor);}
			catch (IllegalArgumentException e)
			{throw new KryoException("Particle anchor " + anchor + " is not known here: the .plax was written by a newer version");}
		}

		return model;
	}
}
