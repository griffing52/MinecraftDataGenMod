#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 ReprojectionMat; // PrevView * InvCurrView
uniform mat4 PrevProjMat;

out vec4 currPos;
out vec4 prevPos;

void main() {
    vec4 pos = vec4(Position, 1.0);
    
    // Current Frame Position
    vec4 viewPos = ModelViewMat * pos;
    gl_Position = ProjMat * viewPos;
    currPos = gl_Position;
    
    // Previous Frame Position (Reprojected)
    // PrevViewPos = (PrevView * InvCurrView) * CurrViewPos
    vec4 prevViewPos = ReprojectionMat * viewPos;
    prevPos = PrevProjMat * prevViewPos;
}
