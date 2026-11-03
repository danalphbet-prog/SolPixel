package com.solpixel.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/** Full-screen film grain over the GL view AND game HUD; does not consume touches. */
final class FilmGrainOverlay extends View {
    private final GameActivity game;
    private final Bitmap tile=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);
    private final int[] pixels=new int[128*128];
    private final Paint paint=new Paint();
    private int seed=0x6F13A71;
    FilmGrainOverlay(Context context){
        super(context);game=(GameActivity)context;
        paint.setFilterBitmap(false);
        paint.setShader(new BitmapShader(tile,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT));
        setClickable(false);setFocusable(false);setWillNotDraw(false);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(!game.filmGrainOn){postInvalidateDelayed(300);return;}
        // Grain neutral in average luminance, noticeable but without color flicker.
        int x=seed;
        for(int i=0;i<pixels.length;i++){
            x^=x<<13;x^=x>>>17;x^=x<<5;
            int r=(x>>>4)&255;
            int alpha=22+(r%74);
            pixels[i]=r<126?(alpha<<24):(alpha<<24)|0x00FFFFFF;
        }
        seed=x;
        tile.setPixels(pixels,0,128,0,0,128,128);
        c.drawRect(0,0,getWidth(),getHeight(),paint);
        postInvalidateDelayed(65);
    }
}
