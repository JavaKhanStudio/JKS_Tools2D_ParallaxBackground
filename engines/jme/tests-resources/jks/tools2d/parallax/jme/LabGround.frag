#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec3 m_FogColor;
uniform vec2 m_Fog;

varying vec3 color;
varying float fogDepth;

// Linear to sRGB, three.js's linearToOutputTexel.
vec3 encoded(vec3 c)
{
    return mix(pow(max(c, vec3(0.0031308)), vec3(1.0 / 2.4)) * 1.055 - 0.055, c * 12.92, vec3(lessThanEqual(c, vec3(0.0031308))));
}

void main()
{
#ifdef BREAK_RAMP
    // jME's getFogLinear: a straight line from near to far.
    float fog = clamp((fogDepth - m_Fog.x) / (m_Fog.y - m_Fog.x), 0.0, 1.0);
#else
    // three.js's fog_fragment.
    float fog = smoothstep(m_Fog.x, m_Fog.y, fogDepth);
#endif
    // three.js mixes after its colorspace_fragment, its fog colour in the output's space too (getUnlitUniformColorSpace).
#ifdef BREAK_MIX
    gl_FragColor = vec4(encoded(mix(color, m_FogColor, fog)), 1.0);
#else
    gl_FragColor = vec4(mix(encoded(color), encoded(m_FogColor), fog), 1.0);
#endif
}
