#import "Common/ShaderLib/GLSLCompat.glsllib"

// core/src/.../GdxLayerEffects.java and engines/godot/addons/jks_parallax/plax_effects.gd are the same lines: change one,
// change them, and run tools/jme-parallax-shots.sh engines/godot/tests/shaders.

uniform sampler2D m_ColorMap;
uniform vec4 m_Region;
uniform vec2 m_Size;
uniform vec3 m_Effect;
uniform float m_Haze;
uniform vec3 m_FogColor;

varying vec2 texCoord;
varying vec4 vertColor;

const float TAU = 6.2831853;

// The color mixed toward the fog's colour by the depth fog, its alpha kept; at 0 the color itself.
vec4 hazed(vec4 color)
{
    return vec4(mix(color.rgb, m_FogColor, m_Haze), color.a);
}

// Where the fragment is in the image, in world units from its bottom-left.
vec2 local()
{
    return vec2((texCoord.x - m_Region.x) / (m_Region.z - m_Region.x) * m_Size.x,
        (m_Region.w - texCoord.y) / (m_Region.w - m_Region.y) * m_Size.y);
}

void main()
{
#if defined(PLAIN)
    // No effect: an IMAGE or SEQUENCE layer the page's depth fog reaches.
    gl_FragColor = hazed(vertColor * texture2D(m_ColorMap, texCoord));
#elif defined(FOG)
    // FOG: the opacity times 1 - amplitude * n, n in 0..1 a sum of three sines, x at 7, 17 and 23 per 8 wavelengths.
    vec2 p = (local() + vec2(m_Effect.z, 0.0)) / m_Effect.y;
    float n = (sin(TAU * 0.875 * p.x + 2.0 * sin(0.5 * TAU * p.y))
        + sin(TAU * (2.125 * p.x - 0.5 * p.y) + 1.3)
        + sin(TAU * (2.875 * p.x + 0.8 * p.y) + 2.9)) / 6.0 + 0.5;
    vec4 color = vertColor * texture2D(m_ColorMap, texCoord);
    color.a *= 1.0 - m_Effect.x * n;
    gl_FragColor = hazed(color);
#else
    // WAVE: each row shifted sideways by amplitude * sin(2 pi (y - phase) / wavelength), kept inside the region.
    float shift = m_Effect.x * sin(TAU * (local().y - m_Effect.z) / m_Effect.y);
    float u = clamp(texCoord.x + shift / m_Size.x * (m_Region.z - m_Region.x), min(m_Region.x, m_Region.z), max(m_Region.x, m_Region.z));
    gl_FragColor = hazed(vertColor * texture2D(m_ColorMap, vec2(u, texCoord.y)));
#endif
}
