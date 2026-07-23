uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D BodyMaskSampler;

uniform vec4 OutlineColor;

in vec2 texCoord;

out vec4 fragColor;

float bodyMask(vec2 uv) {
    vec2 clampedUv = clamp(uv, vec2(0.0), vec2(1.0));
    vec4 mask = texture(BodyMaskSampler, clampedUv);
    return step(0.01, max(mask.a, max(mask.r, max(mask.g, mask.b))));
}

void main() {
    vec4 scene = texture(DiffuseSampler0, texCoord);
    vec2 texel = 1.0 / vec2(textureSize(BodyMaskSampler, 0));
    float center = bodyMask(texCoord);
    float around = 0.0;
    around = max(around, bodyMask(texCoord + vec2( texel.x,  0.0)));
    around = max(around, bodyMask(texCoord + vec2(-texel.x,  0.0)));
    around = max(around, bodyMask(texCoord + vec2( 0.0,  texel.y)));
    around = max(around, bodyMask(texCoord + vec2( 0.0, -texel.y)));
    around = max(around, bodyMask(texCoord + vec2( texel.x,  texel.y)));
    around = max(around, bodyMask(texCoord + vec2(-texel.x,  texel.y)));
    around = max(around, bodyMask(texCoord + vec2( texel.x, -texel.y)));
    around = max(around, bodyMask(texCoord + vec2(-texel.x, -texel.y)));
    float edge = clamp(around - center, 0.0, 1.0);
    float strength = edge * OutlineColor.a;
    fragColor = vec4(mix(scene.rgb, OutlineColor.rgb, strength), scene.a);
    gl_FragDepth = texture(DiffuseDepthSampler, texCoord).r;
}
