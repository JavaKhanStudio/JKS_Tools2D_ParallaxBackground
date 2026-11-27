import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;

/** Prints what libGDX's own parser reads of each atlas named, one line a region, in libGDX's order: atlas-check.sh
 * compares it with what the Godot reader's parser reads (atlas_list.gd). */
public class AtlasList {
	public static void main(String[] args) {
		for (String path : args) {
			System.out.println("== " + path);
			FileHandle atlas = new FileHandle(path);
			TextureAtlasData data = new TextureAtlasData(atlas, atlas.parent(), false);
			for (TextureAtlasData.Region r : data.getRegions())
				System.out.println(r.name + " at " + r.left + "," + r.top + " size "
						+ r.width + "x" + r.height + " orig " + r.originalWidth + "x" + r.originalHeight + " offset "
						+ (int)r.offsetX + "," + (int)r.offsetY + " rotate " + r.rotate);
		}
	}
}
