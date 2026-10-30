package com.solpixel.game;

/**
 * Solas Geology v2.
 * Uma unica mascara espacial guia a pedra exposta e as rochas GLB.
 * As cavernas continuam independentes e dinamicas: nenhum ponto e reservado.
 */
final class TerrainGeology {
    private TerrainGeology() {}

    /** 0 = preenchido, 1 = vazio. Substituivel futuramente por campo 3D. */
    interface CaveField { float voidFraction(float x,float y,float z); }
    private static final CaveField NO_CAVES = (x,y,z) -> 0f;
    private static volatile CaveField caves = NO_CAVES;
    static void setCaveField(CaveField field) { caves=field==null?NO_CAVES:field; }
    static float solidFraction(float x,float y,float z) {
        if(y>World.height(x,z))return 0f;
        return clamp(1f-caves.voidFraction(x,y,z));
    }
    static boolean rockSupported(float x,float z,float radius) {
        float y=World.height(x,z);
        if(solidFraction(x,y-1.2f,z)<.65f || solidFraction(x,y-3f,z)<.45f)return false;
        float d=Math.max(.7f,Math.min(4f,radius*.55f));
        for(int i=0;i<4;i++){
            float dx=i==0?d:i==1?-d:0, dz=i==2?d:i==3?-d:0;
            float h=World.height(x+dx,z+dz);
            if(solidFraction(x+dx,h-1.2f,z+dz)<.50f)return false;
        }
        return true;
    }
    static float slope(float x,float z) {
        float dx=(World.height(x+3f,z)-World.height(x-3f,z))/6f;
        float dz=(World.height(x,z+3f)-World.height(x,z-3f))/6f;
        return (float)Math.hypot(dx,dz);
    }

    /**
     * Intensidade 0..1: a mesma que vai para o shader e para os aglomerados.
     * Usar a sobrecarga com declive conhecido evita amostrar altura 4x por vertice.
     * Manchas largas e faixas anisotropicas coerentes se estendem pela encosta.
     */
    static float rockExposure(float x,float z,float gradient,float altitude) {
        // Provincias amplas, incluindo cumes completamente herbaceos.
        float province=smooth(-.34f,.19f,noise(x*.0021f+273f,z*.0021f-93f));
        float green=smooth(.15f,.46f,noise(x*.00145f-503f,z*.00145f+301f));
        // Veios alongados, com sinuosity: um paredao nao recebe manchas uniformes.
        float warp=noise(x*.0016f+43f,z*.0016f-119f)*38f;
        float band=noise((x+z*.33f+warp)*.0051f+107f,
                         (z-x*.24f-warp)*.00115f-73f);
        float streak=smooth(-.30f,.23f,band);
        float secondary=smooth(-.40f,.21f,noise(x*.009f-217f,z*.004f+173f));
        float steep=smooth(.13f,.94f,gradient);
        float tall=smooth(70f,205f,altitude);
        float bedrock=province*(.23f+.77f*streak)*(.83f+.17f*secondary);
        // Elevacao so aumenta um pouco a chance; nao obriga picos rochosos.
        float natural=clamp(bedrock*(.11f+.84f*steep+.05f*tall)*(1f-.96f*green));
        // Acima de 50% de declive a pedra entra progressivamente;
        // acima de ~55 graus, e praticamente integral, independente da provincia.
        float forced=smooth(.50f,1.48f,gradient);
        return Math.max(natural,forced);
    }
    static float rockExposure(float x,float z) {
        return rockExposure(x,z,slope(x,z),World.height(x,z));
    }
    static float rockDensity(float x,float z) {
        float slope=slope(x,z),mask=rockExposure(x,z,slope,World.height(x,z));
        return clamp(.012f+.72f*mask+.035f*smooth(.33f,1.5f,slope));
    }
    static float rockScale(float x,float z) {
        float g=slope(x,z);
        return .78f+1.65f*smooth(.20f,1.35f,g)+.32f*smooth(90f,230f,World.height(x,z));
    }
    static float isolatedChance(float x,float z,float gradient) {
        // Na planicie, pedras solitarias sao visivelmente mais comuns que na v1.
        float plain=1f-smooth(.20f,.75f,gradient);
        float forest=World.forestDensity(x,z);
        return clamp(.035f+.145f*plain+.055f*forest+.045f*rockExposure(x,z,gradient,World.height(x,z)));
    }
    private static float noise(float x,float z) {
        int ix=(int)Math.floor(x),iz=(int)Math.floor(z);
        float a=x-ix,b=z-iz;a=a*a*(3-2*a);b=b*b*(3-2*b);
        float p=hash(ix,iz)*(1-a)+hash(ix+1,iz)*a;
        float q=hash(ix,iz+1)*(1-a)+hash(ix+1,iz+1)*a;
        return p*(1-b)+q*b;
    }
    private static float hash(int x,int z) {
        int n=x*374761393+z*668265263+0x27D4EB2D;
        n=(n^(n>>>13))*1274126177;
        return ((n^(n>>>16))&0x7fffffff)/1073741824f-1f;
    }
    static float rand(int x,int z){return .5f+.5f*hash(x,z);}
    private static float smooth(float a,float b,float x){float t=clamp((x-a)/(b-a));return t*t*(3f-2f*t);}
    private static float clamp(float x){return Math.max(0f,Math.min(1f,x));}
}
