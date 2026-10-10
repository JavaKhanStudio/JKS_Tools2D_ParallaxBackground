#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform mat4 g_WorldViewProjectionMatrix;

attribute vec3 inPosition;
attribute vec2 inTexCoord;
attribute vec4 inColor;

varying vec2 texCoord;
varying vec4 vertColor;
// Where the vertex is in the camera view, 0 to 1, y up: the dissolve's noise is laid over it.
varying vec2 viewPos;

void main()
{
    texCoord = inTexCoord;
    // JmeBatch drops the alpha's lowest bit, as SpriteBatch packs it; SpriteBatch's shader scales it back.
    vertColor = inColor;
    vertColor.a = vertColor.a * (255.0 / 254.0);
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
    viewPos = gl_Position.xy / gl_Position.w * 0.5 + 0.5;
}
