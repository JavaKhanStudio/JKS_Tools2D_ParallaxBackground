#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldViewMatrix;

attribute vec3 inPosition;
attribute vec4 inColor;

varying vec3 color;
varying float fogDepth;

void main()
{
    color = inColor.rgb;
    vec4 view = g_WorldViewMatrix * vec4(inPosition, 1.0);
#ifdef BREAK_DISTANCE
    // jME's own Lighting.vert: the distance from the camera.
    fogDepth = length(view.xyz);
#else
    // three.js's fog_vertex: vFogDepth = - mvPosition.z.
    fogDepth = -view.z;
#endif
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
