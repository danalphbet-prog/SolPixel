package com.solpixel.game;

/**
 * Solas 0.2: relevo adicional em coordenadas globais, sem estado por frame.
 * A seed faz parte dos salts inteiros; o mesmo (x,z) sempre gera a mesma altura.
 * A camada e aplicada sobre World.baseHeight (nao altera o relevo original).
 */
final class TerrainDetails {
    private TerrainDetails() {}

    static final boolean TERRAIN_DETAILS_ENABLED = true;
    static final boolean PLAINS_HILLS_ENABLED = true;
    static final boolean FOREST_HILLS_ENABLED = true;
    static final float PLAINS_HILL_HEIGHT = 2.0f;
    static final float PLAINS_HILL_RADIUS = 20f;
    static final float FOREST_HILL_HEIGHT = 3.0f;
    static final float FOREST_HILL_RADIUS = 14f;
    static final boolean MICRO_BUMPS_ENABLED = true;
    static final float MICRO_BUMP_MAX_HEIGHT = .8f;
    static final boolean DEPRESSIONS_ENABLED = true;
    static final float DEPRESSION_MAX_DEPTH = .7f;
    static final boolean CLIFFS_ENABLED = true;
    static final float CLIFF_HEIGHT_MIN = 8f;
    static final float CLIFF_HEIGHT_MAX = 70f;
    static final boolean CLIFF_ROCK_TEXTURE_ENABLED = true;
    static final boolean CLIFF_MOSS_ENABLED = true;
    static final boolean TERRAIN_DETAIL_LOD_ENABLED = true;
    static final int NEAR_DETAIL_SUBDIVISIONS = 3;
    static final int NEAR_DETAIL_HALF_CELLS = 16;

    static float apply(float x,float z,float original) {
        if (!TERRAIN_DETAILS_ENABLED) return original;
        // Area de surgimento continua transitavel; sem degrau entre o antigo e o novo.
        float spawn = smooth(22f,65f,(float)Math.hypot(x,z));
        float forest = World.forestTerrainMask(x,z);
        float variation = .26f+.74f*smooth(-.35f,.34f,
                noise(x*.0071f+511f,z*.0071f-229f));
        float hills = 0f;
        if (PLAINS_HILLS_ENABLED || FOREST_HILLS_ENABLED) {
            float plainRadius = Math.max(5f,PLAINS_HILL_RADIUS);
            float forestRadius = Math.max(5f,FOREST_HILL_RADIUS);
            float radius = plainRadius+(forestRadius-plainRadius)*forest;
            float amplitude = (PLAINS_HILLS_ENABLED?PLAINS_HILL_HEIGHT*(1f-forest):0f)
                            +(FOREST_HILLS_ENABLED?FOREST_HILL_HEIGHT*forest:0f);
            float scale = 1f/radius;
            float broad = noise(x*scale*.68f+119f,z*scale*.68f-293f);
            float local = noise(x*scale*1.33f-331f,z*scale*1.33f+77f);
            hills = (broad*.75f+local*.25f)*amplitude*variation;
        }
        float bumps = 0f;
        if (MICRO_BUMPS_ENABLED) {
            float domainX = x+noise(x*.12f+12f,z*.12f-44f)*1.7f;
            float domainZ = z+noise(x*.12f-31f,z*.12f+64f)*1.7f;
            float group = smooth(-.32f,.25f,noise(domainX*.095f-19f,domainZ*.095f+87f));
            float n = noise(domainX*.33f+211f,domainZ*.33f-73f);
            float peak = smooth(.00f,.62f,n);
            bumps = MICRO_BUMP_MAX_HEIGHT*group*peak*peak*(.64f+.36f*forest);
        }
        float depressions = 0f;
        if (DEPRESSIONS_ENABLED) {
            float group = smooth(-.24f,.30f,noise(x*.085f+619f,z*.085f-31f));
            float basin = smooth(.18f,.67f,noise(x*.175f-129f,z*.175f+311f));
            float erosion = .15f*smooth(.15f,.66f,noise(x*.11f+43f,z*.045f-177f));
            depressions = DEPRESSION_MAX_DEPTH*group*basin*(.72f+.28f*forest)+erosion*group;
        }
        float result = original+spawn*(hills+bumps-depressions);
        return CLIFFS_ENABLED?plateaus(x,z,result,original):result;
    }

    /** Maximo de platos locais; as paredes sao rampas extremamente inclinadas,
     * sem prometer overhangs numa malha de altura. */
    private static float plateaus(float x,float z,float result,float original){
        final float cell=310f;
        int gx=(int)Math.floor(x/cell),gz=(int)Math.floor(z/cell);
        // A mascara espacial e independente da camera e dos chunks.
        float spawn=smooth(92f,165f,(float)Math.hypot(x,z));
        if(spawn<=0f)return result;
        for(int j=gz-1;j<=gz+1;j++)for(int i=gx-1;i<=gx+1;i++){
            if(unit(i*13+49,j*17-97)>.255f)continue;
            float cx=(i+.5f+(unit(i*43+9,j*23-41)-.5f)*.46f)*cell;
            float cz=(j+.5f+(unit(i*31-7,j*37+59)-.5f)*.46f)*cell;
            float dx=x-cx,dz=z-cz;
            float radius=55f+69f*unit(i*47-19,j*29+101);
            if(Math.abs(dx)>radius+19f || Math.abs(dz)>radius+19f)continue;
            float dist=(float)Math.hypot(dx,dz);
            if(dist>radius+19f)continue;
            float rough = noise(x*.031f+213f,z*.031f-53f)*6f
                        + noise(x*.072f-71f,z*.072f+227f)*2.2f;
            float rim=3.4f+3.7f*unit(i*71+27,j*79-11); // espessura da transicao (m)
            float interior=smooth(-rim,rim,radius-dist+rough);
            if(interior<=0f)continue;
            float centerHeight=World.baseHeight(cx,cz);
            float rise=CLIFF_HEIGHT_MIN+(CLIFF_HEIGHT_MAX-CLIFF_HEIGHT_MIN)
                     *unit(i*89+67,j*97-19);
            // Cume herbaceo quase horizontal, terreno original preservado na base.
            float top=centerHeight+rise;
            float raised = original+(top-original)*interior*spawn;
            if(raised>result)result=raised;
        }
        return result;
    }

    private static float noise(float x,float z){
        int ix=(int)Math.floor(x),iz=(int)Math.floor(z);
        float tx=x-ix,tz=z-iz;
        tx=tx*tx*(3f-2f*tx);tz=tz*tz*(3f-2f*tz);
        float a=hash(ix,iz)*(1f-tx)+hash(ix+1,iz)*tx;
        float b=hash(ix,iz+1)*(1f-tx)+hash(ix+1,iz+1)*tx;
        return a*(1f-tz)+b*tz;
    }
    private static float hash(int x,int z){
        int h=x*374761393+z*668265263+0x19FBC18D;
        h=(h^(h>>>13))*1274126177;
        return ((h^(h>>>16))&0x7fffffff)/1073741824f-1f;
    }
    private static float unit(int x,int z){return .5f+.5f*hash(x,z);}
    private static float smooth(float a,float b,float value){
        float t=Math.max(0f,Math.min(1f,(value-a)/(b-a)));
        return t*t*(3f-2f*t);
    }
}
