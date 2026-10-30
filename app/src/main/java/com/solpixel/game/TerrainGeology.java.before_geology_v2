package com.solpixel.game;

/**
 * Solas Rocks v1 - Regras deterministas de geologia, separadas do relevo e do futuro gerador volumetrico.
 * Nenhuma zona de caverna e marcada ou reservada previamente.
 */
final class TerrainGeology {
    private TerrainGeology() {}

    /** Futuro gerador de cavernas fornece um campo 3D de vazio em [0,1].
     * O campo DEVE ser determinista por coordenada global; 0=rocha solida, 1=vazio.
     * Trocar o campo exige reconstruir chunks/superficie no sistema de streaming.
     */
    interface CaveField { float voidFraction(float x,float y,float z); }
    private static volatile CaveField caves = (x,y,z) -> 0f;
    static void setCaveField(CaveField field) { caves = field==null ? (x,y,z)->0f : field; }
    static float solidFraction(float x,float y,float z) {
        if(y>World.height(x,z))return 0f;
        return clamp(1f-caves.voidFraction(x,y,z));
    }
    /** Impede rochas apoiadas no vazio apos a integracao futura de cavernas.
     * Nao decide onde as cavernas existirao; apenas consulta o campo vigente.
     */
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
    static float rockDensity(float x,float z) {
        float slope=slope(x,z);
        float altitude=World.height(x,z);
        float forest=World.forestDensity(x,z);
        float geology=smooth(-.36f,.30f,noise(x*.0028f+273f,z*.0028f-93f));
        // Grandes macicos de colinas e cumes podem permanecer verdes e quase sem pedra.
        float meadow=smooth(.08f,.38f,noise(x*.0016f-503f,z*.0016f+301f));
        float rugged=smooth(.22f,1.35f,Math.abs(slope(x+11f,z)-slope(x-11f,z))+
                            Math.abs(slope(x,z+11f)-slope(x,z-11f)));
        float cliffs=smooth(.31f,1.45f,slope);
        float exposure=smooth(40f,220f,altitude);
        float base=.018f + .36f*cliffs + .12f*rugged + .095f*forest + .045f*exposure;
        return clamp(base*(.12f+.88f*geology)*(1f-.94f*meadow));
    }
    static float rockScale(float x,float z) {
        float s=slope(x,z);
        return .8f+2.8f*smooth(.25f,1.6f,s)+.8f*smooth(60f,220f,World.height(x,z));
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
    static float rand(int x,int z) {return .5f+.5f*hash(x,z);}
    private static float smooth(float a,float b,float x){float t=clamp((x-a)/(b-a));return t*t*(3f-2f*t);}
    private static float clamp(float x){return Math.max(0f,Math.min(1f,x));}
}
