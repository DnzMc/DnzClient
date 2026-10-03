#version 330
#extension GL_ARB_separate_shader_objects : require

// DNZ smooth shapes (rounded rectangles, soft shadows). Same uniforms as Minecraft 26.3's gui shader.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

// Vertex format POSITION_TEX_LIGHTMAP_COLOR
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;   // position inside the shape: -1..1 on both axes
layout(location = 2) in ivec2 UV2;  // x: corner radius, y: edge softness (share of the shorter half side, 0..32767)
layout(location = 3) in vec4 Color;

layout(location = 0) out vec2 local;
layout(location = 1) flat out ivec2 shape;
layout(location = 2) out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    local = UV0;
    shape = UV2;
    vertexColor = Color;
}
