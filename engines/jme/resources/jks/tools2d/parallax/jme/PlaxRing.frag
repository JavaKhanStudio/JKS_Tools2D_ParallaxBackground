#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_ColorMap;
uniform vec4 m_Tint;
uniform vec3 m_FogColor;
uniform float m_Haze;
uniform float m_Melt;
uniform float m_EyeMelt;
uniform vec4 m_Grade;

varying vec2 texCoord;
varying float band;
varying vec3 fromEye;

void main()
{
    vec4 color = m_Tint * texture2D(m_ColorMap, texCoord);
    // The band's bottom melts into the fog (sky.js's melt), and, with an eye melt, what the camera sees near its horizon.
    float melted = m_Melt > 0.0 ? 1.0 - smoothstep(0.0, m_Melt, band) : 0.0;
    if (m_EyeMelt > 0.0)
        melted = max(melted, 1.0 - smoothstep(0.0, m_EyeMelt, asin(clamp(normalize(fromEye).y, -1.0, 1.0))));
    float fog = m_Haze + (1.0 - m_Haze) * melted;
    gl_FragColor = vec4(mix(mix(color.rgb, m_FogColor, fog), m_Grade.rgb, m_Grade.a), color.a);
}
