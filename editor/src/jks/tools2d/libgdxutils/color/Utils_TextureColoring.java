//package jks.tools2d.libgdxutils.color;
//
//import com.badlogic.gdx.Game;
//import com.badlogic.gdx.graphics.Color;
//import com.badlogic.gdx.graphics.Pixmap;
//import com.badlogic.gdx.graphics.Texture;
//import com.badlogic.gdx.graphics.TextureData;
//
//public class Utils_TextureColoring 
//{
//
//	
//	/**
//	 * Requires a asset's textureName, and requires gray scale colors of the
//	 * parts
//	 * 
//	 * @param texturename
//	 * @param colorBlade
//	 * @param colorEdge
//	 * @param colorAffinity
//	 * @param colorGrip
//	 * @return
//	 */
//	private static Texture genTexture(Texture tex, int colorBlade,
//	        int colorEdge, int colorAffinity, int colorGrip, int colorExtra) {
//
//	    TextureData textureData = tex.getTextureData();
//	    textureData.prepare();
//
//	    Color tintBlade = chooseColor(mainColors);
//	    Color tintEdge = new Color(tintBlade.r + 0.1f, tintBlade.g + 0.1f,
//	            tintBlade.b + 0.1f, 1);
//
//	    Color tintAffinity = chooseColor(affinityColors);
//	    Color tintGrip;
//	    Color tintExtra = chooseColor(extraColors);
//
//	    boolean colorsAreSet = false;
//
//	    do {
//	        tintGrip = chooseColor(mainColors);
//
//	        if (tintAffinity != tintBlade && tintAffinity != tintGrip
//	                && tintGrip != tintBlade) {
//	            colorsAreSet = true;
//	        }
//	    } while (!colorsAreSet);
//
//	    Pixmap pixmap = tex.getTextureData().consumePixmap();
//
//	    for (int y = 0; y < pixmap.getHeight(); y++) {
//	        for (int x = 0; x < pixmap.getWidth(); x++) {
//
//	            Color color = new Color();
//	            Color.rgba8888ToColor(color, pixmap.getPixel(x, y));
//	            int colorInt[] = getColorFromHex(color);
//
//	            if (colorInt[0] == colorBlade && colorInt[1] == colorBlade
//	                    && colorInt[2] == colorBlade) {
//	                pixmap.setColor(tintBlade);
//	                pixmap.fillRectangle(x, y, 1, 1);
//	            } else if (colorInt[0] == colorEdge && colorInt[1] == colorEdge
//	                    && colorInt[2] == colorEdge) {
//	                pixmap.setColor(tintEdge);
//	                pixmap.fillRectangle(x, y, 1, 1);
//	            } else if (colorInt[0] == colorAffinity
//	                    && colorInt[1] == colorAffinity
//	                    && colorInt[2] == colorAffinity) {
//	                pixmap.setColor(tintAffinity);
//	                pixmap.fillRectangle(x, y, 1, 1);
//	            } else if (colorInt[0] == colorGrip && colorInt[1] == colorGrip
//	                    && colorInt[2] == colorGrip) {
//	                pixmap.setColor(tintGrip);
//	                pixmap.fillRectangle(x, y, 1, 1);
//	            }
//	            else if (colorInt[0] == colorExtra && colorInt[1] == colorExtra
//	                && colorInt[2] == colorExtra) {
//	            pixmap.setColor(tintExtra);
//	            pixmap.fillRectangle(x, y, 1, 1);
//	            }
//	        }
//	    }
//
//	    tex = new Texture(pixmap);
//	    textureData.disposePixmap();
//	    pixmap.dispose();
//
//	    return tex;
//	}
//	
//	
//}
