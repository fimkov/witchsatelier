#version 150

// The far side of a windowway, as drawn from the matching eye: sampled at this very pixel of the screen,
// with a faint glow creeping in from the rim.
uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float GameTime;

in vec4 tint;
out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float shimmer = 0.004 * tint.a * sin(gl_FragCoord.y * 0.05 + GameTime * 3000.0);
    vec3 far = texture(Sampler0, uv + vec2(shimmer, 0.0)).rgb;
    float rim = tint.a * tint.a;
    fragColor = vec4(mix(far, tint.rgb, rim * 0.55), 1.0);
}
