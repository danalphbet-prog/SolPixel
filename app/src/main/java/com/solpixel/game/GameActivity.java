package com.solpixel.game;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.media.MediaPlayer;
import android.content.SharedPreferences;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public final class GameActivity extends Activity {
    // 0 = menu, 1 = jogando, 2 = pausa, 3 = configuracoes
    volatile int state = 0;
    volatile int selectedSlot=0; // mochila 7 slots, espada inicial no slot zero
    GameRenderer renderer;
    GameHud hud;
    GLSurfaceView surface;
    private MediaPlayer ambient;
    private SharedPreferences preferences;
    volatile boolean musicOn=true;
    volatile float musicVolume=.55f;
    // solpixel-v08: distancia menor, sem configuracao de geracao de arvores.
    volatile int renderDistance=260;
    volatile boolean cloudsOn=true;
    volatile int worldRevision=0;
    // 0=Leve, 1=Desempenho, 2=Grafico, -1=ajuste personalizado.
    volatile int graphicsPreset=1;
    volatile boolean forestShadowOn=true;
    volatile int forestShadowDistance=115;
    // Onde cada faixa LOD comeca: 20%,50%,80%,90%,95%,99%.
    volatile int[] lodDistances={45,90,180,300,700,3000};
    volatile int lodRevision=0;


    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        preferences=getSharedPreferences("SolPixelSettings",MODE_PRIVATE);
        musicOn=preferences.getBoolean("music",true);
        musicVolume=preferences.getFloat("volume",.55f);
        cloudsOn=preferences.getBoolean("clouds",true);
        graphicsPreset=Math.max(-1,Math.min(2,preferences.getInt("graphicsPreset",1)));
        forestShadowOn=preferences.getBoolean("forestShadowOn",true);
        forestShadowDistance=Math.max(10,Math.min(1000,preferences.getInt("forestShadowDistance",115)));
        int[] loadedLods={45,90,180,300,700,3000};
        for(int i=0;i<loadedLods.length;i++){
            int d=preferences.getInt("lodStart"+i,loadedLods[i]);
            loadedLods[i]=Math.max(i==0?10:loadedLods[i-1]+10,Math.min(4000,d));
        }
        lodDistances=loadedLods;
        int savedDistance=preferences.getInt("distance",260);
        // Migracao unica: descarta o raio antigo de 520 a 1000 metros.
        if(!preferences.getBoolean("v08_distance_migrated",false)){
            savedDistance=260;
            preferences.edit().putInt("distance",260)
                .putBoolean("v08_distance_migrated",true).apply();
        }
        int[] distanceOptions={180,260,340,420};
        renderDistance=distanceOptions[0];
        for(int option:distanceOptions)
            if(Math.abs(savedDistance-option)<Math.abs(savedDistance-renderDistance))
                renderDistance=option;
        try {
            ambient=MediaPlayer.create(this,R.raw.ambient_music);
            if(ambient!=null){ambient.setLooping(true);ambient.setVolume(musicVolume,musicVolume);}
        }catch(Exception ignored){ambient=null;}
        renderer = new GameRenderer(this);
        surface = new GLSurfaceView(this);
        surface.setEGLContextClientVersion(3);
        surface.setEGLConfigChooser(8,8,8,8,24,0);
        surface.setPreserveEGLContextOnPause(true);
        surface.setRenderer(renderer);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        hud = new GameHud(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1,-1));
        root.addView(hud, new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
    }
    void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    void play() {
        selectedSlot=0;renderer.reset(); state = 1;
        hud.resetControls(); hud.invalidate();
        updateAudio();
    }
    void pause() {
        if (state == 1) { state = 2; renderer.move(0,0); hud.resetControls(); hud.invalidate(); }
    }
    void resumeGame() { state = 1; hud.invalidate();updateAudio(); }
    void mainMenu() { state = 0; renderer.move(0,0); hud.resetControls(); hud.invalidate();updateAudio(); }
    void openSettings(){if(state==2){state=3;hud.invalidate();}}
    void closeSettings(){if(state==3){state=2;hud.invalidate();}}
    void setMusicOn(boolean enabled){
        musicOn=enabled;preferences.edit().putBoolean("music",enabled).apply();updateAudio();hud.invalidate();
    }
    void setMusicVolume(float value){
        musicVolume=Math.max(0f,Math.min(1f,value));
        preferences.edit().putFloat("volume",musicVolume).apply();updateAudio();hud.invalidate();
    }
    void setRenderDistance(int distance){
        int[] options={180,260,340,420};
        int closest=options[0];
        for(int v:options)if(Math.abs(distance-v)<Math.abs(distance-closest))closest=v;
        renderDistance=closest;
        graphicsPreset=-1;
        preferences.edit().putInt("distance",renderDistance)
            .putInt("graphicsPreset",-1).apply();hud.invalidate();
    }
    void setCloudsOn(boolean enabled){
        cloudsOn=enabled;
        preferences.edit().putBoolean("clouds",enabled).apply();
        hud.invalidate();
    }
    void applyGraphicsPreset(int level){
        int p=Math.max(0,Math.min(2,level));
        graphicsPreset=p;
        int[] lod;
        if(p==0){
            forestShadowOn=false;
            forestShadowDistance=10;
            renderDistance=180;
            cloudsOn=false;
            lod=new int[]{20,35,60,100,180,300};
        }else if(p==1){
            forestShadowOn=true;
            forestShadowDistance=115;
            renderDistance=260;
            cloudsOn=true;
            lod=new int[]{45,90,180,300,700,3000};
        }else{
            forestShadowOn=true;
            // Limite maximo disponivel. A nitidez cai conforme a area aumenta.
            forestShadowDistance=1000;
            renderDistance=420;
            cloudsOn=true;
            lod=new int[]{70,130,260,600,1700,3500};
        }
        lodDistances=lod;
        lodRevision++;
        SharedPreferences.Editor editor=preferences.edit()
            .putInt("graphicsPreset",graphicsPreset)
            .putBoolean("forestShadowOn",forestShadowOn)
            .putInt("forestShadowDistance",forestShadowDistance)
            .putInt("distance",renderDistance)
            .putBoolean("clouds",cloudsOn);
        for(int i=0;i<lod.length;i++)editor.putInt("lodStart"+i,lod[i]);
        editor.apply();hud.invalidate();
    }
    void setForestShadowDistance(int distance){
        forestShadowDistance=Math.max(10,Math.min(1000,distance));
        forestShadowOn=true;
        graphicsPreset=-1;
        preferences.edit().putInt("forestShadowDistance",forestShadowDistance)
            .putBoolean("forestShadowOn",true).putInt("graphicsPreset",-1).apply();
        hud.invalidate();
    }
    void setForestShadowOn(boolean enabled){
        forestShadowOn=enabled;
        graphicsPreset=-1;
        preferences.edit().putBoolean("forestShadowOn",enabled).putInt("graphicsPreset",-1).apply();
        hud.invalidate();
    }
    void setLodDistance(int index,int distance){
        if(index<0||index>=6)return;
        int[] arr=lodDistances.clone();
        int min=index==0?10:arr[index-1]+10;
        int max=index==5?4000:arr[index+1]-10;
        arr[index]=Math.max(min,Math.min(max,distance));
        lodDistances=arr;
        lodRevision++;
        graphicsPreset=-1;
        preferences.edit().putInt("lodStart"+index,arr[index])
            .putInt("graphicsPreset",-1).apply();
        hud.invalidate();
    }
    private void updateAudio(){
        if(ambient==null)return;
        try {
            ambient.setVolume(musicVolume,musicVolume);
            if(musicOn && state!=0){if(!ambient.isPlaying())ambient.start();}
            else if(ambient.isPlaying())ambient.pause();
        }catch(IllegalStateException ignored){}
    }
    @Override protected void onPause() {
        if (state == 1) pause();
        if(ambient!=null && ambient.isPlaying())ambient.pause();
        surface.onPause(); super.onPause();
    }
    @Override protected void onResume() {
        super.onResume(); surface.onResume(); immersive();updateAudio();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }
    @Override protected void onDestroy(){
        if(ambient!=null){ambient.release();ambient=null;}
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (state == 1) pause();
        else if (state == 3) closeSettings();
        else if (state == 2) resumeGame();
        else super.onBackPressed();
    }
}
