#include sable_schematic_api:frontier_laser_noise

#veil:buffer veil:camera VeilCamera

uniform vec4 ColorModulator;
uniform float VeilRenderTime;

in vec4 vertexColor;
in vec3 localPosition;

out vec4 fragColor;

void main() {
    vec4 base = vertexColor * ColorModulator;
    if (base.a <= 0.001) {
        discard;
    }

    vec3 worldPos = localPosition + VeilCamera.CameraPosition;
    float fastFrame = floor(VeilRenderTime * 30.0);
    vec3 coarseCell = floor(worldPos * 9.0);
    vec3 fineCell = floor(worldPos * 23.0);
    float coarse = frontier_laser_hash(coarseCell + vec3(fastFrame * 3.1, fastFrame * 1.7, fastFrame * 2.3));
    float fine = frontier_laser_hash(fineCell + vec3(fastFrame * 5.3, -fastFrame * 2.9, fastFrame * 4.7));
    float darkSpeckle = max(step(0.69, coarse), step(0.82, fine));
    float dust = step(0.92, frontier_laser_hash(floor(worldPos * 41.0) + vec3(fastFrame)));
    float pulse = 0.82 + 0.18 * step(0.5, frontier_laser_hash(vec3(fastFrame, floor(worldPos.y * 2.0), 19.0)));

    vec3 deepBlue = base.rgb * vec3(0.72, 0.82, 1.0);
    vec3 blackDust = vec3(0.012, 0.014, 0.020);
    vec3 grayDust = vec3(0.050, 0.055, 0.065);
    vec3 color = mix(deepBlue, blackDust, darkSpeckle);
    color = mix(color, grayDust, dust * (1.0 - darkSpeckle));

    float alpha = base.a * pulse * mix(1.0, 0.88, darkSpeckle);
    fragColor = vec4(color, alpha);
}
