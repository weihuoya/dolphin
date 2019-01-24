in vec4 v_texcoord1;
in vec4 v_texcoord2;
in vec4 v_texcoord3;
in vec4 v_texcoord4;
in vec4 v_texcoord5;
in vec4 v_texcoord6;

const float bb = 0.5; // effects black border sensitivity; from 0.0 to 1.0

void main()
{
  vec3 c00 = SampleLocation(v_texcoord5.xy).xyz; 
  vec3 c10 = SampleLocation(v_texcoord1.xy).xyz; 
  vec3 c20 = SampleLocation(v_texcoord2.zw).xyz; 
  vec3 c01 = SampleLocation(v_texcoord3.xy).xyz; 
  vec3 c11 = SampleLocation(GetCoordinates()).xyz; 
  vec3 c21 = SampleLocation(v_texcoord4.xy).xyz; 
  vec3 c02 = SampleLocation(v_texcoord1.zw).xyz; 
  vec3 c12 = SampleLocation(v_texcoord2.xy).xyz; 
  vec3 c22 = SampleLocation(v_texcoord6.xy).xyz; 
  vec3 dt = vec3(1.0,1.0,1.0); 

  float d1 = dot(abs(c00 - c22), dt);
  float d2 = dot(abs(c20 - c02), dt);
  float hl = dot(abs(c01 - c21), dt);
  float vl = dot(abs(c10 - c12), dt);
  float  d = bb * (d1 + d2 + hl + vl) / (dot(c11, dt) + 0.15);

  float lc = 4.0 * length(c11);
  float f = fract(lc); f*=f;
  lc = 0.25 * (floor(lc) + f * f) + 0.05;
  c11 = 4.0 * normalize(c11); 
  vec3 frct = fract(c11); frct *= frct;
  c11 = floor(c11) + 0.05 * dt + frct * frct;
  SetOutput(float4(0.25 * lc * (1.1 - d * sqrt(d)) * c11, 1.0));
}
