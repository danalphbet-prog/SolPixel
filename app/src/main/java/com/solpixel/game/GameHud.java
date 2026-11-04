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
import android.os.SystemClock;
import android.view.View;

public final class GameHud extends View {
    private final GameActivity game;
    private final Paint p = new Paint(3);
    private final Bitmap backpack,actionIcon,slotSword;
    private boolean inventoryOpen=false;
    private int pendingSlot=-1;
    private long bagPressUntil=0L;
    private long actionPressAt=0L,lastPickupTapAt=-10000L;
    private boolean doublePickupHold=false;
    private int interactionKind=0; // 1 collect, 2 drop
    private int actionId=-1;
    private static final int[] SLOT_BORDER={0xFFF8E6C0,0xFFA9B6AF};
    private int moveId = -1, lookId = -1, jumpId = -1;
    private int sliderId=-1;
    private int sliderWhich=-1;
    private int settingsTab=0; // 0 geral, 1 sombras, 2 LODs, 3 distancias
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
        inventoryOpen=false;pendingSlot=-1;actionId=-1;interactionKind=0;game.renderer.swordAction(false);
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
    private float inventoryCell(float w){return Math.min(dp(72), (w-dp(46))/5f);}
    private float inventoryLeft(float w,float cell){return w*.5f-2.5f*cell;}
    private float inventoryTop(float h,float cell){return Math.max(dp(48),h*.48f-1.4f*cell);}
    private float handX(float w,float cell,int hand){return w*.5f+(hand==0?-.63f:.63f)*cell;}
    private void drawItem(Canvas c,int item,float cx,float cy,float size){
        if(item==InventorySlots.SWORD){
            if(slotSword!=null)drawSprite(c,slotSword,cx,cy,size);
            else text(c,"ESPADA",cx,cy+dp(5),9,0xFFF2E1B3,true);
        }else if(item==InventorySlots.LOG){
            float k=size/48f;
            rounded(c,cx-15*k,cy-10*k,cx+15*k,cy+10*k,3,0xFF8D593C);
            rounded(c,cx+7*k,cy-10*k,cx+16*k,cy+10*k,4,0xFFDAB083);
            rounded(c,cx+10*k,cy-6*k,cx+14*k,cy+6*k,3,0xFF986E49);
        }else if(item==InventorySlots.TORCH){
            // Icone de tocha em pixel-art sem depender de um modelo 3D inexistente.
            float k=size/48f;
            rounded(c,cx-3*k,cy-4*k,cx+3*k,cy+21*k,1,0xFF8F5939);
            rounded(c,cx-7*k,cy-17*k,cx+7*k,cy-3*k,2,0xFFFFA137);
            rounded(c,cx-4*k,cy-22*k,cx+4*k,cy-8*k,2,0xFFFFE6A0);
        }
    }
    private void drawStack(Canvas c,int index,float cx,float cy,float size){
        int count=game.stackAt(index);
        if(count>1)text(c,String.valueOf(count),cx+size*.31f,cy+size*.34f,12,0xFFFFEEC5,true);
    }
    private void drawBackpackButton(Canvas c,float x,float y){
        boolean pressed=SystemClock.uptimeMillis()<bagPressUntil;
        float scale=pressed?1.10f:1f;
        rounded(c,x-dp(34)*scale,y-dp(33)*scale,x+dp(34)*scale,y+dp(33)*scale,8,
                pressed?0xFFF5DBA1:(inventoryOpen?0xFFDAAC72:0xC1142330));
        ring(c,x,y,dp(33)*scale,pressed?0xFF182D3E:0xB3EAD8B6,1.5f);
        if(backpack!=null)drawSprite(c,backpack,x,y,dp(52)*scale);
        else text(c,"BAG",x,y+dp(5),15,pressed?0xFF182D3E:0xFFFFFFFF,true);
        if(pressed)postInvalidateDelayed(45);
    }
    private int inventoryHit(float x,float y,float w,float h){
        float cell=inventoryCell(w),left=inventoryLeft(w,cell),top=inventoryTop(h,cell);
        for(int i=0;i<InventorySlots.BAG_COUNT;i++){
            float cx=left+(i%5+.5f)*cell,cy=top+(i/5+.5f)*cell;
            if(within(x,y,cx,cy,cell*.44f,cell*.44f))return i;
        }
        for(int hand=0;hand<2;hand++){
            float cx=handX(w,cell,hand),cy=top+2.83f*cell;
            if(within(x,y,cx,cy,cell*.44f,cell*.44f))return 10+hand;
        }
        return -1;
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
            ink(0xDF0B1825);c.drawRect(0,0,w,h,p);
            rounded(c,w*.16f,h*.055f,w*.84f,h*.96f,13,0xFA1B344A);
            text(c,"CONFIGURAÇÕES",w*.5f,h*.16f,23,0xFFF7E8CE,true);
            String[] tabs={"GERAL","SOMBRAS","LODs","DISTÂNCIA"};
            for(int i=0;i<4;i++){
                float tx=w*(.245f+.17f*i);
                rounded(c,tx-w*.077f,h*.215f,tx+w*.077f,h*.292f,6,
                        settingsTab==i?0xFFF2BD7D:0xFF405568);
                text(c,tabs[i],tx,h*.269f,12,
                        settingsTab==i?0xFF193344:0xFFFFFFFF,true);
            }
            if(settingsTab==0){
                drawCheck(c,w*.31f,h*.39f,game.musicOn);
                text(c,"Música ambiente",w*.53f,h*.406f,15,0xFFEAF0F3,false);
                text(c,"VOLUME  "+Math.round(game.musicVolume*100)+"%",w*.5f,h*.51f,13,0xFFF9E3BE,true);
                drawSlider(c,w*.28f,w*.72f,h*.57f,game.musicVolume);
                text(c,"TERRENO PRÓXIMO  "+game.renderDistance+" m",w*.5f,h*.66f,13,0xFFF9E3BE,true);
                drawSlider(c,w*.28f,w*.72f,h*.72f,(game.renderDistance-180f)/460f);
                drawCheck(c,w*.31f,h*.81f,game.cloudsOn);
                text(c,"Nuvens 3D",w*.53f,h*.827f,15,0xFFEAF0F3,false);
            }else if(settingsTab==1){
                text(c,"PERFIL DE QUALIDADE",w*.5f,h*.37f,12,0xFFF9E3BE,true);
                String[] presets={"LEVE","DESEMPENHO","GRÁFICO"};
                for(int i=0;i<3;i++){
                    float px=w*(.31f+.19f*i);
                    rounded(c,px-w*.091f,h*.407f,px+w*.091f,h*.485f,6,
                            game.graphicsPreset==i?0xFFF2BD7D:0xFF405568);
                    text(c,presets[i],px,h*.463f,11,
                            game.graphicsPreset==i?0xFF193344:0xFFFFFFFF,true);
                }
                drawCheck(c,w*.29f,h*.56f,game.forestShadowOn);
                text(c,"Sombras reais de árvores",w*.53f,h*.575f,13,0xFFEAF0F3,false);
                text(c,"ALCANCE  "+game.forestShadowDistance+" m",w*.5f,h*.675f,
                     14,0xFFF9E3BE,true);
                drawSlider(c,w*.27f,w*.73f,h*.735f,
                           (game.forestShadowDistance-10f)/990f);
                text(c,"Cascata próxima nítida + sombras distantes",
                     w*.5f,h*.82f,10,0xFFBBCBD4,false);
            }else if(settingsTab==2){
                text(c,"DISTÂNCIA DE TRANSIÇÃO DE CADA LOD",w*.5f,h*.345f,
                     12,0xFFF9E3BE,true);
                int[] levels={20,50,80,90,95,99};
                int[] ds=game.lodDistances;
                for(int i=0;i<6;i++){
                    float y=h*(.41f+.079f*i);
                    text(c,"LOD "+levels[i]+"%  após "+ds[i]+" m",
                         w*.5f,y,11,0xFFEAF0F3,false);
                    drawSlider(c,w*.28f,w*.72f,y+h*.026f,
                               lodValueToSlider(ds[i]));
                }
            }else{
                text(c,"HORIZONTE VISÍVEL  "+game.viewDistance+" m",w*.5f,h*.385f,13,0xFFF9E3BE,true);
                drawSlider(c,w*.28f,w*.72f,h*.45f,(game.viewDistance-1000f)/3000f);
                text(c,"RELEVO REFINADO  "+game.terrainDetailRadius+" m",w*.5f,h*.575f,13,0xFFF9E3BE,true);
                drawSlider(c,w*.28f,w*.72f,h*.64f,(game.terrainDetailRadius-40f)/260f);
                text(c,"Terreno próximo: "+game.renderDistance+" m",w*.5f,h*.701f,11,0xFFEAF0F3,false);
                drawCheck(c,w*.30f,h*.819f,game.softPixelOn);
                text(c,"Pixelização sutil (1,35 px)",w*.555f,h*.832f,11,0xFFBBCBD4,false);
                drawCheck(c,w*.30f,h*.765f,game.filmGrainOn);
                text(c,"Film grain na tela inteira",w*.555f,h*.778f,11,0xFFBBCBD4,false);
            }
            rounded(c,w*.435f,h*.873f,w*.565f,h*.943f,8,0xFFF2BD7D);
            text(c,"VOLTAR",w*.5f,h*.924f,12,0xFF193344,true);
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
        // Dois slots de maos sempre visiveis, com selecao visual independente.
        float sz=dp(55),hotY=h-dp(43);
        for(int hand=0;hand<2;hand++){
            float hx=handX(w,sz,hand);
            rounded(c,hx-sz*.49f,hotY-sz*.49f,hx+sz*.49f,hotY+sz*.49f,7,
                    game.selectedSlot==hand?0xE45B4D38:0xC3182435);
            ring(c,hx,hotY,sz*.49f,game.selectedSlot==hand?0xFFFFD88B:0x8897B4BD,1.6f);
            drawItem(c,game.itemAt(10+hand),hx,hotY,dp(43));
            drawStack(c,10+hand,hx,hotY,dp(43));
            text(c,hand==0?"E":"D",hx+sz*.30f,hotY-sz*.27f,9,0xFFEEE3CD,true);
        }
        float bagX=w-dp(41),bagY=dp(42);
        drawBackpackButton(c,bagX,bagY);
        {
            float ax=w-dp(103),ay=h-dp(216);
            boolean glowing=game.renderer.swordActive();
            rounded(c,ax-dp(40),ay-dp(40),ax+dp(40),ay+dp(40),10,
                    glowing?0xDA555C69:0xB9142330);
            if(glowing) ring(c,ax,ay,dp(42),0xFFFFFFFF,2.5f);
            if(game.hasSwordEquipped() && actionIcon!=null)drawSprite(c,actionIcon,ax,ay,dp(64));
            else text(c,game.logsInHand()?"SOLTAR":game.emptyHand()?"PEGAR":"USAR",ax,ay+dp(5),13,0xFFFFFFFF,true);
        }
        if(inventoryOpen){
            ink(0xD10B111C);c.drawRect(0,0,w,h,p);
            float cell=inventoryCell(w),left=inventoryLeft(w,cell),top=inventoryTop(h,cell);
            rounded(c,left-dp(14),top-cell*.63f,left+5*cell+dp(14),top+3.58f*cell,12,0xF0202938);
            text(c,"MOCHILA  10 SLOTS",w*.5f,top-cell*.27f,16,0xFFF4EDDC,true);
            for(int i=0;i<InventorySlots.BAG_COUNT;i++){
                float cx=left+(i%5+.5f)*cell,cy=top+(i/5+.5f)*cell;
                boolean selected=i==pendingSlot;
                rounded(c,cx-cell*.43f,cy-cell*.43f,cx+cell*.43f,cy+cell*.43f,6,
                        selected?0xFFF5CD78:0xDD111D2B);
                ring(c,cx,cy,cell*.42f,selected?0xFFFFF1B5:0x889CB2BC,1.2f);
                drawItem(c,game.itemAt(i),cx,cy,cell*.69f);
                drawStack(c,i,cx,cy,cell*.69f);
            }
            text(c,"MÃOS",w*.5f,top+2.20f*cell,12,0xFFE5D5B4,true);
            for(int hand=0;hand<2;hand++){
                int slot=10+hand;
                float hx=handX(w,cell,hand),hy=top+2.83f*cell;
                boolean selected=slot==pendingSlot;
                rounded(c,hx-cell*.43f,hy-cell*.43f,hx+cell*.43f,hy+cell*.43f,6,
                        selected?0xFFF5CD78:0xDD2A3843);
                ring(c,hx,hy,cell*.42f,selected?0xFFFFF1B5:0xFFA7B9C4,1.5f);
                drawItem(c,game.itemAt(slot),hx,hy,cell*.72f);
                drawStack(c,slot,hx,hy,cell*.72f);
                text(c,hand==0?"ESQUERDA":"DIREITA",hx,hy+cell*.62f,9,0xFFD8E3E8,true);
            }
            text(c,pendingSlot<0?"SELECIONE UM ITEM PARA MOVER":
                    "SELECIONE O DESTINO OU TOQUE NOVAMENTE",w*.5f,top+3.45f*cell,
                    9,0xFFF4DFC1,false);
            drawBackpackButton(c,bagX,bagY); // permanece acessivel por cima da janela
        }
        if(game.renderer.swordActive())postInvalidateDelayed(120);
        // Acerto confirmado pelo renderizador: facilita conferir os seis golpes.
        int hits=game.renderer.pineHitFeedback();
        if(hits>0){
            rounded(c,w*.5f-dp(96),h*.5f-dp(82),
                w*.5f+dp(96),h*.5f-dp(47),7,0xCB192A30);
            text(c,hits>=6?"PINHEIRO DERRUBADO":"PINHEIRO  "+hits+"/6",
                w*.5f,h*.5f-dp(58),14,0xFFFFD998,true);
            postInvalidateDelayed(90);
        }
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
    private float lodValueToSlider(int value){
        return (float)(Math.log(Math.max(10,value)/10.0)/Math.log(400.0));
    }
    private void applySlider(float x,float w,int which){
        float x0=(which==2?w*.27f:w*.28f);
        float x1=(which==2?w*.73f:w*.72f);
        float t=Math.max(0f,Math.min(1f,(x-x0)/(x1-x0)));
        if(which==0)game.setMusicVolume(t);
        else if(which==1)game.setRenderDistance(new int[]{180,260,340,420,520,640}[Math.min(5,Math.round(t*5f))]);
        else if(which==2)game.setForestShadowDistance(10+Math.round(t*990f));
        else if(which>=3&&which<=8){
            int distance=Math.round((float)(10.0*Math.pow(400.0,t)));
            game.setLodDistance(which-3,distance);
        }else if(which==9)game.setViewDistance(1000+Math.round(t*3000f));
        else if(which==10)game.setTerrainDetailRadius(40+Math.round(t*260f));
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
                if(within(x,y,w*.5f,h*.908f,w*.08f,h*.044f))game.closeSettings();
                else if(y>=h*.21f&&y<=h*.30f&&x>=w*.20f&&x<=w*.80f){
                    int tab=Math.min(3,Math.max(0,(int)((x/w-.16f)/.17f)));
                    settingsTab=tab;sliderId=-1;sliderWhich=-1;
                }else if(settingsTab==0){
                    if(within(x,y,w*.43f,h*.39f,w*.18f,h*.045f))game.setMusicOn(!game.musicOn);
                    else if(within(x,y,w*.43f,h*.81f,w*.18f,h*.047f))game.setCloudsOn(!game.cloudsOn);
                    else if(Math.abs(y-h*.57f)<h*.043f){sliderId=id;sliderWhich=0;applySlider(x,w,0);}
                    else if(Math.abs(y-h*.72f)<h*.043f){sliderId=id;sliderWhich=1;applySlider(x,w,1);}
                }else if(settingsTab==1){
                    if(y>=h*.401f&&y<=h*.491f){
                        float dx=x/w;
                        if(dx>=.20f&&dx<.405f)game.applyGraphicsPreset(0);
                        else if(dx>=.405f&&dx<.60f)game.applyGraphicsPreset(1);
                        else if(dx>=.60f&&dx<.80f)game.applyGraphicsPreset(2);
                    }else if(within(x,y,w*.43f,h*.56f,w*.20f,h*.048f))game.setForestShadowOn(!game.forestShadowOn);
                    else if(Math.abs(y-h*.735f)<h*.042f){sliderId=id;sliderWhich=2;applySlider(x,w,2);}
                }else if(settingsTab==2){
                    for(int k=0;k<6;k++){
                        float row=h*(.41f+.079f*k)+h*.026f;
                        if(Math.abs(y-row)<h*.037f){sliderId=id;sliderWhich=3+k;applySlider(x,w,3+k);break;}
                    }
                }else{
                    if(Math.abs(y-h*.45f)<h*.045f){sliderId=id;sliderWhich=9;applySlider(x,w,9);}
                    else if(Math.abs(y-h*.64f)<h*.045f){sliderId=id;sliderWhich=10;applySlider(x,w,10);}
                    else if(within(x,y,w*.435f,h*.819f,w*.22f,h*.034f))game.setSoftPixelOn(!game.softPixelOn);
                    else if(within(x,y,w*.435f,h*.765f,w*.22f,h*.030f))game.setFilmGrainOn(!game.filmGrainOn);
                }
            } else if(inventoryOpen && game.state==1){
                if(within(x,y,w-dp(41),dp(42),dp(41),dp(41))){
                    inventoryOpen=false;pendingSlot=-1;
                    bagPressUntil=SystemClock.uptimeMillis()+170L;
                }else{
                    int slot=inventoryHit(x,y,w,h);
                    if(slot>=0){
                        if(pendingSlot<0){
                            if(game.itemAt(slot)!=InventorySlots.EMPTY)pendingSlot=slot;
                        }else if(pendingSlot==slot){pendingSlot=-1;}
                        else{
                            game.swapInventorySlots(pendingSlot,slot);
                            pendingSlot=-1;
                        }
                    }
                }
            } else if(game.state==1 && within(x,y,w-dp(41),dp(42),dp(41),dp(41))){
                inventoryOpen=true;pendingSlot=-1;
                bagPressUntil=SystemClock.uptimeMillis()+170L;
                game.renderer.move(0,0);
                stickX=stickY=0;moveId=lookId=jumpId=actionId=-1;
                game.renderer.swordAction(false);
            } else if(game.state==1 &&
                      within(x,y,w-dp(103),h-dp(216),dp(44),dp(45))){
                actionId=id;
                interactionKind=0;
                if(game.hasSwordEquipped()){
                    game.renderer.swordAction(true);game.playSwordSound();
                }else if(game.emptyHand()||game.logsInHand()){
                    interactionKind=game.emptyHand()?1:2;
                    actionPressAt=SystemClock.uptimeMillis();
                    doublePickupHold=interactionKind==1 &&
                        actionPressAt-lastPickupTapAt<=360L;
                }
            } else if(game.state==1 &&
                      (within(x,y,handX(w,dp(55),0),h-dp(43),dp(29),dp(29)) ||
                       within(x,y,handX(w,dp(55),1),h-dp(43),dp(29),dp(29)))){
                game.selectedSlot=within(x,y,handX(w,dp(55),0),h-dp(43),dp(29),dp(29))?0:1;
                game.renderer.swordAction(false);
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
                if(actionId==id && action!=MotionEvent.ACTION_CANCEL && interactionKind!=0){
                    long now=SystemClock.uptimeMillis();
                    long elapsed=now-actionPressAt;
                    if(interactionKind==1){
                        boolean all=doublePickupHold&&elapsed>=420L;
                        game.renderer.inventoryInteract(all,false);
                        lastPickupTapAt=all?-10000L:now;
                    }else if(interactionKind==2){
                        game.renderer.inventoryInteract(elapsed>=450L,true);
                        lastPickupTapAt=-10000L;
                    }
                }
                interactionKind=0;actionId=-1;game.renderer.swordAction(false);
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
