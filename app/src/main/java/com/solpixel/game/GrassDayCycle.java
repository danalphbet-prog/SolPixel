package com.solpixel.game;

/** Um dia virtual dura 24 minutos; inicio as 07:00. Somente coloracao, sem alterar sol/fisica. */
final class GrassDayCycle {
    private static final float[] HOURS={0f,5f,7f,12f,16.5f,18.8f,21f,24f};
    private static final float[][] TINT={
        {.46f,.58f,.87f},  // noite azul
        {.61f,.75f,.88f},  // madrugada
        {.78f,1.00f,.83f},// manha esverdeada
        {1.00f,1.02f,.73f},// tarde verde-amarelada
        {1.13f,.89f,.64f},// fim de tarde quente
        {.93f,.76f,.71f}, // crepusculo
        {.48f,.61f,.88f}, // noite
        {.46f,.58f,.87f}
    };
    static void color(float seconds,float[] dest){
        float hour=(7f+seconds/60f)%24f;
        int i=0;
        while(i<HOURS.length-2 && hour>HOURS[i+1])i++;
        float t=(hour-HOURS[i])/(HOURS[i+1]-HOURS[i]);
        t=Math.max(0f,Math.min(1f,t));
        t=t*t*(3f-2f*t);
        for(int j=0;j<3;j++)dest[j]=TINT[i][j]+(TINT[i+1][j]-TINT[i][j])*t;
    }
}
