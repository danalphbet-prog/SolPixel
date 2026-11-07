package com.solpixel.game;

/** Render-thread bridge for pine combat and filtering removed collision trees. */
final class TreeHarvestHooks {
    private static volatile PineHarvestSystem system;
    private TreeHarvestHooks(){}
    static void register(PineHarvestSystem current){system=current;}
    static int swordSwing(float x,float eyeY,float z,float dx,float dy,float dz,boolean glowing){
        PineHarvestSystem h=system;
        return h==null?0:h.hit(x,eyeY,z,dx,dy,dz,glowing);
    }
    static boolean isFelled(float x,float z){
        PineHarvestSystem h=system;
        return h!=null && h.isFelled(x,z);
    }
}
