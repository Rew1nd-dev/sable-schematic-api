float frontier_laser_hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.11, 0.17, 0.23));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float frontier_laser_noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = frontier_laser_hash(i + vec3(0.0, 0.0, 0.0));
    float n100 = frontier_laser_hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = frontier_laser_hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = frontier_laser_hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = frontier_laser_hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = frontier_laser_hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = frontier_laser_hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = frontier_laser_hash(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);
    return mix(nxy0, nxy1, f.z);
}
