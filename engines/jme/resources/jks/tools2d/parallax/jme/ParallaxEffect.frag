#import "Common/ShaderLib/GLSLCompat.glsllib"

// core/src/.../GdxLayerEffects.java and engines/godot/addons/jks_parallax/plax_effects.gd are the same lines: change one,
// change them, and run engines/jme/tools/frame-check.sh engines/rounds/shaders.

uniform sampler2D m_ColorMap;
uniform vec4 m_Region;
uniform vec2 m_Size;
uniform vec3 m_Effect;
uniform float m_Haze;
uniform vec3 m_FogColor;
uniform vec4 m_Dissolve;
uniform vec2 m_Cells;
uniform vec4 m_Grade;
uniform vec3 m_Across;
uniform vec3 m_Up;
uniform vec4 m_Glint;
uniform vec2 m_Sun;
uniform vec4 m_GlintA;
uniform vec4 m_GlintB;
uniform vec2 m_Wrap;

varying vec2 texCoord;
varying vec4 vertColor;
varying vec2 viewPos;

const float TAU = 6.2831853;

// A transfert's dissolve (LayerEffects.setDissolve): m_Dissolve side (0 none, 1 outgoing, 2 incoming), ramp, softness,
// drift; FOG's noise over the view, m_Cells cells across and up it. The layer's share of the slot, 0 to 1.
float dissolved()
{
    if (m_Dissolve.x < 0.5)
        return 1.0;
    vec2 p = viewPos * m_Cells + vec2(m_Dissolve.w, 0.0);
    float n = (sin(TAU * 0.875 * p.x + 2.0 * sin(0.5 * TAU * p.y))
        + sin(TAU * (2.125 * p.x - 0.5 * p.y) + 1.3)
        + sin(TAU * (2.875 * p.x + 0.8 * p.y) + 2.9)) / 6.0 + 0.5;
    float e = m_Dissolve.z;
    float m = smoothstep(n - e, n + e, m_Dissolve.y * (1.0 + 2.0 * e) - e);
    return m_Dissolve.x < 1.5 ? 1.0 - m : m;
}

// The color mixed toward the fog's colour by the depth fog, then toward m_Grade's rgb by its a (a transfert through a
// colour, LayerEffects.setGrade), its alpha times dissolved(); at 0, no grade and no dissolve the color itself.
vec4 hazed(vec4 color)
{
    return vec4(mix(mix(color.rgb, m_FogColor, m_Haze), m_Grade.rgb, m_Grade.a), color.a * dissolved());
}

// Where the fragment is in the image, in world units from its bottom-left.
vec2 local()
{
    return vec2((texCoord.x - m_Region.x) / (m_Region.z - m_Region.x) * m_Size.x,
        (m_Region.w - texCoord.y) / (m_Region.w - m_Region.y) * m_Size.y);
}

// Where the fragment is along the layer, in world units: local() carried on across its tiles (ParallaxLayer.getEffectStartX,
// getEffectStartY), so FOG's noise has no seam at a tile edge, on X (r229) or Y (r262), nor WAVE's ripple on Y (r273).
vec2 along()
{
    return vec2(m_Across.z * (viewPos.x * m_Across.x - m_Across.y), m_Up.z * (viewPos.y * m_Up.x - m_Up.y));
}

