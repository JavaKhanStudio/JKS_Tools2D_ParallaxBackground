import java.io.FileOutputStream;

import com.badlogic.gdx.files.FileHandle;
import com.esotericsoftware.kryo.io.Output;

import jks.tools2d.parallax.heart.GVars_Serialization;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Sequence_Segment;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * java -cp CORE ToSequencePlax.java IN.jplax OUT.plax ATLAS_NAME [--plain]: the page as a .plax, its atlas renamed (the
 * stress run finds it from its assets folder), its SHADER layers drawn as plain images, and, unless --plain, every
 * image layer of parallax4 turned into a SEQUENCE of Hiver's four parallax4 regions, weighing 1 to 4, 64 slots, a seed
 * each. --plain is the run's control: the same layers as single images.
 */
public class ToSequencePlax
{
	public static void main(String[] args) throws Exception
	{
		WholePage_Model page = Utils_Page_Json.loadPage(new FileHandle(args[0]));
		page.pageModel.atlasName = args[2];
		boolean plain = args.length > 3 && args[3].equals("--plain");
		int sequences = 0;
		for (Parallax_Model layer : page.pageModel.pageList)
		{
			if (layer.kind == Enum_LayerKind.SHADER)
				layer.kind = Enum_LayerKind.IMAGE;
			if (plain || layer.kind != Enum_LayerKind.IMAGE || !"parallax4".equals(layer.regionName))
				continue;
			layer.kind = Enum_LayerKind.SEQUENCE;
			for (int position = 0; position < 4; position++)
				layer.sequenceSegments.add(new Sequence_Segment("parallax4", position, position + 1));
			layer.sequenceSeed = 1000 + sequences++;
			layer.sequenceLength = 64;
		}
		System.out.println(args[1] + ": " + sequences + " sequence layers");
		GVars_Serialization.init();
		try (Output output = new Output(new FileOutputStream(args[1])))
		{GVars_Serialization.kryo.writeObject(output, page);}
	}
}
