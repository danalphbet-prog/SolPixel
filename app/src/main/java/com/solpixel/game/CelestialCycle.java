package com.solpixel.game;

/**
 * Solas v0.5.5: tres dias consecutivos, iniciando ao meio-dia.
 * Dias 1/2: Lua e Sol em lados opostos. Dia 3: eclipse diurno.
 * A Lua volta continuamente para a orbita normal apos o eclipse.
 */
final class CelestialCycle {
    static final float PERIOD=660f;
    static final float DAY_SECONDS=PERIOD*.5f;
    static final float NIGHT_SECONDS=PERIOD*.5f;
    private static final float TWO_PI=(float)(2.0*Math.PI);
    private static final float ORBIT_X=.910f;
    private static final float ORBIT_Z=-.41460825f;  // ORBIT_X^2 + ORBIT_Z^2 ~ 1
    private CelestialCycle() {}

    static float timeInCycle(float elapsed){
        float t=elapsed%PERIOD;
        return t<0f?t+PERIOD:t;
    }
    static int dayNumber(float elapsed){
        return (int)Math.floor(elapsed/PERIOD)+1;
    }
    private static float smooth(float start,float end,float x){
        float t=Math.max(0f,Math.min(1f,(x-start)/(end-start)));
        return t*t*(3f-2f*t);
    }
    private static float phase(float elapsed){return timeInCycle(elapsed)/PERIOD;}
    private static boolean isEclipseDay(float elapsed){
        int day=(int)Math.floor(elapsed/PERIOD);
        return Math.floorMod(day,3)==2;
    }
    private static float orbitAngle(float elapsed){return TWO_PI*phase(elapsed);}

    /** t=0: sol no zenite; 1/4 do ciclo: poente; 1/2: meia-noite. */
    static void sunDirection(float elapsed,float[] out){
        float angle=orbitAngle(elapsed);
        float horizontal=-(float)Math.sin(angle);
        out[0]=ORBIT_X*horizontal;
        out[1]=(float)Math.cos(angle);
        out[2]=ORBIT_Z*horizontal;
    }
    static float elevationDegrees(float elapsed){
        return (float)Math.toDegrees(Math.asin(Math.cos(orbitAngle(elapsed))));
    }
    static float night(float elapsed){
        float sunY=(float)Math.cos(orbitAngle(elapsed));
        return 1f-smooth(-.20f,.10f,sunY);
    }
    static float hourOfDay(float elapsed){
        return (12f+phase(elapsed)*24f)%24f;
    }
    /** Apenas o terceiro dia produz eclipse, no inicio da tarde. */
    static float eclipse(float elapsed){
        if(!isEclipseDay(elapsed))return 0f;
        float p=phase(elapsed);
        return smooth(.141f,.170f,p)*(1f-smooth(.181f,.210f,p));
    }
    /**
     * Espelha a orbita normal, sem interpolar vetores antipodais diretamente.
     * Desloca a fase lunar por PI -> 0 -> PI no dia 3, sempre suavemente.
     */
    static void moonDirection(float elapsed,float[] sun,float[] out){
        float track=0f;
        if(isEclipseDay(elapsed)){
            float p=phase(elapsed);
            track=smooth(.065f,.145f,p)*(1f-smooth(.205f,.290f,p));
        }
        float angle=orbitAngle(elapsed)+(float)Math.PI*(1f-track);
        float horizontal=-(float)Math.sin(angle);
        out[0]=ORBIT_X*horizontal;
        out[1]=(float)Math.cos(angle);
        out[2]=ORBIT_Z*horizontal;
    }
}
