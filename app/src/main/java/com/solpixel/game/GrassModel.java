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

/** SolPixel v0.9: 6 tufos alpha cutout; dois planos cruzados por instancia, uma textura-atlas. */
final class GrassModel {
    private final int vao,vbo,ibo,instanceBuffer,atlas;
    private float[] uploaded;

    GrassModel(Context context) throws Exception {
        int[] t=new int[1];
        GLES30.glGenTextures(1,t,0);
        atlas=t[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,atlas);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST_MIPMAP_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
        try(InputStream stream=context.getAssets().open("grass_atlas.png")){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
            Bitmap b=BitmapFactory.decodeStream(stream,null,options);
            if(b==null)throw new IllegalStateException("Falha ao decodificar grass_atlas.png");
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,b,0);
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
            b.recycle();
        }
        // O primeiro quad esta no plano XY, e o segundo no plano YZ (90 graus).
        float[] v={
            -.5f,0,0,  0,1,  .5f,0,0,  1,1,  .5f,1,0, 1,0,  -.5f,1,0, 0,0,
            0,0,-.5f,  0,1,  0,0,.5f, 1,1, 0,1,.5f, 1,0,   0,1,-.5f,0,0
        };
        short[] ids={0,1,2,0,2,3,4,5,6,4,6,7};
        FloatBuffer vertices=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertices.put(v).position(0);
        ShortBuffer indices=ByteBuffer.allocateDirect(ids.length*2).order(ByteOrder.nativeOrder()).asShortBuffer();
        indices.put(ids).position(0);
        int[] a=new int[1],b=new int[3];
        GLES30.glGenVertexArrays(1,a,0);GLES30.glGenBuffers(3,b,0);
        vao=a[0];vbo=b[0];ibo=b[1];instanceBuffer=b[2];
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,v.length*4,vertices,GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,ibo);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,ids.length*2,indices,GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,20,0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1,2,GLES30.GL_FLOAT,false,20,12);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,instanceBuffer);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glVertexAttribPointer(2,4,GLES30.GL_FLOAT,false,32,0);
        GLES30.glEnableVertexAttribArray(3);
        GLES30.glVertexAttribPointer(3,4,GLES30.GL_FLOAT,false,32,16);
        GLES30.glVertexAttribDivisor(2,1);GLES30.glVertexAttribDivisor(3,1);
        GLES30.glBindVertexArray(0);
    }

    void draw(int program,float[] instances,float[] tint,float limit){
        if(instances==null||instances.length==0)return;
        if(instances!=uploaded){
            FloatBuffer data=ByteBuffer.allocateDirect(instances.length*4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
            data.put(instances).position(0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,instanceBuffer);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,instances.length*4,data,GLES30.GL_STATIC_DRAW);
            uploaded=instances;
        }
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uGrassLimit"),limit);
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program,"uGrassTint"),1,tint,0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE6);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,atlas);
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"uGrassAtlas"),6);
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES,12,GLES30.GL_UNSIGNED_SHORT,0,instances.length/8);
        GLES30.glBindVertexArray(0);
    }
}
