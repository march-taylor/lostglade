#version 330

uniform sampler2D InSampler;
uniform sampler2D PaletteSampler;

layout(std140) uniform Lg2CaptureParams {
    vec4 SourceUvRect;
    vec2 OutputSize;
};

in vec2 texCoord;

out vec4 fragColor;

const int BAYER_8X8[64] = int[](
    0, 48, 12, 60, 3, 51, 15, 63,
    32, 16, 44, 28, 35, 19, 47, 31,
    8, 56, 4, 52, 11, 59, 7, 55,
    40, 24, 36, 20, 43, 27, 39, 23,
    2, 50, 14, 62, 1, 49, 13, 61,
    34, 18, 46, 30, 33, 17, 45, 29,
    10, 58, 6, 54, 9, 57, 5, 53,
    42, 26, 38, 22, 41, 25, 37, 21
);

vec2 resolveSourceUv(vec2 normalizedUv) {
    return SourceUvRect.xy + normalizedUv * SourceUvRect.zw;
}

vec3 sampleSourceColor(vec2 normalizedUv) {
    ivec2 sourceSize = textureSize(InSampler, 0);
    vec2 cropPixels = vec2(SourceUvRect.z * float(sourceSize.x), SourceUvRect.w * float(sourceSize.y));
    vec2 sampleSpan = cropPixels / max(OutputSize, vec2(1.0));
    vec2 sourceUv = resolveSourceUv(normalizedUv);

    if (sampleSpan.x > 1.15 || sampleSpan.y > 1.15) {
        vec2 texel = 1.0 / vec2(sourceSize);
        vec2 offset = sampleSpan * 0.25 * texel;
        vec3 a = texture(InSampler, sourceUv + vec2(-offset.x, -offset.y)).rgb;
        vec3 b = texture(InSampler, sourceUv + vec2(offset.x, -offset.y)).rgb;
        vec3 c = texture(InSampler, sourceUv + vec2(-offset.x, offset.y)).rgb;
        vec3 d = texture(InSampler, sourceUv + vec2(offset.x, offset.y)).rgb;
        return (a + b + c + d) * 0.25;
    }

    return texture(InSampler, sourceUv).rgb;
}

vec3 applyOrderedDither(vec3 color) {
    float red = color.r * 255.0;
    float green = color.g * 255.0;
    float blue = color.b * 255.0;
    float maxChannel = max(red, max(green, blue));
    float minChannel = min(red, min(green, blue));
    float saturation = maxChannel <= 0.0 ? 0.0 : (maxChannel - minChannel) / maxChannel;
    float saturationWeight = clamp(saturation / 0.35, 0.0, 1.0);
    float strength = 5.5 - 3.0 * saturationWeight;
    ivec2 pixel = ivec2(floor(gl_FragCoord.xy));
    int threshold = BAYER_8X8[((pixel.y & 7) << 3) | (pixel.x & 7)] - 31;
    float delta = float(threshold) * (strength / 31.0);
    vec3 dithered = vec3(red + delta, green + delta, blue + delta) / 255.0;
    return clamp(dithered, 0.0, 1.0);
}

void main() {
    vec3 color = sampleSourceColor(texCoord);

    #ifdef LG2_DITHER
    color = applyOrderedDither(color);
    #endif

    ivec3 rgb = ivec3(round(clamp(color, 0.0, 1.0) * 255.0));
    int key = ((rgb.r >> 3) << 11) | ((rgb.g >> 2) << 5) | (rgb.b >> 3);
    ivec2 lookupCoord = ivec2(key & 255, key >> 8);
    float paletteId = texelFetch(PaletteSampler, lookupCoord, 0).r;
    fragColor = vec4(paletteId, paletteId, paletteId, 1.0);
}
