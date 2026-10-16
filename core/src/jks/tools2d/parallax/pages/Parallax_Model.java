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
	 * A FOG layer's depth haze, 0 to 1, as format 9 stored it; no longer written nor drawn (r217): a page read from such
	 * a file takes its largest as its fog's strength ({@link WholePage_Model#getFogStrength}).
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

	/** How a FLOOR layer's rows map its image; stored since format 11, MODE7 before. */
	public Enum_FloorMapping floorMapping = Enum_FloorMapping.MODE7;
	/**
	 * A FLOOR layer's top row's speed ratio X, its bottom row's being the layer's own ({@link #parallaxScalingSpeedX}).
	 * Stored since format 11.
	 */
	public float floorTopSpeed;
	/** How many rows a FLOOR layer is cut in, 2 at least. Stored since format 11, 96 before. */
	public int floorRows = 96;
	/**
	 * How high a FLOOR layer's box is, in percent of the world height; 0: as high as its image makes it, as an IMAGE
	 * layer is. Stored since format 11.
	 */
	public float floorHeight;

	/** What a layer saved before format 12, or without one, darkens toward its tower's sides: the r266 mock's. */
	public static final float DEFAULT_TOWER_SHADE = 0.65f;
	/** An animated patch's frame rate in a layer saved before format 16, or without one. */
	public static final float DEFAULT_ANIM_FPS = 12;
	/** {@link #mirrorDepth} when a page stores none: the beach sea's, about 3 world units of visible water (r419). */
	public static final float DEFAULT_MIRROR_DEPTH = 3;
	/**
	 * The radius of the tower an IMAGE layer is wrapped round, in percent of the world width; 0, the default: none, the
	 * layer is flat. Stored since format 12.
	 */
	public float towerRadius;
	/**
	 * How much a tower's wall darkens as it turns away, 0 to 1: a strip facing the view at angle a is drawn at
	 * {@code 1 - towerShade (1 - cos a)} of its brightness. Stored since format 12, {@link #DEFAULT_TOWER_SHADE} before.
	 */
	public float towerShade = DEFAULT_TOWER_SHADE;
	/**
	 * Where the camera looking at a tower stands, in tower radii from its axis (above 1); 0, the default: far away, the
	 * wall's courses straight and the page behind it flat. Above 1, the wall's strips are scaled up and down about the
	 * view's middle by their distance, so its courses arc, and the page's layers slower than the tower wrap round it as
	 * panoramas (docs/depth-tricks.md §2, r350). Stored since format 14.
	 */
	public float towerCamera;

	/**
	 * Where a LURKER layer's draws start, as {@link #sequenceSeed} does (0 starts from SequenceCycle's zero seed). Stored
	 * since format 15.
	 */
	public int lurkerSeed;
	/**
	 * After a LURKER layer's figure has gone, each later sighting has 1 chance in this of showing it; 1 at least.
	 * Stored since format 15, 3 before.
	 */
	public int lurkerOdds = 3;
	/**
	 * Where the layers in front of a LURKER layer cover its figure wholly, worked out by the editor when it saves:
	 * triplets (how many layers in front of the lurker that layer is, from, to), the relative offset in world units of
	 * the page's saved world. Empty: the figure switches only outside the view. Stored since format 15.
	 */
	public float[] lurkerCover = new float[0];

	/**
	 * An IMAGE or SHADER layer's animated patch (docs/animated-layers.md): the name of its frames in the page's atlas,
	 * the regions of that name in atlas order, as many frames as there are; empty (or null): none. Stored since format
	 * 16.
	 */
	public String animRegion = "";
	/**
	 * Where the patch's bottom-left corner lies in the layer's region, in that region's pixels from its bottom-left:
	 * drawn at the tile's corner plus {@code animX} times the drawn width over the region's, and so on Y. Stored since
	 * format 16.
	 */
	public int animX, animY;
	/** The patch's frames a second, on the reader's clock. Stored since format 16, {@link #DEFAULT_ANIM_FPS} before. */
	public float animFps = DEFAULT_ANIM_FPS;

	/**
	 * How strongly a SHADER layer's painted crests catch the sun after its effect, 0 to 1.5 (light added); 0, the
	 * default: no glint (docs/effect-layers.md, r408). Drawn only with a positive {@link #shaderWavelength}: the glint
	 * loops over the effect's period. Stored since format 17.
	 */
	public float glintStrength;

	/**
	 * Where a MIRROR layer's water meets the sky it mirrors, as a fraction of its image's height from its bottom: 1, the
	 * default, its top edge; under 1, an image whose water fills only its bottom, clear above (the beach's sea: 84 of its
	 * 384 rows, 0.21875). Stored since format 18.
	 */
	public float mirrorHorizon = 1;
	/**
	 * How far under that horizon, in world units, a MIRROR layer's flat water mirrors the top of the page's sky: the
	 * water's depth as seen. Stored since format 18, {@link #DEFAULT_MIRROR_DEPTH} before.
	 */
	public float mirrorDepth = DEFAULT_MIRROR_DEPTH;

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

	public Enum_FloorMapping getFloorMapping()
	{return floorMapping;}

	public void setFloorMapping(Enum_FloorMapping floorMapping)
	{this.floorMapping = floorMapping;}

	public float getFloorTopSpeed()
	{return floorTopSpeed;}

	public void setFloorTopSpeed(float floorTopSpeed)
	{this.floorTopSpeed = floorTopSpeed;}

	public int getFloorRows()
	{return floorRows;}

	public void setFloorRows(int floorRows)
	{this.floorRows = floorRows;}

	public float getFloorHeight()
	{return floorHeight;}

	public void setFloorHeight(float floorHeight)
	{this.floorHeight = floorHeight;}

	public float getTowerRadius()
	{return towerRadius;}

	public void setTowerRadius(float towerRadius)
	{this.towerRadius = towerRadius;}

	public float getTowerShade()
	{return towerShade;}

	public void setTowerShade(float towerShade)
	{this.towerShade = towerShade;}

	public float getTowerCamera()
	{return towerCamera;}

	public void setTowerCamera(float towerCamera)
	{this.towerCamera = towerCamera;}

	public int getLurkerSeed()
	{return lurkerSeed;}

	public void setLurkerSeed(int lurkerSeed)
	{this.lurkerSeed = lurkerSeed;}

	public int getLurkerOdds()
	{return lurkerOdds;}

	public void setLurkerOdds(int lurkerOdds)
	{this.lurkerOdds = lurkerOdds;}

	public float[] getLurkerCover()
	{return lurkerCover;}

	public void setLurkerCover(float[] lurkerCover)
	{this.lurkerCover = lurkerCover;}

	public String getAnimRegion()
	{return animRegion;}

	public void setAnimRegion(String animRegion)
	{this.animRegion = animRegion;}

	public int getAnimX()
	{return animX;}

	public void setAnimX(int animX)
	{this.animX = animX;}

	public int getAnimY()
	{return animY;}

	public void setAnimY(int animY)
	{this.animY = animY;}

	public float getAnimFps()
	{return animFps;}

	public void setAnimFps(float animFps)
	{this.animFps = animFps;}

	public float getGlintStrength()
	{return glintStrength;}

	public void setGlintStrength(float glintStrength)
	{this.glintStrength = glintStrength;}

	public float getMirrorHorizon()
	{return mirrorHorizon;}

	public void setMirrorHorizon(float mirrorHorizon)
	{this.mirrorHorizon = mirrorHorizon;}

	public float getMirrorDepth()
	{return mirrorDepth;}

	public void setMirrorDepth(float mirrorDepth)
	{this.mirrorDepth = mirrorDepth;}

	public String getCompleteRegionName()
	{return regionName + regionPosition;}
}
