import java.io.FileOutputStream;

import com.badlogic.gdx.files.FileHandle;
import com.esotericsoftware.kryo.io.Output;

import jks.tools2d.parallax.heart.GVars_Serialization;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * java -cp CORE ToPlax.java IN.jplax OUT.plax ATLAS_NAME [--plain]: the page as a .plax, its atlas renamed (the stress
 * run finds it from its assets folder), its SHADER layers drawn as plain images when asked: the run's control, the same
 * pixels without the shaders and their flushes.
 */
public class ToPlax
{
	public static void main(String[] args) throws Exception
	{
		WholePage_Model page = Utils_Page_Json.loadPage(new FileHandle(args[0]));
		page.pageModel.atlasName = args[2];
		if (args.length > 3 && args[3].equals("--plain"))
			for (Parallax_Model layer : page.pageModel.pageList)
				if (layer.kind == Enum_LayerKind.SHADER)
					layer.kind = Enum_LayerKind.IMAGE;
		GVars_Serialization.init();
		try (Output output = new Output(new FileOutputStream(args[1])))
		{GVars_Serialization.kryo.writeObject(output, page);}
	}
}
