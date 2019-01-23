const mat3 RGBtoYIQ = mat3(0.299, 0.596, 0.212, 
                           0.587,-0.275,-0.523, 
                           0.114,-0.321, 0.311);

const mat3 YIQtoRGB = mat3(1.0, 1.0, 1.0,
                           0.95568806036115671171,-0.27158179694405859326,-1.1081773266826619523,
                           0.61985809445637075388,-0.64687381613840131330, 1.7050645599191817149);

const float3 val00 = float3( 1.2, 1.2, 1.2);

void main()
{
	float3 c0 = Sample().xyz;
	float3 c1 = RGBtoYIQ * c0;
	c1 = float3(pow(c1.x, val00.x), c1.yz * val00.yz);
	SetOutput(float4(YIQtoRGB * c1, 1.0));
}
