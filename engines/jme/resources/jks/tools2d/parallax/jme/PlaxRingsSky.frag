#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec3 m_TopFrom;
uniform vec3 m_TopTo;
uniform vec3 m_BottomFrom;
uniform vec3 m_BottomTo;
uniform vec3 m_FogColor;
uniform vec2 m_Top;
uniform vec2 m_Bottom;
uniform float m_Horizon;

varying vec3 direction;

float along(float e, vec2 span)
{
    return span.y > span.x ? clamp((e - span.x) / (span.y - span.x), 0.0, 1.0) : step(span.x, e);
}

void main()
{
    float e = asin(clamp(normalize(direction).y, -1.0, 1.0));
    vec3 color = mix(m_TopFrom, m_TopTo, along(e, m_Top));
    if (e <= m_Bottom.y)
        color = mix(m_BottomFrom, m_BottomTo, along(e, m_Bottom));
    gl_FragColor = vec4(mix(m_FogColor, color, smoothstep(-0.02, m_Horizon, e)), 1.0);
}
