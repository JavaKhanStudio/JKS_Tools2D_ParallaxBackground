import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;

/** Prints what libGDX's own parser reads of an atlas: atlas-parsers.sh runs it on the live link's generated atlas. */
public class AtlasProbe {
	public static void main(String[] args) {
		FileHandle atlas = new FileHandle(args[0]);
		TextureAtlasData data = new TextureAtlasData(atlas, atlas.parent(), false);
		for (TextureAtlasData.Region r : data.getRegions())
			System.out.println(r.name + " " + r.page.textureFile.name() + " " + r.left + "," + r.top + " " + r.width + "x" + r.height
					+ " orig " + r.originalWidth + "x" + r.originalHeight + " index " + r.index);
	}
}
