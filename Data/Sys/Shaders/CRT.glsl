void main()
{
    // scanlines
    float vTime = float(GetTime());
    float2 vCoord0 = GetCoordinates();
    int vPos = int( ( vCoord0.y + vTime * 0.5 ) * 272.0 );
    float line_intensity = mod( float(vPos), 2.0 );
    
    // color shift
    float off = line_intensity * 0.0005;
    float2 shift = float2( off, 0 );
    
    // shift R and G channels to simulate NTSC color bleed
    float2 colorShift = float2( 0.001, 0 );
    float r = SampleLocation(vCoord0 + colorShift + shift).x;
    float g = SampleLocation(vCoord0 - colorShift + shift).y;
    float b = SampleLocation(vCoord0).z;
    
    vec4 c = vec4( r, g * 0.99, b, 1.0 ) * clamp( line_intensity, 0.85, 1.0 );
    
    float rollbar = sin( ( vCoord0.y + vTime ) * 4.0 );
    
    SetOutput(c + (rollbar * 0.02));
}
