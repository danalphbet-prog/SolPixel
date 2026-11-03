package com.solpixel.game;

/** Render-thread bridge for pine combat and filtering removed collision trees. */
final class TreeHarvestHooks {
    private static volatile PineHarvestSystem system;
    private TreeHarvestHooks(){}
    static void register(PineHarvestSystem current){system=current;}
    static void swordSwing(float x,float z,float dx,float dz){
        PineHarvestSystem h=system;
        if(h!=null)h.hit(x,z,dx,dz);
    }
    static boolean isFelled(float x,float z){
        PineHarvestSystem h=system;
        return h!=null && h.isFelled(x,z);
    }
}
