package com.solpixel.game;

import android.opengl.GLES30;
import android.opengl.Matrix;
import android.util.Log;

/**
 * Mapa de sombra direcional real das arvores GLB proximas.
 * Atualiza ao caminhar / trocar de chunk; limitado a 128 casters e 1024x1024.
 * Fallback: o renderizador continua sem sombras mapeadas em GPUs sem suporte.
 */
final class ForestShadows {
    private static final int SIZE=1024;
    private final int program, framebuffer, depthTexture;
    final float[] lightVP=new float[16];
    private final float[] lightView=new float[16],lightProj=new float[16];
    private float lastX=Float.MAX_VALUE,lastZ=Float.MAX_VALUE,lastHeight=Float.MAX_VALUE;
    private World.Decoration lastDecoration;
    private int frames=0;
    private float lastRange=-1f;
    private boolean valid=false;

    ForestShadows(int depthProgram){
        program=depthProgram;
        int[] ids=new int[1];
        GLES30.glGenTextures(1,ids,0);depthTexture=ids[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,depthTexture);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_DEPTH_COMPONENT16,
                SIZE,SIZE,0,GLES30.GL_DEPTH_COMPONENT,GLES30.GL_UNSIGNED_SHORT,null);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glGenFramebuffers(1,ids,0);framebuffer=ids[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_DEPTH_ATTACHMENT,
                GLES30.GL_TEXTURE_2D,depthTexture,0);
        GLES30.glDrawBuffers(1,new int[]{GLES30.GL_NONE},0);
        GLES30.glReadBuffer(GLES30.GL_NONE);
        int status=GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
        if(status!=GLES30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Shadow framebuffer incompleto: "+Integer.toHexString(status));
    }
    int texture(){return depthTexture;}
    boolean ready(){return valid;}

    void update(float x,float y,float z,float[] sun,World.Decoration decoration,
                TreeModel ordinary,TreeModel pine,float requestedRange,boolean enabled){
        if(!enabled){valid=false;return;}
        if(decoration==null || ordinary==null || pine==null)return;
        float range=Math.max(10f,Math.min(1000f,requestedRange));
        frames++;
        // Desacopla o custo do mapa do FPS; nao depende do giro da camera.
        if(valid && decoration==lastDecoration && frames<12 &&
           Math.abs(x-lastX)<3.2f && Math.abs(z-lastZ)<3.2f &&
           Math.abs(y-lastHeight)<2.5f && Math.abs(range-lastRange)<1f)return;
        frames=0;
        float cx=Math.round(x/2f)*2f,cz=Math.round(z/2f)*2f;
        float centerY=y+20f;
        float length=(float)Math.sqrt(sun[0]*sun[0]+sun[1]*sun[1]+sun[2]*sun[2]);
        float nx=sun[0]/length,ny=sun[1]/length,nz=sun[2]/length;
        float cameraDistance=Math.max(220f,range*1.8f);
        Matrix.setLookAtM(lightView,0,cx+nx*cameraDistance,centerY+ny*cameraDistance,cz+nz*cameraDistance,
                           cx,centerY,cz,0f,1f,0f);
        Matrix.orthoM(lightProj,0,-range,range,-range,range,8f,
                      cameraDistance*2.6f+range);
        Matrix.multiplyMM(lightVP,0,lightProj,0,lightView,0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer);
        GLES30.glViewport(0,0,SIZE,SIZE);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glColorMask(false,false,false,false);
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL);
        GLES30.glPolygonOffset(1.8f,3.0f);
        GLES30.glUseProgram(program);
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program,"uLightVP"),1,false,lightVP,0);
        try {
            // 64 arvores reais de cada especie, incluindo transparencias da copa.
            int casters=Math.min(96,Math.max(32,Math.round(32f+64f*range/1000f)));
            ordinary.drawShadowCasters(program,decoration.broadleafInstances,cx,cz,range,casters);
            pine.drawShadowCasters(program,decoration.pineInstances,cx,cz,range,casters);
            valid=true;lastDecoration=decoration;
            lastX=x;lastZ=z;lastHeight=y;lastRange=range;
        } finally {
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL);
            GLES30.glColorMask(true,true,true,true);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
        }
    }
}
