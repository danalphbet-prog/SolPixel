package com.solpixel.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public final class GameHud extends View {
    private final GameActivity game;
    private final Paint p = new Paint(3);
    private int moveId = -1, lookId = -1, jumpId = -1;
    private float lastLookX,lastLookY, stickX,stickY;
    private float density;
    GameHud(GameActivity game) {
        super(game); this.game = game;
        density=getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }
    private float dp(float v) { return v*density; }
    void resetControls() {
        moveId=lookId=jumpId=-1; stickX=stickY=0;
        game.renderer.move(0,0);
    }
    private void ink(int color) { p.setColor(color); p.setStyle(Paint.Style.FILL); p.setStrokeWidth(dp(2)); }
    private void ring(Canvas c,float x,float y,float r,int color,float width) {
        ink(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(width)); c.drawCircle(x,y,r,p); p.setStyle(Paint.Style.FILL);
    }
    private void rounded(Canvas c,float l,float t,float r,float b,float rad,int color) {
        ink(color); c.drawRoundRect(new RectF(l,t,r,b),dp(rad),dp(rad),p);
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold) {
        ink(color); p.setTypeface(bold?android.graphics.Typeface.create("sans-serif-black",1):
          android.graphics.Typeface.create("sans-serif",0));
        p.setTextSize(dp(size)); p.setTextAlign(Paint.Align.CENTER);
        c.drawText(s,x,y,p);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(game.state==0) {
            // Janela de titulo sobre o mundo 3D, ja renderizado ao fundo.
            ink(0xA50B1320); c.drawRect(0,0,w,h,p);
            rounded(c,w*.5f-dp(218),h*.12f,w*.5f+dp(218),h*.84f,18,0xC9162634);
            text(c,"SOL PIXEL",w/2,h*.31f,49,0xFFFFE6B2,true);
            text(c,"UM MUNDO PARA EXPLORAR",w/2,h*.40f,13,0xFFE5D6BC,false);
            rounded(c,w/2-dp(115),h*.52f,w/2+dp(115),h*.52f+dp(64),13,0xFFF4BC77);
            text(c,"PLAY",w/2,h*.52f+dp(42),26,0xFF182331,true);
            text(c,"TERRENO PROCEDURAL  •  LUZ  •  POEIRA",w/2,h*.77f,11,0xFFB7C9C6,false);
            return;
        }
        if(game.state==2) {
            ink(0xAE0C1722); c.drawRect(0,0,w,h,p);
            rounded(c,w/2-dp(190),h*.18f,w/2+dp(190),h*.85f,18,0xE8172632);
            text(c,"PAUSADO",w/2,h*.36f,37,0xFFFFE6B2,true);
            rounded(c,w/2-dp(125),h*.46f,w/2+dp(125),h*.46f+dp(58),12,0xFFF2B86E);
            text(c,"CONTINUAR",w/2,h*.46f+dp(38),19,0xFF243342,true);
            rounded(c,w/2-dp(125),h*.64f,w/2+dp(125),h*.64f+dp(46),11,0xFF405568);
            text(c,"MENU",w/2,h*.64f+dp(30),17,0xFFFFFFFF,true);
            return;
        }
        // Joystick na esquerda.
        float jx=dp(99), jy=h-dp(106), jr=dp(68);
        ink(0x404B6372); c.drawCircle(jx,jy,jr,p);
        ring(c,jx,jy,jr,0x97DCE7D9,1.8f);
        ink(0xBDCFDCC8); c.drawCircle(jx+stickX*jr*.63f,jy+stickY*jr*.63f,dp(30),p);
        ring(c,jx+stickX*jr*.63f,jy+stickY*jr*.63f,dp(30),0xFFFFFFFF,1.5f);
        // Botao pular na direita.
        float bx=w-dp(103), by=h-dp(100), br=dp(49);
        ink(jumpId>=0?0xEDFCD89B:0x93F4C884); c.drawCircle(bx,by,br,p);
        ring(c,bx,by,br,0xEDFFE9BE,2.2f);
        text(c,"PULAR",bx,by+dp(6),15,0xFF23343E,true);
        // Pause pequeno e centralizado no topo.
        float px=w/2, py=dp(28);
        rounded(c,px-dp(24),py-dp(17),px+dp(24),py+dp(17),9,0x82334751);
        ink(0xFFFFE5BE); c.drawRoundRect(new RectF(px-dp(7),py-dp(9),px-dp(2),py+dp(9)),2,2,p);
        c.drawRoundRect(new RectF(px+dp(3),py-dp(9),px+dp(8),py+dp(9)),2,2,p);
        text(c,"DESLIZE PARA OLHAR",w/2,h-dp(19),11,0xAFEBE7D9,false);
        // Mira sutil no centro da tela.
        ring(c,w/2,h/2,dp(2.5f),0x80FFF4D7,1.2f);
    }
    private boolean within(float x,float y,float cx,float cy,float rx,float ry){ return Math.abs(x-cx)<=rx && Math.abs(y-cy)<=ry; }
    @Override public boolean onTouchEvent(MotionEvent e) {
        int action=e.getActionMasked(); int index=e.getActionIndex();
        float w=getWidth(),h=getHeight();
        if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            int id=e.getPointerId(index), x0=index;
            float x=e.getX(x0), y=e.getY(x0);
            if(game.state==0) {
                if(within(x,y,w/2,h*.52f+dp(32),dp(130),dp(41))) game.play();
            } else if(game.state==2) {
                if(within(x,y,w/2,h*.46f+dp(29),dp(140),dp(35))) game.resumeGame();
                else if(within(x,y,w/2,h*.64f+dp(23),dp(140),dp(30))) game.mainMenu();
            } else if(within(x,y,w/2,dp(28),dp(35),dp(29))) {
                game.pause();
            } else if(x < w*.35f && y>h*.38f && moveId<0) {
                moveId=id; updateStick(x,y,h);
            } else if(x>w*.70f && y>h*.53f && jumpId<0) {
                jumpId=id; game.renderer.jump();
            } else if(lookId<0) {
                lookId=id; lastLookX=x;lastLookY=y;
            }
        } else if(action==MotionEvent.ACTION_MOVE && game.state==1) {
            if(moveId>=0){int i=e.findPointerIndex(moveId);if(i>=0)updateStick(e.getX(i),e.getY(i),h);}
            if(lookId>=0) {
                int i=e.findPointerIndex(lookId);
                if(i>=0){float x=e.getX(i),y=e.getY(i);
                    game.renderer.look(x-lastLookX,y-lastLookY);lastLookX=x;lastLookY=y;}
            }
        } else if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP || action==MotionEvent.ACTION_CANCEL) {
            int id= action==MotionEvent.ACTION_CANCEL?-999:e.getPointerId(index);
            if(moveId==id || action==MotionEvent.ACTION_CANCEL) {
                moveId=-1; stickX=stickY=0; game.renderer.move(0,0);
            }
            if(lookId==id || action==MotionEvent.ACTION_CANCEL) lookId=-1;
            if(jumpId==id || action==MotionEvent.ACTION_CANCEL) jumpId=-1;
        }
        invalidate(); return true;
    }
    private void updateStick(float x,float y,float h) {
        float r=dp(68), dx=(x-dp(99))/r, dy=(y-(h-dp(106)))/r;
        float l=(float)Math.hypot(dx,dy);
        if(l>1){dx/=l;dy/=l;}
        stickX=dx;stickY=dy;
        game.renderer.move(dx,-dy);
    }
}
