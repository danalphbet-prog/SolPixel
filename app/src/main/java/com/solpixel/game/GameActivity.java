package com.solpixel.game;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public final class GameActivity extends Activity {
    // 0 = menu, 1 = jogando, 2 = pausado
    volatile int state = 0;
    GameRenderer renderer;
    GameHud hud;
    GLSurfaceView surface;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
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
    }
    void pause() {
        if (state == 1) { state = 2; renderer.move(0,0); hud.resetControls(); hud.invalidate(); }
    }
    void resumeGame() { state = 1; hud.invalidate(); }
    void mainMenu() { state = 0; renderer.move(0,0); hud.resetControls(); hud.invalidate(); }
    @Override protected void onPause() {
        if (state == 1) pause();
        surface.onPause(); super.onPause();
    }
    @Override protected void onResume() {
        super.onResume(); surface.onResume(); immersive();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }
    @Override public void onBackPressed() {
        if (state == 1) pause();
        else if (state == 2) resumeGame();
        else super.onBackPressed();
    }
}
