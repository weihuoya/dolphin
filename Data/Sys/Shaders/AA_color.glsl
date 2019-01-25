const vec3 c_ch = vec3(1.0,1.0,1.0);  //  rgb color channel intensity
const float   a = 1.20 ;              //  saturation
const float   b = 1.00 ;              //  brightness
const float   c = 1.20 ;              //  contrast

// you can use contrast1, contrast2...contrast4 (or contrast0 for speedup)
float contrast0(float x) { return x; }
float contrast1(float x) { x = x * 1.1547 - 1.0; return sign(x) * pow(abs(x), 1.0 / c) * 0.86 + 0.86;}
float contrast2(float x) { return normalize(vec2(pow(x, c), pow(0.86, c))).x * 1.72;}
float contrast3(float x) { return 1.73 * pow(0.57735 * x, c); }
float contrast4(float x) { return clamp(0.866 + c * (x - 0.866), 0.05, 1.73); }

void main()
{
  vec3 color = Sample().xyz;
  float x = sqrt(dot(color, color));

  color.r = pow(color.r + 0.001, a);
  color.g = pow(color.g + 0.001, a);
  color.b = pow(color.b + 0.001, a);

  SetOutput(float4(contrast4(x) * normalize(color * c_ch) * b, 1.0));
}
