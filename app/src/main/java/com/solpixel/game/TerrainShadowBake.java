package com.solpixel.game;

import android.opengl.GLES30;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bake assíncrono de oclusão direcional das montanhas. Sem raymarch por frame. */
final class TerrainShadowBake {
    static final int SIZE=96;
    private int texture=0;
    private Data active;
    static final class Data {
        final float x,z,span;
        final byte[] pixels;
        Data(float x,float z,float span,byte[] pixels){
            this.x=x;this.z=z;this.span=span;this.pixels=pixels;
        }
    }
    static Data generate(float cx,float cz,float radius){
        // 96x96, extensão maior que o chunk local, alcança encostas além do jogador.
        float span=Math.max(560f,Math.min(1600f,radius*2.3f));
        float left=cx-span*.5f,top=cz-span*.5f;
        byte[] pixels=new byte[SIZE*SIZE];
        // Direção de referência do Solas. A intensidade do eclipse é aplicada no shader.
        final float sx=.53f/.48f,sz=-.47f/.48f;
        for(int j=0;j<SIZE;j++)for(int i=0;i<SIZE;i++){
            float px=left+(i+.5f)*span/SIZE;
            float pz=top+(j+.5f)*span/SIZE;
            float h=World.height(px,pz)+.5f;
            float occlusion=0f;
            for(int k=1;k<=28;k++){
                float d=k*22f;
                float mountain=World.height(px+sx*d,pz+sz*d);
                float clearance=mountain-(h+d);
                if(clearance>0f)occlusion=Math.max(occlusion,Math.min(1f,clearance/9f));
            }
            pixels[j*SIZE+i]=(byte)Math.round(255f*(1f-occlusion));
        }
        return new Data(left,top,span,pixels);
    }
    void upload(Data data){
        if(data==null)return;
        if(texture==0){
            int[] ids=new int[1];GLES30.glGenTextures(1,ids,0);texture=ids[0];
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT,1);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
        ByteBuffer buffer=ByteBuffer.allocateDirect(data.pixels.length).order(ByteOrder.nativeOrder());
        buffer.put(data.pixels).position(0);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_R8,SIZE,SIZE,0,
                            GLES30.GL_RED,GLES30.GL_UNSIGNED_BYTE,buffer);
        active=data;
    }
    void bind(int program){
        int enabled=GLES30.glGetUniformLocation(program,"uTerrainShadowEnabled");
        if(enabled<0)return;
        GLES30.glUniform1f(enabled,active!=null?1f:0f);
        if(active==null)return;
        GLES30.glActiveTexture(GLES30.GL_TEXTURE9);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"uTerrainShadowMap"),9);
        GLES30.glUniform4f(GLES30.glGetUniformLocation(program,"uTerrainShadowBounds"),
                           active.x,active.z,active.span,active.span);
    }
}
