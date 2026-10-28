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
    GameRenderer renderer;
    GameHud hud;
    GLSurfaceView surface;
    private MediaPlayer ambient;
    private SharedPreferences preferences;
    volatile boolean musicOn=true;
    volatile float musicVolume=.55f;
    volatile int renderDistance=260;
    // solpixel-v05: parametros persistidos, sem quota fixa de arvores.
    volatile float treeDensity=1.20f;
    volatile float clusterScale=1.30f;
    volatile boolean cloudsOn=true;
    volatile int worldRevision=0;


    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        preferences=getSharedPreferences("SolPixelSettings",MODE_PRIVATE);
        musicOn=preferences.getBoolean("music",true);
        musicVolume=preferences.getFloat("volume",.55f);
        renderDistance=preferences.getInt("distance",260);
        treeDensity=Math.max(.45f,Math.min(1.80f,preferences.getFloat("trees",1.20f)));
        clusterScale=Math.max(.65f,Math.min(2.3f,preferences.getFloat("clusters",1.30f)));
        cloudsOn=preferences.getBoolean("clouds",true);
        World.TREE_DENSITY=treeDensity;
        World.CLUSTER_SCALE=clusterScale;
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
        renderer.reset(); state = 1;
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
        preferences.edit().putInt("distance",renderDistance).apply();hud.invalidate();
    }
    void setTreeDensity(float density){
        treeDensity=Math.max(.45f,Math.min(1.80f,density));
        World.TREE_DENSITY=treeDensity;
        preferences.edit().putFloat("trees",treeDensity).apply();
        worldRevision++;
        hud.invalidate();
    }
    void setClusterScale(float scale){
        clusterScale=Math.max(.65f,Math.min(2.30f,scale));
        World.CLUSTER_SCALE=clusterScale;
        preferences.edit().putFloat("clusters",clusterScale).apply();
        worldRevision++;
        hud.invalidate();
    }
    void setCloudsOn(boolean enabled){
        cloudsOn=enabled;
        preferences.edit().putBoolean("clouds",enabled).apply();
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
