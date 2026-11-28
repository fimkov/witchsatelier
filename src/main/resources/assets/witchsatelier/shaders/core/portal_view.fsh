#version 150

// The far side of a windowway, as drawn from the matching eye: sampled at this very pixel of the screen,
// with a faint glow creeping in from the rim.
//
// The disc is a fan whose alpha runs from 0 at its centre to RIM_ALPHA at its edge, so the alpha tells how far
// out a pixel lies. While the window turns, Iris narrows the opening: past it the wall behind shows, with a lip of
// light at the edge. Dim flashes the opening pale, from the rim inward, while the window turns someone away.
uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float GameTime;
uniform float Iris;
uniform float Dim;

in vec4 tint;
out vec4 fragColor;

const float RIM_ALPHA = 120.0 / 255.0;

void main() {
    float out_ = clamp(tint.a / RIM_ALPHA, 0.0, 1.0);
    float turning = 1.0 - step(0.999, Iris);
    if (turning > 0.0 && out_ > Iris) discard;
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float shimmer = 0.004 * tint.a * sin(gl_FragCoord.y * 0.05 + GameTime * 3000.0);
    vec3 far = texture(Sampler0, uv + vec2(shimmer, 0.0)).rgb;
    float rim = tint.a * tint.a;
    vec3 col = mix(far, tint.rgb, rim * 0.55);
    float lip = turning * smoothstep(Iris - 0.08, Iris, out_);
    col = mix(col, mix(tint.rgb, vec3(1.0), 0.6), lip);
    float flash = clamp(Dim, 0.0, 1.0) * (0.35 + 0.65 * out_ * out_);
    col = mix(col, mix(tint.rgb, vec3(1.0), 0.55), clamp(flash * 1.2, 0.0, 1.0));
    fragColor = vec4(col, 1.0);
}
