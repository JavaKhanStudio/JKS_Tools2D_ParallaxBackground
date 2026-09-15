package jks.tools2d.parallax.pages;

import com.esotericsoftware.kryo.Kryo;
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

		return model;
	}
}
