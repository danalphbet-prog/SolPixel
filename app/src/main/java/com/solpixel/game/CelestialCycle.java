package com.solpixel.game;

/** Solas v0.4: sol em arco 12-85 graus, eclipse na descida a 45 graus.
 * Manha: 0-180s 12->85; tarde: 180-300s 85->45; noite: 300-660s 45->12.
 * O ciclo se repete sem salto na altitude. A noite e a ocultacao sao fantásticas.
 */
final class CelestialCycle {
    static final float DAY_SECONDS=300f,NIGHT_SECONDS=360f,PERIOD=660f;
    private CelestialCycle(){}
    static float timeInCycle(float t){float v=t%PERIOD;return v<0?v+PERIOD:v;}
    private static float smooth(float a,float b,float value){
        float f=Math.max(0f,Math.min(1f,(value-a)/(b-a)));
        return f*f*(3f-2f*f);
    }
    static float elevationDegrees(float elapsed){
        float t=timeInCycle(elapsed);
        if(t<180f)return 12f+73f*smooth(0f,180f,t);
        if(t<DAY_SECONDS)return 85f-40f*smooth(180f,DAY_SECONDS,t);
        return 45f-33f*smooth(DAY_SECONDS,PERIOD,t);
    }
    static void sunDirection(float elapsed,float[] out){
        float altitude=(float)Math.toRadians(elevationDegrees(elapsed));
        // Arco solar norte-sul estavel, espelha o ciclo sem descer abaixo do horizonte.
        float azimuth=(float)Math.toRadians(146.5);
        float horizon=(float)Math.cos(altitude);
        out[0]=(float)Math.sin(azimuth)*horizon;
        out[1]=(float)Math.sin(altitude);
        out[2]=(float)-Math.cos(azimuth)*horizon;
    }
    static float eclipse(float elapsed){
        float t=timeInCycle(elapsed);
        return smooth(DAY_SECONDS-4f,DAY_SECONDS+24f,t) *
            (1f-smooth(PERIOD-24f,PERIOD,t));
    }
    static void moonDirection(float elapsed,float[] sun,float[] out){
        float t=timeInCycle(elapsed),phase=t/PERIOD;
        // A lua cruza a linha do sol junto ao eclipse, girando seu eixo no sentido horario.
        float drift=.096f*(float)Math.sin(2.0*Math.PI*(DAY_SECONDS-t)/PERIOD);
        float angle=phase*(float)(2.0*Math.PI);
        float ux=(float)Math.cos(angle),uy=-(float)Math.sin(angle);
        // Base do plano perpendicular ao Sol (estavel para 85 graus).
        float rx=-sun[2],rz=sun[0];
        float rl=(float)Math.hypot(rx,rz);rx/=rl;rz/=rl;
        float bx=-sun[1]*rz,by=rl,bz=sun[1]*rx;
        float mx=sun[0]+drift*(ux*rx+uy*bx);
        float my=sun[1]+drift*uy*by;
        float mz=sun[2]+drift*(ux*rz+uy*bz);
        float m=(float)Math.sqrt(mx*mx+my*my+mz*mz);
        out[0]=mx/m;out[1]=my/m;out[2]=mz/m;
    }
}
