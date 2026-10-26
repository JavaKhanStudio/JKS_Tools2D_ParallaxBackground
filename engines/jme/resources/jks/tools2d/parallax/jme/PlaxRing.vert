#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldMatrix;
uniform mat4 g_ViewMatrix;
uniform mat4 g_ProjectionMatrix;
uniform vec3 g_CameraPosition;
uniform vec2 g_FrustumNearFar;

attribute vec3 inPosition;
attribute vec2 inTexCoord;
// x: how far up the layer's box the vertex is, 0 at its bottom (the melt's).
attribute vec2 inTexCoord2;

varying vec2 texCoord;
varying float band;
varying vec3 fromEye;
#ifdef RING
// A SHADER layer's band (PlaxRingEffect.j3md, r61): ParallaxEffect.frag's inputs. Its viewPos is the image's page units
// (x round the ring from the first tile's left, y up from the layer's bottom), so along() runs round the ring as it runs
// across the flat page; its vertColor the background's tint.
uniform vec4 m_Tint;
attribute vec2 inTexCoord3;
varying vec4 vertColor;
varying vec2 viewPos;
#endif

void main()
{
    texCoord = inTexCoord;
    band = inTexCoord2.x;
#ifdef RING
    viewPos = inTexCoord3;
    vertColor = m_Tint;
#endif
#ifdef STILL
    // Turned by the scroll, never moved: 0.85 of the far plane round the camera, as three.js's _follow puts it.
    vec3 direction = normalize((g_WorldMatrix * vec4(inPosition, 0.0)).xyz);
    fromEye = direction;
    vec4 pos = g_ViewMatrix * vec4(direction * (g_FrustumNearFar.y * 0.85), 0.0);
    pos.w = 1.0;
    gl_Position = g_ProjectionMatrix * pos;
#else
    fromEye = (g_WorldMatrix * vec4(inPosition, 1.0)).xyz - g_CameraPosition;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
#endif
}