// The sun on the painted crests after the effect (GdxLayerEffects.glintUniforms, r408): a crest is brighter than the
// water m_Glint.w world units above and below it; two waves of whole turns pick pieces of it, a third sets when each fades
// in and out in place; within the sun's column across the view (m_Sun). m_Glint.x 0: none.
vec4 glinted(vec4 color, vec4 image, vec2 uv)
{
    if (m_Glint.x <= 0.0)
        return color;
    const vec3 LUMA = vec3(0.299, 0.587, 0.114);
    float dv = m_Glint.w / m_Size.y * (m_Region.w - m_Region.y);
    float lo = min(m_Region.y, m_Region.w), hi = max(m_Region.y, m_Region.w);
    vec4 above = texture2D(m_ColorMap, vec2(uv.x, clamp(uv.y - dv, lo, hi)));
    vec4 below = texture2D(m_ColorMap, vec2(uv.x, clamp(uv.y + dv, lo, hi)));
    float ridge = dot(image.rgb, LUMA) - 0.5 * (dot(above.rgb, LUMA) + dot(below.rgb, LUMA));
    float crest = smoothstep(0.005, 0.035, ridge) * min(above.a, below.a) * image.a;
    float sunAt = (viewPos.x - m_Sun.x) / m_Sun.y;
    vec2 q = along() / m_GlintB.zw;
    float n = (cos(TAU * (m_GlintA.x * q.x + m_GlintA.y * q.y)) + 0.6 * cos(TAU * (m_GlintA.z * q.x + m_GlintA.w * q.y + 0.3))) / 1.6;
    float pulse = 0.5 + 0.5 * cos(TAU * (m_Glint.z + 1.5 * sin(TAU * (m_GlintB.x * q.x + m_GlintB.y * q.y))));
    float glow = smoothstep(1.0 - m_Glint.y, 1.0, n) * pulse * pulse * crest * exp(-sunAt * sunAt);
    return vec4(min(color.rgb + m_Glint.x * glow * vec3(1.0, 0.96, 0.85), 1.0), color.a);
}

void main()
{
#if defined(PLAIN)
    // No effect: an IMAGE or SEQUENCE layer the page's depth fog reaches.
    gl_FragColor = hazed(vertColor * texture2D(m_ColorMap, texCoord));
#elif defined(FOG)
    // FOG: the opacity times 1 - amplitude * n, n in 0..1 a sum of three sines, x at 7, 17 and 23 per 8 wavelengths.
    vec2 p = (along() + vec2(m_Effect.z, 0.0)) / m_Effect.y;
    float n = (sin(TAU * 0.875 * p.x + 2.0 * sin(0.5 * TAU * p.y))
        + sin(TAU * (2.125 * p.x - 0.5 * p.y) + 1.3)
        + sin(TAU * (2.875 * p.x + 0.8 * p.y) + 2.9)) / 6.0 + 0.5;
    vec4 image = texture2D(m_ColorMap, texCoord);
    vec4 color = vertColor * image;
    color.a *= 1.0 - m_Effect.x * n;
    gl_FragColor = hazed(glinted(color, image, texCoord));
#elif defined(SWELL)
    // SWELL (r431): each pixel read from where two crossing travelling waves move it, up to amplitude sideways and half
    // that up and down, the image wrapped on the axes its page repeats on (m_Wrap: 1 wraps, 0 holds the edge rows, r434)
    // and held on the others; whole turns over 8 wavelengths across and 10 up.
    vec2 a = along();
    float L = m_Effect.y;
    float t = m_Effect.z / L;
    float p1 = TAU * ((a.x + 0.6 * a.y) / L - t);
    float p2 = TAU * (0.625 * a.x / L - 0.9 * a.y / L + 2.0 * t);
    vec2 l = local() + m_Effect.x * vec2(0.6 * sin(p1) + 0.4 * sin(p2), 0.5 * (0.6 * cos(p1) - 0.4 * cos(p2)));
    vec2 f = mix(clamp(l / m_Size, 0.0, 1.0), fract(l / m_Size), m_Wrap);
    vec2 uv = vec2(mix(m_Region.x, m_Region.z, f.x), mix(m_Region.w, m_Region.y, f.y));
    vec4 image = texture2D(m_ColorMap, uv);
    gl_FragColor = hazed(glinted(vertColor * image, image, uv));
#else
    // WAVE: each row shifted sideways by amplitude * sin(2 pi (y - phase) / wavelength), kept inside the region; y is
    // along().y, running on up across the tiles (r273).
    float shift = m_Effect.x * sin(TAU * (along().y - m_Effect.z) / m_Effect.y);
    float u = clamp(texCoord.x + shift / m_Size.x * (m_Region.z - m_Region.x), min(m_Region.x, m_Region.z), max(m_Region.x, m_Region.z));
    vec2 uv = vec2(u, texCoord.y);
    vec4 image = texture2D(m_ColorMap, uv);
    gl_FragColor = hazed(glinted(vertColor * image, image, uv));
#endif
}
