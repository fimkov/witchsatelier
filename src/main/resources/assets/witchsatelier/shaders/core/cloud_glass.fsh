#version 150

// The little cloud under a levitating thing, as frosted glass: inside its lumpy outline what lies behind is seen
// softly blurred and a little milky, touched with the spell's colour, and a thin bright line runs round its edge.
// Sampler0: the cloud's shape (its alpha); Sampler1: a copy of the frame drawn so far.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 ScreenSize;
uniform float GameTime;

in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

void main() {
    float shape = texture(Sampler0, texCoord).a;
    if (shape < 0.03) discard;

    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec2 px = vec2(ScreenSize.y / 300.0) / ScreenSize;
    float t = GameTime * 2400.0;
    vec2 drift = vec2(sin(t * 0.6 + gl_FragCoord.y * 0.03), cos(t * 0.45 + gl_FragCoord.x * 0.03)) * px * 0.5;
    vec3 sum = vec3(0.0);
    float weight = 0.0;
    for (int i = -4; i <= 4; i++) {
        for (int j = -4; j <= 4; j++) {
            float k = exp(-float(i * i + j * j) / 9.0);
            sum += texture(Sampler1, uv + drift + vec2(float(i), float(j)) * px).rgb * k;
            weight += k;
        }
    }
    vec3 frosted = sum / weight;

    // a puff of light: what is behind it seen through blurred, and the spell's glow filling it, brightest in
    // the heart, with a thin line round its rim
    float body = smoothstep(0.10, 0.55, shape);
    float core = smoothstep(0.55, 1.0, shape);
    float pulse = 0.92 + 0.08 * sin(t * 1.7);
    vec3 glow = mix(tint.rgb, vec3(1.0), 0.30 + 0.55 * core);
    vec3 color = frosted * (1.0 - 0.40 * body) + glow * (0.45 * body + 0.55 * core) * pulse;
    vec3 line = mix(tint.rgb, vec3(1.0), 0.6);
    float rim = smoothstep(0.04, 0.12, shape) * (1.0 - smoothstep(0.12, 0.22, shape));
    color = mix(color, line, clamp(rim * 1.5, 0.0, 1.0));
    float alpha = max(body * 0.96, rim * 0.9) * tint.a;
    fragColor = vec4(color, alpha);
}
