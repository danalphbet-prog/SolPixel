package com.solpixel.game;

import android.opengl.GLES30;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Solas v0.3: pontos luminosos locais, apenas com eclipse e jogador na floresta. */
final class FireflyModel {
    private final int vao,vbo;
    private float[] source;
    private int count;
    private float lastX=Float.POSITIVE_INFINITY,lastZ=Float.POSITIVE_INFINITY;
    FireflyModel(){
        int[] a=new int[1],b=new int[1];
        GLES30.glGenVertexArrays(1,a,0);GLES30.glGenBuffers(1,b,0);
        vao=a[0];vbo=b[0];
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0,4,GLES30.GL_FLOAT,false,16,0);
        GLES30.glBindVertexArray(0);
    }
    private void prepare(float[] positions,float px,float pz){
        if(source==positions && Math.abs(px-lastX)<6f && Math.abs(pz-lastZ)<6f)return;
        source=positions;lastX=px;lastZ=pz;
        if(positions==null){count=0;return;}
        int n=0;
        for(int i=0;i+3<positions.length;i+=4){
            float dx=positions[i]-px,dz=positions[i+2]-pz;
            if(dx*dx+dz*dz<43f*43f)n++;
        }
        FloatBuffer data=ByteBuffer.allocateDirect(n*16).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for(int i=0;i+3<positions.length;i+=4){
            float dx=positions[i]-px,dz=positions[i+2]-pz;
            if(dx*dx+dz*dz<43f*43f)data.put(positions,i,4);
        }
        data.flip();count=n;
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,n*16,data,GLES30.GL_DYNAMIC_DRAW);
    }
    void draw(int program,float[] instances,float[] vp,float px,float py,float pz,float time,float night){
        if(night<.10f||instances==null)return;
        prepare(instances,px,pz);
        if(count==0)return;
        GLES30.glUseProgram(program);
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program,"uMvp"),1,false,vp,0);
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program,"uEye"),px,py,pz);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uTime"),time);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uNight"),night);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE);
        GLES30.glDepthMask(false);
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawArrays(GLES30.GL_POINTS,0,count);
        GLES30.glBindVertexArray(0);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
    }
}
