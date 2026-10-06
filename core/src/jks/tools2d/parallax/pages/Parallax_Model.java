package jks.tools2d.parallax.pages;

import java.util.ArrayList;
import java.util.List;

/** Serialized description of one layer: which atlas region, and how it scrolls. */
public class Parallax_Model
{
	public String regionName;
	/** Position of the region among the atlas regions sharing {@link #regionName}. */
	public int regionPosition;
	public boolean flipX;
	public boolean flipY;

	public float parallaxScalingSpeedX;
	public float parallaxScalingSpeedY;

	public float speedXAtRest;
	public float sizeRatio = 1;
	public float decal_X_Ratio;
	public float decal_Y_Ratio;

	public float padX;
	public float padXFactor;

	public float padY;
	public float padYFactor;

	/** Stored since format 3. */
	public boolean mirror;

	/** What the layer draws; stored since format 5, IMAGE before. */
	public Enum_LayerKind kind = Enum_LayerKind.IMAGE;
	/** The key a game's {@link jks.tools2d.parallax.LayerHook} is registered under; stored since format 5, null before. */
	public String name;

	/** A PARTICLES layer's libGDX effect (a .p), relative to the page's atlas folder; stored since format 6. */
	public String particlesLibgdx;
	/** A PARTICLES layer's Godot scene (a .tscn), relative to the page's atlas folder; stored since format 6. */
	public String particlesGodot;
	/** Where a PARTICLES layer's effect sits as the page scrolls; stored since format 6, LAYER before. */
	public Enum_ParticleAnchor particlesAnchor = Enum_ParticleAnchor.LAYER;

	/** The effect a SHADER layer draws its image through; stored since format 7, WAVE before. */
	public Enum_ShaderEffect shaderEffect = Enum_ShaderEffect.WAVE;
	/** A SHADER layer's strength: WAVE's sideways shift in world units, FOG's thinning from 0 to 1. Stored since format 7. */
	public float shaderAmplitude;
	/** A SHADER layer's scale in world units: WAVE's wavelength, the size of FOG's patches; 0 draws no effect. Format 7. */
	public float shaderWavelength;
	/** How fast a SHADER layer's effect moves, in world units per second. Stored since format 7. */
	public float shaderSpeed;
	/**
	 * A FOG layer's depth haze, 0 to 1: each layer behind it is mixed toward the mist's white by
	 * {@code 1 - (1 - shaderHaze)^n}, n how many layers behind it that layer is. 0 hazes nothing. Stored since format 9.
	 */
	public float shaderHaze;

	/**
	 * The images a SEQUENCE layer chains, each with its weight; the first one sets the layer's height. Stored since
	 * format 8, empty before.
	 */
	public List<Sequence_Segment> sequenceSegments = new ArrayList<>();
	/** What a SEQUENCE layer's cycle is drawn from, unless the game passes its own. Stored since format 8. */
	public int sequenceSeed;
	/** How many segments a SEQUENCE layer's cycle holds before it repeats (1 at least). Stored since format 8, 16 before. */
	public int sequenceLength = 16;

	public boolean isFlipX()
	{return flipX;}

	public void setFlipX(boolean flipX)
	{this.flipX = flipX;}

	public boolean isFlipY()
	{return flipY;}

	public void setFlipY(boolean flipY)
	{this.flipY = flipY;}

	public boolean isMirror()
	{return mirror;}

	public void setMirror(boolean mirror)
	{this.mirror = mirror;}

	public float getParallaxScalingSpeedX()
	{return parallaxScalingSpeedX;}

	public void setParallaxScalingSpeedX(float parallaxScalingSpeedX)
	{this.parallaxScalingSpeedX = parallaxScalingSpeedX;}

	public float getParallaxScalingSpeedY()
	{return parallaxScalingSpeedY;}

	public void setParallaxScalingSpeedY(float parallaxScalingSpeedY)
	{this.parallaxScalingSpeedY = parallaxScalingSpeedY;}

	/** Alias of {@link #speedXAtRest}. */
	public float getSpeed()
	{return speedXAtRest;}

	public void setSpeed(float speed)
	{this.speedXAtRest = speed;}

	public float getSizeRatio()
	{return sizeRatio;}

	public void setSizeRatio(float sizeRatio)
	{this.sizeRatio = sizeRatio;}

	public float getDecal_X_Ratio()
	{return decal_X_Ratio;}

	public void setDecal_X_Ratio(float decal_X_Ratio)
	{this.decal_X_Ratio = decal_X_Ratio;}

	public float getDecal_Y_Ratio()
	{return decal_Y_Ratio;}

	public void setDecal_Y_Ratio(float decal_Y_Ratio)
	{this.decal_Y_Ratio = decal_Y_Ratio;}

	public Enum_LayerKind getKind()
	{return kind;}

	public void setKind(Enum_LayerKind kind)
	{this.kind = kind;}

	public String getName()
	{return name;}

	public void setName(String name)
	{this.name = name;}

	public String getParticlesLibgdx()
	{return particlesLibgdx;}

	public void setParticlesLibgdx(String particlesLibgdx)
	{this.particlesLibgdx = particlesLibgdx;}

	public String getParticlesGodot()
	{return particlesGodot;}

	public void setParticlesGodot(String particlesGodot)
	{this.particlesGodot = particlesGodot;}

	public Enum_ParticleAnchor getParticlesAnchor()
	{return particlesAnchor;}

	public void setParticlesAnchor(Enum_ParticleAnchor particlesAnchor)
	{this.particlesAnchor = particlesAnchor;}

	public Enum_ShaderEffect getShaderEffect()
	{return shaderEffect;}

	public void setShaderEffect(Enum_ShaderEffect shaderEffect)
	{this.shaderEffect = shaderEffect;}

	public float getShaderAmplitude()
	{return shaderAmplitude;}

	public void setShaderAmplitude(float shaderAmplitude)
	{this.shaderAmplitude = shaderAmplitude;}

	public float getShaderWavelength()
	{return shaderWavelength;}

	public void setShaderWavelength(float shaderWavelength)
	{this.shaderWavelength = shaderWavelength;}

	public float getShaderSpeed()
	{return shaderSpeed;}

	public void setShaderSpeed(float shaderSpeed)
	{this.shaderSpeed = shaderSpeed;}

	public float getShaderHaze()
	{return shaderHaze;}

	public void setShaderHaze(float shaderHaze)
	{this.shaderHaze = shaderHaze;}

	public List<Sequence_Segment> getSequenceSegments()
	{return sequenceSegments;}

	public void setSequenceSegments(List<Sequence_Segment> sequenceSegments)
	{this.sequenceSegments = sequenceSegments;}

	public int getSequenceSeed()
	{return sequenceSeed;}

	public void setSequenceSeed(int sequenceSeed)
	{this.sequenceSeed = sequenceSeed;}

	public int getSequenceLength()
	{return sequenceLength;}

	public void setSequenceLength(int sequenceLength)
	{this.sequenceLength = sequenceLength;}

	public String getCompleteRegionName()
	{return regionName + regionPosition;}
}
