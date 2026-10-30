package com.solpixel.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public final class GameHud extends View {
    private final GameActivity game;
    private final Paint p = new Paint(3);
    private final Bitmap backpack,actionIcon,slotSword;
    private boolean inventoryOpen=false;
    private int actionId=-1;
    private static final int[] SLOT_BORDER={0xFFF8E6C0,0xFFA9B6AF};
    private int moveId = -1, lookId = -1, jumpId = -1;
    private int sliderId=-1;
    private int sliderWhich=-1;
    private float lastLookX,lastLookY, stickX,stickY;
    private float density;
    GameHud(GameActivity game) {
        super(game); this.game = game;
        density=getResources().getDisplayMetrics().density;
        backpack=bitmap("backpack_64.png");actionIcon=bitmap("sword_action_64.png");
        slotSword=bitmap("sword_slot_64.png");
        // Usar Canvas acelerado pelo hardware. Layer SOFTWARE prejudicava os toques
        // e o desempenho do GLSurfaceView rodando ao fundo.
    }
    private Bitmap bitmap(String name){
        try(InputStream in=getContext().getAssets().open(name)){
            return BitmapFactory.decodeStream(in);
        }catch(Exception e){return null;}
    }
    private void drawSprite(Canvas c,Bitmap b,float x,float y,float size){
        if(b==null)return;
        ink(0xFFFFFFFF);p.setFilterBitmap(false);
        c.drawBitmap(b,null,new RectF(x-size/2,y-size/2,x+size/2,y+size/2),p);
    }
    private float dp(float v) { return v*density; }
    void resetControls() {
        moveId=lookId=jumpId=sliderId=-1; stickX=stickY=0;
        inventoryOpen=false;actionId=-1;game.renderer.swordAction(false);
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
            rounded(c,w/2-dp(190),h*.12f,w/2+dp(190),h*.91f,18,0xE8172632);
            text(c,"PAUSADO",w/2,h*.27f,34,0xFFFFE6B2,true);
            rounded(c,w/2-dp(125),h*.35f,w/2+dp(125),h*.35f+dp(50),12,0xFFF2B86E);
            text(c,"CONTINUAR",w/2,h*.35f+dp(34),18,0xFF243342,true);
            rounded(c,w/2-dp(125),h*.52f,w/2+dp(125),h*.52f+dp(50),12,0xFF526D81);
            text(c,"CONFIGURAÇÕES",w/2,h*.52f+dp(32),16,0xFFFFFFFF,true);
            rounded(c,w/2-dp(125),h*.69f,w/2+dp(125),h*.69f+dp(47),11,0xFF405568);
            text(c,"MENU",w/2,h*.69f+dp(31),17,0xFFFFFFFF,true);
            return;
        }
        if(game.state==3){
            // solpixel-v08: configuracao compacta, sem controles de geracao.
            ink(0xD90B1825);c.drawRect(0,0,w,h,p);
            rounded(c,w*.19f,h*.07f,w*.81f,h*.95f,12,0xFA1B344A);
            text(c,"CONFIGURAÇÕES",w*.5f,h*.20f,25,0xFFF7E8CE,true);
            drawCheck(c,w*.33f,h*.345f,game.musicOn);
            text(c,"Música ambiente",w*.55f,h*.36f,16,0xFFEAF0F3,false);
            text(c,"VOLUME  "+Math.round(game.musicVolume*100)+"%",w*.5f,h*.475f,15,0xFFF9E3BE,true);
            drawSlider(c,w*.31f,w*.69f,h*.54f,game.musicVolume);
            text(c,"TERRENO  "+game.renderDistance+" m / HORIZONTE 4000 m",w*.5f,h*.635f,13,0xFFF9E3BE,true);
            drawSlider(c,w*.31f,w*.69f,h*.70f,(game.renderDistance-180f)/240f);
            drawCheck(c,w*.33f,h*.785f,game.cloudsOn);
            text(c,"Nuvens 3D",w*.55f,h*.798f,16,0xFFEAF0F3,false);
            rounded(c,w*.41f,h*.865f,w*.59f,h*.935f,8,0xFFF2BD7D);
            text(c,"VOLTAR",w*.5f,h*.916f,13,0xFF193344,true);
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
        // Solas v0.4: 1 hotbar de acesso rapido; sete slots dentro da mochila.
        float sz=dp(55),hotY=h-dp(43);
        rounded(c,w*.5f-sz*.5f,hotY-sz*.5f,w*.5f+sz*.5f,hotY+sz*.5f,7,0xC3182435);
        ring(c,w*.5f,hotY,sz*.54f,0xB6FFF0C3,1.4f);
        if(game.selectedSlot==0){
            if(slotSword!=null)drawSprite(c,slotSword,w*.5f,hotY,dp(44));
            else text(c,"1",w*.5f,hotY+dp(10),24,0xFFFFFFFF,true);
        }
        else text(c,"-",w*.5f,hotY+dp(10),24,0x99FFFFFF,false);
        float bagX=w-dp(41),bagY=dp(42);
        rounded(c,bagX-dp(34),bagY-dp(33),bagX+dp(34),bagY+dp(33),8,0xC1142330);
        if(backpack!=null)drawSprite(c,backpack,bagX,bagY,dp(52));
        else text(c,"BAG",bagX,bagY+dp(5),15,0xFFFFFFFF,true);
        if(game.selectedSlot==0){
            float ax=w-dp(103),ay=h-dp(216);
            boolean glowing=game.renderer.swordActive();
            rounded(c,ax-dp(40),ay-dp(40),ax+dp(40),ay+dp(40),10,
                    glowing?0xDA555C69:0xB9142330);
            if(glowing) ring(c,ax,ay,dp(42),0xFFFFFFFF,2.5f);
            if(actionIcon!=null)drawSprite(c,actionIcon,ax,ay,dp(64));
            else text(c,"LUZ",ax,ay+dp(5),15,0xFFFFFFFF,true);
        }
        if(inventoryOpen){
            ink(0xC70B111C);c.drawRect(0,0,w,h,p);
            float left=w*.5f-dp(202),top=h*.5f-dp(115),cell=dp(82);
            rounded(c,left-dp(17),top-dp(52),left+dp(4*82+17),top+dp(2*82+20),13,0xF0202938);
            text(c,"MOCHILA   7 SLOTS",w*.5f,top-dp(17),18,0xFFF4EDDC,true);
            for(int i=0;i<7;i++){
                int row=i/4,col=i%4;
                float cx=left+col*cell+cell*.5f,cy=top+row*cell+cell*.5f;
                rounded(c,cx-cell*.43f,cy-cell*.43f,cx+cell*.43f,cy+cell*.43f,7,
                        i==game.selectedSlot?0xCC6C5B38:0xCC111D2B);
                ring(c,cx,cy,cell*.42f,i==game.selectedSlot?0xFFFFDA8A:0x889CB2BC,1.1f);
                if(i==0 && slotSword!=null)drawSprite(c,slotSword,cx,cy,dp(50));
            }
            text(c,"TOQUE EM UM SLOT  •  MOCHILA PARA FECHAR",w*.5f,top+dp(2*82+8),10,0xFFE1E7E5,false);
        }
        if(game.renderer.swordActive())postInvalidateDelayed(120);
        // Mira sutil no centro da tela.
        ring(c,w/2,h/2,dp(2.5f),0x80FFF4D7,1.2f);
    }
    private void drawCheck(Canvas c,float x,float y,boolean checked){
        rounded(c,x-dp(12),y-dp(12),x+dp(12),y+dp(12),3,0xFFF3E5D0);
        if(checked){
            ink(0xFF234E5D);p.setStrokeWidth(dp(3));p.setStyle(Paint.Style.STROKE);
            c.drawLine(x-dp(6),y,x-dp(1),y+dp(5),p);
            c.drawLine(x-dp(1),y+dp(5),x+dp(8),y-dp(7),p);
            p.setStyle(Paint.Style.FILL);
        }
    }
    private void drawSlider(Canvas c,float x0,float x1,float y,float value){
        rounded(c,x0,y-dp(4),x1,y+dp(4),4,0xFF547082);
        rounded(c,x0,y-dp(4),x0+(x1-x0)*value,y+dp(4),4,0xFFF4BC77);
        ink(0xFFFFF4DD);c.drawCircle(x0+(x1-x0)*value,y,dp(13),p);
    }
    private void applySlider(float x,float w,int which){
        float t=Math.max(0f,Math.min(1f,(x-w*.31f)/(w*.38f)));
        if(which==0)game.setMusicVolume(t);
        else if(which==1)game.setRenderDistance(new int[]{180,260,340,420}[Math.min(3,Math.round(t*3f))]);
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
                if(within(x,y,w/2,h*.35f+dp(25),dp(140),dp(32))) game.resumeGame();
                else if(within(x,y,w/2,h*.52f+dp(25),dp(140),dp(32))) game.openSettings();
                else if(within(x,y,w/2,h*.69f+dp(23),dp(140),dp(30))) game.mainMenu();
            } else if(game.state==3){
                if(within(x,y,w*.5f,h*.90f,w*.11f,h*.047f))game.closeSettings();
                else if(within(x,y,w*.43f,h*.345f,w*.18f,h*.056f))game.setMusicOn(!game.musicOn);
                else if(within(x,y,w*.43f,h*.785f,w*.18f,h*.056f))game.setCloudsOn(!game.cloudsOn);
                else if(Math.abs(y-h*.54f)<h*.050f){sliderId=id;sliderWhich=0;applySlider(x,w,0);}
                else if(Math.abs(y-h*.70f)<h*.050f){sliderId=id;sliderWhich=1;applySlider(x,w,1);}
            } else if(inventoryOpen && game.state==1){
                float left=w*.5f-dp(202),top=h*.5f-dp(115),cell=dp(82);
                if(within(x,y,w-dp(41),dp(42),dp(41),dp(41))){inventoryOpen=false;}
                else {
                    int col=(int)((x-left)/cell),row=(int)((y-top)/cell),slot=row*4+col;
                    if(x>=left&&y>=top&&col>=0&&col<4&&row>=0&&row<2&&slot<7){
                        game.selectedSlot=slot;inventoryOpen=false;
                        game.renderer.swordAction(false);
                    }
                }
            } else if(game.state==1 && within(x,y,w-dp(41),dp(42),dp(41),dp(41))){
                inventoryOpen=true;game.renderer.move(0,0);
                stickX=stickY=0;moveId=lookId=jumpId=actionId=-1;
                game.renderer.swordAction(false);
            } else if(game.state==1 && game.selectedSlot==0 &&
                      within(x,y,w-dp(103),h-dp(216),dp(44),dp(45))){
                actionId=id;game.renderer.swordAction(true);
            } else if(within(x,y,w/2,dp(28),dp(35),dp(29))) {
                game.pause();
            } else if(x < w*.35f && y>h*.38f && moveId<0) {
                moveId=id; updateStick(x,y,h);
            } else if(x>w*.70f && y>h*.53f && jumpId<0) {
                jumpId=id; game.renderer.jump();
            } else if(lookId<0) {
                lookId=id; lastLookX=x;lastLookY=y;
            }
        } else if(action==MotionEvent.ACTION_MOVE && game.state==3){
            if(sliderId>=0){int i=e.findPointerIndex(sliderId);
                if(i>=0)applySlider(e.getX(i),w,sliderWhich);}
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
            if(actionId==id || action==MotionEvent.ACTION_CANCEL){
                actionId=-1;game.renderer.swordAction(false);
            }
            if(sliderId==id || action==MotionEvent.ACTION_CANCEL){sliderId=-1;sliderWhich=-1;}
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
