#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform mat4 g_WorldViewProjectionMatrix;

attribute vec3 inPosition;
attribute vec2 inTexCoord;
attribute vec4 inColor;

varying vec2 texCoord;
varying vec4 vertColor;

void main()
{
    texCoord = inTexCoord;
    // JmeBatch drops the alpha's lowest bit, as SpriteBatch packs it; SpriteBatch's shader scales it back.
    vertColor = inColor;
    vertColor.a = vertColor.a * (255.0 / 254.0);
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
