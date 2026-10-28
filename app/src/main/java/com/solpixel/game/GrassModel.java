package com.solpixel.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Vegetacao PS1 v0.11: 10 sprites originais, um atlas 4x4 com gutter de 8px.
 * Flores usam um unico plano; as outras 7 especies usam planos cruzados.
 * Dois buffers de instancias persistentes: uploads apenas na troca de chunk.
 * A distribuicao nao acontece na GL thread, mas em World.decorate().
 */
final class GrassModel {
    private final int vao, vbo, ibo, crossBuffer, flowerBuffer, atlas;
    private float[] uploaded;
    private float selectionX=Float.MAX_VALUE,selectionZ=Float.MAX_VALUE;
    private int crossCount, flowerCount;

    GrassModel(Context context) throws Exception {
        int[] tex={0};
        GLES30.glGenTextures(1,tex,0);
        atlas=tex[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,atlas);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST_MIPMAP_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
        try(InputStream input=context.getAssets().open("vegetation_atlas.png")){
            BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;
            Bitmap b=BitmapFactory.decodeStream(input,null,o);
            if(b==null)throw new IllegalStateException("Falha em vegetation_atlas.png");
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,b,0);
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
            b.recycle();
        }
        // XY + YZ: frente/verso sem culling (desabilitado no renderer).
        float[] quad={
            -.5f,0,0,0,1,   .5f,0,0,1,1,   .5f,1,0,1,0,   -.5f,1,0,0,0,
            0,0,-.5f,0,1,   0,0,.5f,1,1,   0,1,.5f,1,0,   0,1,-.5f,0,0
        };
        short[] ids={0,1,2,0,2,3, 4,5,6,4,6,7};
        FloatBuffer verts=ByteBuffer.allocateDirect(quad.length*4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        verts.put(quad).position(0);
        ShortBuffer indices=ByteBuffer.allocateDirect(ids.length*2)
            .order(ByteOrder.nativeOrder()).asShortBuffer();
        indices.put(ids).position(0);
        int[] a=new int[1],bs=new int[4];
        GLES30.glGenVertexArrays(1,a,0);GLES30.glGenBuffers(4,bs,0);
        vao=a[0];vbo=bs[0];ibo=bs[1];crossBuffer=bs[2];flowerBuffer=bs[3];
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,quad.length*4,verts,GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,ibo);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,ids.length*2,indices,GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,20,0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1,2,GLES30.GL_FLOAT,false,20,12);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glEnableVertexAttribArray(3);
        GLES30.glVertexAttribDivisor(2,1);
        GLES30.glVertexAttribDivisor(3,1);
        GLES30.glBindVertexArray(0);
    }

    private static void upload(int buffer,float[] data){
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,buffer);
        FloatBuffer f=ByteBuffer.allocateDirect(data.length*4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        f.put(data).position(0);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,data.length*4,f,GLES30.GL_STATIC_DRAW);
    }

    private void prepare(float[] source,float px,float pz,float limit){
        // Apenas plantas ate (limite+8)m participam do desenho e do vento.
        if(uploaded==source && Math.abs(px-selectionX)<3.5f &&
           Math.abs(pz-selectionZ)<3.5f)return;
        float limit2=(limit+8f)*(limit+8f);
        int crosses=0,flowers=0;
        for(int i=0;i<source.length;i+=8){
            float dx=source[i]-px,dz=source[i+2]-pz;
            if(dx*dx+dz*dz>limit2)continue;
            int type=Math.round(source[i+6]);
            if(type>=3&&type<=5)flowers++;else crosses++;
        }
        float[] cross=new float[crosses*8],flower=new float[flowers*8];
        int a=0,b=0;
        for(int i=0;i<source.length;i+=8){
            float dx=source[i]-px,dz=source[i+2]-pz;
            if(dx*dx+dz*dz>limit2)continue;
            int type=Math.round(source[i+6]);
            if(type>=3&&type<=5){System.arraycopy(source,i,flower,b,8);b+=8;}
            else {System.arraycopy(source,i,cross,a,8);a+=8;}
        }
        upload(crossBuffer,cross);upload(flowerBuffer,flower);
        crossCount=crosses;flowerCount=flowers;
        uploaded=source;selectionX=px;selectionZ=pz;
    }

    private static void instanceBuffer(int buffer){
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,buffer);
        GLES30.glVertexAttribPointer(2,4,GLES30.GL_FLOAT,false,32,0);
        GLES30.glVertexAttribPointer(3,4,GLES30.GL_FLOAT,false,32,16);
        GLES30.glVertexAttribDivisor(2,1);GLES30.glVertexAttribDivisor(3,1);
    }

    void draw(int program,float[] instances,float[] tint,float limit,
              float time,float px,float pz,float vx,float vz){
        if(instances==null||instances.length==0)return;
        prepare(instances,px,pz,limit);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uWindTime"),time);
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program,"uPlayerXZ"),px,pz);
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program,"uPlayerVelocity"),vx,vz);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uGrassLimit"),limit);
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program,"uGrassTint"),1,tint,0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE6);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,atlas);
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"uGrassAtlas"),6);
        GLES30.glBindVertexArray(vao);
        if(crossCount>0){
            instanceBuffer(crossBuffer);
            GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES,12,GLES30.GL_UNSIGNED_SHORT,0,crossCount);
        }
        if(flowerCount>0){
            instanceBuffer(flowerBuffer);
            GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES,6,GLES30.GL_UNSIGNED_SHORT,0,flowerCount);
        }
        GLES30.glBindVertexArray(0);
    }
}
