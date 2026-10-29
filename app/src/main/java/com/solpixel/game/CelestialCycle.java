package com.solpixel.game;

/** Solas v3: ciclo de 11 minutos com sol parado, satelite visual e eclipse prolongado. */
final class CelestialCycle {
    static final float DAY_SECONDS=300f,NIGHT_SECONDS=360f,PERIOD=660f;
    private CelestialCycle(){}
    static float timeInCycle(float elapsed){float t=elapsed%PERIOD;return t<0?t+PERIOD:t;}
    private static float smooth(float a,float b,float value){
        float x=Math.max(0f,Math.min(1f,(value-a)/(b-a)));
        return x*x*(3f-2f*x);
    }
    static float eclipse(float elapsed){
        float t=timeInCycle(elapsed);
        return smooth(DAY_SECONDS,DAY_SECONDS+24f,t)*(1f-smooth(PERIOD-24f,PERIOD,t));
    }
    static void moonDirection(float elapsed,float[] sun,float[] out){
        float t=timeInCycle(elapsed);
        // Durante o dia o satelite viaja lentamente ate o sol;
        // durante a noite permanece sobreposto para sustentar a sombra.
        float offset=t<DAY_SECONDS ? .40f*(1f-t/DAY_SECONDS) :
             t<PERIOD-24f ? 0f : .40f*smooth(PERIOD-24f,PERIOD,t);
        float x=sun[0]+offset*sun[2],y=sun[1],z=sun[2]-offset*sun[0];
        float length=(float)Math.sqrt(x*x+y*y+z*z);
        out[0]=x/length;out[1]=y/length;out[2]=z/length;
    }
}
