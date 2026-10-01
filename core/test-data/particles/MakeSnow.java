import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.utils.Array;
import java.io.*;
/**
 * Writes a falling-snow .p with libGDX's own writer, from a spawn line WIDTH world units wide whose flakes live LIFE ms:
 * java -cp gdx.jar MakeSnow.java > snow.p (12, 3000), and java -cp gdx.jar MakeSnow.java 46 9000 > snow-wide.p.
 */
public class MakeSnow {
  public static void main(String[] a) throws Exception {
    float width = a.length > 0 ? Float.parseFloat(a[0]) : 12, life = a.length > 1 ? Float.parseFloat(a[1]) : 3000;
    ParticleEmitter e = new ParticleEmitter();
    e.setName("snow");
    e.setMaxParticleCount((int) (life / 1000 * 20 * width / 12));
    e.setMinParticleCount(0);
    e.getDuration().setLow(1000);
    e.setContinuous(true);
    e.getEmission().setHigh(20 * width / 12);
    e.getLife().setHigh(life);
    e.getXScale().setHigh(0.4f);
    e.getVelocity().setActive(true);
    e.getVelocity().setHigh(2, 4);
    e.getAngle().setActive(true);
    e.getAngle().setHigh(260, 280);
    e.getSpawnShape().setShape(ParticleEmitter.SpawnShape.line);
    e.getSpawnWidth().setHigh(width);
    e.getTransparency().setHigh(1);
    e.getTint().setColors(new float[]{1,1,1});
    e.setAdditive(false);
    e.setImagePaths(new Array<>(new String[]{"snowflake.png"}));
    ParticleEffect effect = new ParticleEffect();
    effect.getEmitters().add(e);
    Writer w = new OutputStreamWriter(System.out);
    effect.save(w);
    w.flush();
  }
}
