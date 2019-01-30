/*
FXAA_PRESET - Choose compile-in knob preset 0-5.
------------------------------------------------------------------------------
FXAA_EDGE_THRESHOLD - The minimum amount of local contrast required 
                      to apply algorithm.
                      1.0/3.0  - too little
                      1.0/4.0  - good start
                      1.0/8.0  - applies to more edges
                      1.0/16.0 - overkill
------------------------------------------------------------------------------
FXAA_EDGE_THRESHOLD_MIN - Trims the algorithm from processing darks.
                          Perf optimization.
                          1.0/32.0 - visible limit (smaller isn't visible)
                          1.0/16.0 - good compromise
                          1.0/12.0 - upper limit (seeing artifacts)
------------------------------------------------------------------------------
FXAA_SEARCH_STEPS - Maximum number of search steps for end of span.
------------------------------------------------------------------------------
FXAA_SEARCH_THRESHOLD - Controls when to stop searching.
                        1.0/4.0 - seems to be the best quality wise
------------------------------------------------------------------------------
FXAA_SUBPIX_TRIM - Controls sub-pixel aliasing removal.
                   1.0/2.0 - low removal
                   1.0/3.0 - medium removal
                   1.0/4.0 - default removal
                   1.0/8.0 - high removal
                   0.0 - complete removal
------------------------------------------------------------------------------
FXAA_SUBPIX_CAP - Insures fine detail is not completely removed.
                  This is important for the transition of sub-pixel detail,
                  like fences and wires.
                  3.0/4.0 - default (medium amount of filtering)
                  7.0/8.0 - high amount of filtering
                  1.0 - no capping of sub-pixel aliasing removal
*/

#ifndef FXAA_PRESET
    #define FXAA_PRESET 5
#endif
#if (FXAA_PRESET == 3)
    #define FXAA_EDGE_THRESHOLD      (1.0/8.0)
    #define FXAA_EDGE_THRESHOLD_MIN  (1.0/16.0)
    #define FXAA_SEARCH_STEPS        16
    #define FXAA_SEARCH_THRESHOLD    (1.0/4.0)
    #define FXAA_SUBPIX_CAP          (3.0/4.0)
    #define FXAA_SUBPIX_TRIM         (1.0/4.0)
#endif
#if (FXAA_PRESET == 4)
    #define FXAA_EDGE_THRESHOLD      (1.0/8.0)
    #define FXAA_EDGE_THRESHOLD_MIN  (1.0/24.0)
    #define FXAA_SEARCH_STEPS        24
    #define FXAA_SEARCH_THRESHOLD    (1.0/4.0)
    #define FXAA_SUBPIX_CAP          (3.0/4.0)
    #define FXAA_SUBPIX_TRIM         (1.0/4.0)
#endif
#if (FXAA_PRESET == 5)
    #define FXAA_EDGE_THRESHOLD      (1.0/8.0)
    #define FXAA_EDGE_THRESHOLD_MIN  (1.0/24.0)
    #define FXAA_SEARCH_STEPS        32
    #define FXAA_SEARCH_THRESHOLD    (1.0/4.0)
    #define FXAA_SUBPIX_CAP          (3.0/4.0)
    #define FXAA_SUBPIX_TRIM         (1.0/4.0)
#endif

#define FXAA_SUBPIX_TRIM_SCALE (1.0/(1.0 - FXAA_SUBPIX_TRIM))

// Return the luma, the estimation of luminance from rgb inputs.
// This approximates luma using one FMA instruction,
// skipping normalization and tossing out blue.
// FxaaLuma() will range 0.0 to 2.963210702.
float FxaaLuma(float3 rgb) {
    return rgb.y * (0.587/0.299) + rgb.x;
}

float3 FxaaLerp3(float3 a, float3 b, float amountOfA) {
    return (float3(-amountOfA) * b) + ((a * float3(amountOfA)) + b);
}

float4 FxaaTexOff(float2 pos, int2 off, float2 rcpFrame) {
    float x = pos.x + float(off.x) * rcpFrame.x;
    float y = pos.y + float(off.y) * rcpFrame.y;
    return SampleLocation(float2(x, y));
}

