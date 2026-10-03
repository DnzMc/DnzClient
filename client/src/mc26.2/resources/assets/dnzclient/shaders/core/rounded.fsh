#version 330

// DNZ smooth shapes: every pixel measures its distance to the rounded edge, so corners are soft at any
// GUI scale (no stair steps). Softness > 0 turns the shape into a blurred shadow.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 local;
flat in ivec2 shape;
in vec4 vertexColor;

out vec4 fragColor;

float roundedBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    // Half size of the shape in screen pixels: "local" goes from -1 to 1 across it.
    vec2 halfSize = 1.0 / max(fwidth(local), vec2(1e-6));
    vec2 p = local * halfSize;
    float shorter = min(halfSize.x, halfSize.y);
    float softness = float(shape.y) / 32767.0 * shorter;
    float radius = float(shape.x) / 32767.0 * shorter;
    vec2 body = max(halfSize - softness, vec2(0.5));
    float d = roundedBox(p, body, min(radius, min(body.x, body.y)));
    float coverage = softness > 0.0
        ? 1.0 - smoothstep(-softness, softness, d)
        : clamp(0.5 - d, 0.0, 1.0);
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * coverage);
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
