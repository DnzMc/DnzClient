#version 330

// DNZ smooth shapes (rounded rectangles, borders, soft shadows). Same uniforms as Minecraft's gui shader.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;   // position inside the shape: -1..1 on both axes
in ivec2 UV2;  // x: corner radius, y: edge softness (both as a share of the shorter half side, 0..32767)
in vec4 Color;

out vec2 local;
flat out ivec2 shape;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    local = UV0;
    shape = UV2;
    vertexColor = Color;
}
