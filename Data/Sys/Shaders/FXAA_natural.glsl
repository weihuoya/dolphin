#define FXAA_REDUCE_MIN     (1.0 / 128.0)
#define FXAA_REDUCE_MUL     (1.0 / 8.0)
#define FXAA_SPAN_MAX       8.0

float3 applyFXAA(float2 fragCoord)
{
    float2 inverseVP = GetInvResolution();
    float3 rgbNW = SampleLocation((fragCoord + float2(-1.0, -1.0)) * inverseVP).xyz;
    float3 rgbNE = SampleLocation((fragCoord + float2(1.0, -1.0)) * inverseVP).xyz;
    float3 rgbSW = SampleLocation((fragCoord + float2(-1.0, 1.0)) * inverseVP).xyz;
    float3 rgbSE = SampleLocation((fragCoord + float2(1.0, 1.0)) * inverseVP).xyz;
    float3 rgbM  = SampleLocation(fragCoord  * inverseVP).xyz;
    float3 luma = float3(0.299, 0.587, 0.114);
    float lumaNW = dot(rgbNW, luma);
    float lumaNE = dot(rgbNE, luma);
    float lumaSW = dot(rgbSW, luma);
    float lumaSE = dot(rgbSE, luma);
    float lumaM  = dot(rgbM,  luma);
    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));

    float2 dir;
    dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
    dir.y =  ((lumaNW + lumaSW) - (lumaNE + lumaSE));

    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) *
                        (0.25 * FXAA_REDUCE_MUL), FXAA_REDUCE_MIN);

    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
    dir = min(float2(FXAA_SPAN_MAX, FXAA_SPAN_MAX),
            max(float2(-FXAA_SPAN_MAX, -FXAA_SPAN_MAX),
            dir * rcpDirMin)) * inverseVP;

    float3 rgbA = 0.5 * (
        SampleLocation(fragCoord * inverseVP + dir * (1.0 / 3.0 - 0.5)).xyz +
        SampleLocation(fragCoord * inverseVP + dir * (2.0 / 3.0 - 0.5)).xyz);
    float3 rgbB = rgbA * 0.5 + 0.25 * (
        SampleLocation(fragCoord * inverseVP + dir * -0.5).xyz +
        SampleLocation(fragCoord * inverseVP + dir * 0.5).xyz);

    float lumaB = dot(rgbB, luma);
    if ((lumaB < lumaMin) || (lumaB > lumaMax))
        return rgbA;
    else
        return rgbB;
}

const mat3 RGBtoYIQ = mat3(0.299, 0.596, 0.212, 
                           0.587,-0.275,-0.523, 
                           0.114,-0.321, 0.311);

const mat3 YIQtoRGB = mat3(1.0, 1.0, 1.0,
                           0.95568806036115671171,-0.27158179694405859326,-1.1081773266826619523,
                           0.61985809445637075388,-0.64687381613840131330, 1.7050645599191817149);

const float3 val00 = float3(1.2, 1.2, 1.2);

float4 applyNatural(float3 c0)
{
    float3 c1 = RGBtoYIQ * c0;
    c1 = float3(pow(c1.x, val00.x), c1.yz * val00.yz);
    return float4(YIQtoRGB * c1, 1.0);
}

void main()
{
    SetOutput(applyNatural(applyFXAA(GetCoordinates() * GetResolution())));
}
