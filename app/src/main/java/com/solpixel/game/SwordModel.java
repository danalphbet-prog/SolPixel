package com.solpixel.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** Espada GLB enviada pelo usuario. Renderer de arma FPS, sem engine extra. */
final class SwordModel {
    private int vao,vbo,ebo,texture,program,count;
    SwordModel(Context context,String vertexShader,String fragmentShader) throws Exception {
        byte[] bytes;
        try(InputStream in=context.getAssets().open("espada_lowpoly_ps1.glb");ByteArrayOutputStream buf=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)buf.write(b,0,n);bytes=buf.toByteArray();
        }
        ByteBuffer file=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if(file.getInt(0)!=0x46546c67||file.getInt(4)!=2)throw new IllegalArgumentException("Espada precisa ser GLB 2.0");
        int jsonLength=file.getInt(12),binStart=20+jsonLength+8;
        JSONObject root=new JSONObject(new String(bytes,20,jsonLength,"UTF-8"));
        JSONArray views=root.getJSONArray("bufferViews"),accessors=root.getJSONArray("accessors");
        JSONObject prim=root.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives").getJSONObject(0);
        JSONObject attrs=prim.getJSONObject("attributes");
        float[] pos=readFloat(file,binStart,views,accessors,attrs.getInt("POSITION"),3);
        float[] normals=readFloat(file,binStart,views,accessors,attrs.getInt("NORMAL"),3);
        float[] uv=readFloat(file,binStart,views,accessors,attrs.getInt("TEXCOORD_0"),2);
        int vCount=pos.length/3;
        FloatBuffer vertex=ByteBuffer.allocateDirect(vCount*32).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for(int i=0;i<vCount;i++)vertex.put(pos,i*3,3).put(normals,i*3,3).put(uv,i*2,2);
        vertex.position(0);
        JSONObject index=accessors.getJSONObject(prim.getInt("indices"));
        if(index.getInt("componentType")!=5123)throw new IllegalArgumentException("Sword indices must be UNSIGNED_SHORT");
        JSONObject indexView=views.getJSONObject(index.getInt("bufferView"));
        count=index.getInt("count");
        int offset=binStart+indexView.optInt("byteOffset",0)+index.optInt("byteOffset",0);
        ShortBuffer inds=ByteBuffer.allocateDirect(count*2).order(ByteOrder.nativeOrder()).asShortBuffer();
        for(int i=0;i<count;i++)inds.put(file.getShort(offset+i*2));inds.position(0);
        JSONObject image=root.getJSONArray("images").getJSONObject(0);
        JSONObject imageView=views.getJSONObject(image.getInt("bufferView"));
        int start=binStart+imageView.optInt("byteOffset",0),length=imageView.getInt("byteLength");
        Bitmap bmp=BitmapFactory.decodeByteArray(bytes,start,length);
        if(bmp==null)throw new IllegalArgumentException("Sword texture PNG missing");
        int[] a=new int[1],bs=new int[2];
        GLES30.glGenTextures(1,a,0);texture=a[0];GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST_MIPMAP_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_REPEAT);
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,bmp,0);GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);bmp.recycle();
        GLES30.glGenVertexArrays(1,a,0);vao=a[0];GLES30.glGenBuffers(2,bs,0);vbo=bs[0];ebo=bs[1];
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,vCount*32,vertex,GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,ebo);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,count*2,inds,GLES30.GL_STATIC_DRAW);
        for(int k=0;k<3;k++)GLES30.glEnableVertexAttribArray(k);
        GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,32,0);
        GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,32,12);
        GLES30.glVertexAttribPointer(2,2,GLES30.GL_FLOAT,false,32,24);
        GLES30.glBindVertexArray(0);
        program=compile(vertexShader,fragmentShader);
    }
    private static float[] readFloat(ByteBuffer b,int start,JSONArray views,JSONArray accessors,int id,int comps)throws Exception{
        JSONObject a=accessors.getJSONObject(id),v=views.getJSONObject(a.getInt("bufferView"));
        int n=a.getInt("count"),pos=start+v.optInt("byteOffset",0)+a.optInt("byteOffset",0),stride=v.optInt("byteStride",comps*4);
        float[] out=new float[n*comps];for(int i=0;i<n;i++)for(int k=0;k<comps;k++)out[i*comps+k]=b.getFloat(pos+i*stride+k*4);return out;
    }
    private static int compilePart(int type,String source){
        int shader=GLES30.glCreateShader(type);GLES30.glShaderSource(shader,source);GLES30.glCompileShader(shader);
        int[] ok=new int[1];GLES30.glGetShaderiv(shader,GLES30.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0)throw new IllegalStateException("Sword shader: "+GLES30.glGetShaderInfoLog(shader));return shader;
    }
    private static int compile(String v,String f){
        int a=compilePart(GLES30.GL_VERTEX_SHADER,v),b=compilePart(GLES30.GL_FRAGMENT_SHADER,f);
        int p=GLES30.glCreateProgram();GLES30.glAttachShader(p,a);GLES30.glAttachShader(p,b);GLES30.glLinkProgram(p);
        int[] ok=new int[1];GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,ok,0);
        GLES30.glDeleteShader(a);GLES30.glDeleteShader(b);
        if(ok[0]==0)throw new IllegalStateException("Sword link: "+GLES30.glGetProgramInfoLog(p));return p;
    }
    void draw(float[] projection,float glow,float time){
        GLES30.glUseProgram(program);
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program,"uProjection"),1,false,projection,0);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uGlow"),glow);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uTime"),time);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"uSwordTexture"),0);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);
        GLES30.glBindVertexArray(vao);GLES30.glDrawElements(GLES30.GL_TRIANGLES,count,GLES30.GL_UNSIGNED_SHORT,0);
        GLES30.glBindVertexArray(0);GLES30.glDepthMask(true);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }
}