// pos is the output of FxaaVertexShader interpolated across screen.
// xy -> actual texture position {0.0 to 1.0}
// rcpFrame should be a uniform equal to  {1.0/frameWidth, 1.0/frameHeight}
void main()
{
    float2 pos = GetCoordinates();
    float2 rcpFrame = GetInvResolution();

    float3 rgbN = FxaaTexOff(pos.xy, int2( 0,-1), rcpFrame).xyz;
    float3 rgbW = FxaaTexOff(pos.xy, int2(-1, 0), rcpFrame).xyz;
    float3 rgbM = FxaaTexOff(pos.xy, int2( 0, 0), rcpFrame).xyz;
    float3 rgbE = FxaaTexOff(pos.xy, int2( 1, 0), rcpFrame).xyz;
    float3 rgbS = FxaaTexOff(pos.xy, int2( 0, 1), rcpFrame).xyz;
    
    float lumaN = FxaaLuma(rgbN);
    float lumaW = FxaaLuma(rgbW);
    float lumaM = FxaaLuma(rgbM);
    float lumaE = FxaaLuma(rgbE);
    float lumaS = FxaaLuma(rgbS);
    float rangeMin = min(lumaM, min(min(lumaN, lumaW), min(lumaS, lumaE)));
    float rangeMax = max(lumaM, max(max(lumaN, lumaW), max(lumaS, lumaE)));
    
    float range = rangeMax - rangeMin;
    if(range < max(FXAA_EDGE_THRESHOLD_MIN, rangeMax * FXAA_EDGE_THRESHOLD))
    {
        SetOutput(float4(rgbM, 1.0));
        return;
    }
    
    float3 rgbL = rgbN + rgbW + rgbM + rgbE + rgbS;
    
    float lumaL = (lumaN + lumaW + lumaE + lumaS) * 0.25;
    float rangeL = abs(lumaL - lumaM);
    float blendL = max(0.0, (rangeL / range) - FXAA_SUBPIX_TRIM) * FXAA_SUBPIX_TRIM_SCALE; 
    blendL = min(FXAA_SUBPIX_CAP, blendL);
    
    float3 rgbNW = FxaaTexOff(pos.xy, int2(-1,-1), rcpFrame).xyz;
    float3 rgbNE = FxaaTexOff(pos.xy, int2( 1,-1), rcpFrame).xyz;
    float3 rgbSW = FxaaTexOff(pos.xy, int2(-1, 1), rcpFrame).xyz;
    float3 rgbSE = FxaaTexOff(pos.xy, int2( 1, 1), rcpFrame).xyz;
    rgbL += (rgbNW + rgbNE + rgbSW + rgbSE);
    rgbL *= float3(1.0/9.0);
    
    float lumaNW = FxaaLuma(rgbNW);
    float lumaNE = FxaaLuma(rgbNE);
    float lumaSW = FxaaLuma(rgbSW);
    float lumaSE = FxaaLuma(rgbSE);

    float edgeVert = 
        abs((0.25 * lumaNW) + (-0.5 * lumaN) + (0.25 * lumaNE)) +
        abs((0.50 * lumaW ) + (-1.0 * lumaM) + (0.50 * lumaE )) +
        abs((0.25 * lumaSW) + (-0.5 * lumaS) + (0.25 * lumaSE));
    float edgeHorz = 
        abs((0.25 * lumaNW) + (-0.5 * lumaW) + (0.25 * lumaSW)) +
        abs((0.50 * lumaN ) + (-1.0 * lumaM) + (0.50 * lumaS )) +
        abs((0.25 * lumaNE) + (-0.5 * lumaE) + (0.25 * lumaSE));

    bool horzSpan = edgeHorz >= edgeVert;
    float lengthSign = horzSpan ? -rcpFrame.y : -rcpFrame.x;
    
    if(!horzSpan)
    {
        lumaN = lumaW;
        lumaS = lumaE;
    }
    
    float gradientN = abs(lumaN - lumaM);
    float gradientS = abs(lumaS - lumaM);
    lumaN = (lumaN + lumaM) * 0.5;
    lumaS = (lumaS + lumaM) * 0.5;
    
    if (gradientN < gradientS)
    {
        lumaN = lumaS;
        lumaN = lumaS;
        gradientN = gradientS;
        lengthSign *= -1.0;
    }
    
    float2 posN;
    posN.x = pos.x + (horzSpan ? 0.0 : lengthSign * 0.5);
    posN.y = pos.y + (horzSpan ? lengthSign * 0.5 : 0.0);
    
    gradientN *= FXAA_SEARCH_THRESHOLD;
    
    float2 posP = posN;
    float2 offNP = horzSpan ? float2(rcpFrame.x, 0.0) : float2(0.0, rcpFrame.y); 
    float lumaEndN = lumaN;
    float lumaEndP = lumaN;
    bool doneN = false;
    bool doneP = false;
    posN += offNP * float2(-1.0, -1.0);
    posP += offNP * float2( 1.0,  1.0);
    
    for(int i = 0; i < FXAA_SEARCH_STEPS; i++) 
    {
        if(!doneN)
        {
            lumaEndN = FxaaLuma(SampleLocation(posN.xy).xyz);
        }
        if(!doneP)
        {
            lumaEndP = FxaaLuma(SampleLocation(posP.xy).xyz);
        }
        
        doneN = doneN || (abs(lumaEndN - lumaN) >= gradientN);
        doneP = doneP || (abs(lumaEndP - lumaN) >= gradientN);
        
        if(doneN && doneP)
        {
            break;
        }
        if(!doneN)
        {
            posN -= offNP;
        }
        if(!doneP)
        {
            posP += offNP;
        }
    }
    
    float dstN = horzSpan ? pos.x - posN.x : pos.y - posN.y;
    float dstP = horzSpan ? posP.x - pos.x : posP.y - pos.y;
    bool directionN = dstN < dstP;
    lumaEndN = directionN ? lumaEndN : lumaEndP;
    
    if(((lumaM - lumaN) < 0.0) == ((lumaEndN - lumaN) < 0.0))
    {
        lengthSign = 0.0;
    }
 

    float spanLength = (dstP + dstN);
    dstN = directionN ? dstN : dstP;
    float subPixelOffset = (0.5 + (dstN * (-1.0/spanLength))) * lengthSign;
    float3 rgbF = SampleLocation(float2(
        pos.x + (horzSpan ? 0.0 : subPixelOffset),
        pos.y + (horzSpan ? subPixelOffset : 0.0))).xyz;

    SetOutput(float4(FxaaLerp3(rgbL, rgbF, blendL), 1.0));
}
