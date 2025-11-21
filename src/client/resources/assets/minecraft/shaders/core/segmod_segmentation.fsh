#version 150

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // DEBUG: Force RED to verify shader is running
    fragColor = vec4(1.0, 0.0, 0.0, 1.0);
}
