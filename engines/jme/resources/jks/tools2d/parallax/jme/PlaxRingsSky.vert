#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform mat4 g_ViewMatrix;
uniform mat4 g_ProjectionMatrix;
uniform vec2 g_FrustumNearFar;

attribute vec3 inPosition;

varying vec3 direction;

void main()
{
    // Round the camera wherever it goes (jME's Sky.vert): the view's turn only, between the near and far planes.
    direction = inPosition;
    vec4 pos = g_ViewMatrix * vec4(normalize(inPosition) * (0.5 * (g_FrustumNearFar.x + g_FrustumNearFar.y)), 0.0);
    pos.w = 1.0;
    gl_Position = g_ProjectionMatrix * pos;
}
