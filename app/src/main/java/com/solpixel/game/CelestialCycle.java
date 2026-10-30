package com.solpixel.game;

/** Solas v0.4.1: eclipse gradual desde primeiro contato. Tempo em segundos de jogo. */
final class CelestialCycle {
    static final float DAY_SECONDS=300f,NIGHT_SECONDS=360f,PERIOD=660f;
    static final float FIRST_CONTACT=260f,CENTER=300f,EXIT_START=600f,EXIT_END=660f;
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
        float azimuth=(float)Math.toRadians(146.5);
        float horizon=(float)Math.cos(altitude);
        out[0]=(float)Math.sin(azimuth)*horizon;
        out[1]=(float)Math.sin(altitude);
        out[2]=(float)-Math.cos(azimuth)*horizon;
    }
    static float eclipse(float elapsed){
        float t=timeInCycle(elapsed);
        return smooth(FIRST_CONTACT,CENTER,t)*(1f-smooth(EXIT_START,EXIT_END,t));
    }
    /** Orbita estilizada; posicao aparente da Lua sempre vinculada ao Sol. */
    static void moonDirection(float elapsed,float[] sun,float[] out){
        float t=timeInCycle(elapsed);
        // Primeira tangencia em 260 s; centro depois de 40 s. O alinhamento e
        // preservado durante a noite, e a saida ocupa os ultimos 60 segundos.
        float separation;
        if(t<FIRST_CONTACT) separation=.052f+.21f*(1f-smooth(0f,FIRST_CONTACT,t));
        else if(t<CENTER) separation=.052f*(1f-smooth(FIRST_CONTACT,CENTER,t));
        else if(t<EXIT_START) separation=0f;
        else separation=.262f*smooth(EXIT_START,EXIT_END,t);
        // Os vetores tangentes fazem a Lua circundar a linha visual no sentido horario.
        float angle=-2f*(float)Math.PI*t/PERIOD;
        float rx=-sun[2],rz=sun[0],rl=(float)Math.hypot(rx,rz);
        if(rl<.0001f){rx=1f;rz=0f;rl=1f;}
        rx/=rl;rz/=rl;
        float bx=-sun[1]*rz,by=rl,bz=sun[1]*rx;
        float ca=(float)Math.cos(angle),sa=(float)Math.sin(angle);
        float mx=sun[0]+separation*(ca*rx+sa*bx);
        float my=sun[1]+separation*sa*by;
        float mz=sun[2]+separation*(ca*rz+sa*bz);
        float inv=1f/(float)Math.sqrt(mx*mx+my*my+mz*mz);
        out[0]=mx*inv;out[1]=my*inv;out[2]=mz*inv;
    }
}
