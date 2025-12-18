#version 150

in vec4 currPos;
in vec4 prevPos;

out vec4 fragColor;

void main() {
    // Convert to Normalized Device Coordinates (NDC) [-1, 1]
    vec2 currNDC = currPos.xy / currPos.w;
    vec2 prevNDC = prevPos.xy / prevPos.w;
    
    // Calculate flow (delta)
    vec2 flow = currNDC - prevNDC;
    
    // Encode flow into color
    // 0.5 is zero motion. <0.5 is negative, >0.5 is positive.
    vec2 encodedFlow = (flow * 5.0) + 0.5; // Scale by 5 to make small motions visible
    
    fragColor = vec4(encodedFlow.x, encodedFlow.y, 0.0, 1.0);
}
