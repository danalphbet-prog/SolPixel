package com.solpixel.game;

import android.content.Context;
import android.opengl.GLES30;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

/** Solas Rocks v1 - Renderizacao instanciada GLB com LOD 1280 / 320 / 80 triangulos. */
final class RockModel {
    private static final String[] IDS={"a","b","c","d","f"};
    private final Shape[][] shapes=new Shape[3][5];
    private final int[] instanceBuffers=new int[15];
    private final int[][] count=new int[3][5];
    private final float[][] batches=new float[15][];
    private float[] oldSource;
    private float oldX=Float.MAX_VALUE,oldZ=Float.MAX_VALUE,oldFx=10f,oldFz=10f,oldMax=-1f;
    private static final class Shape {int vao,vbo,vertices;}
    private static final class Floats {
        float[] data=new float[128];int n;
        void add8(float[] src,int o){if(n+8>data.length)data=Arrays.copyOf(data,data.length*2);
            System.arraycopy(src,o,data,n,8);n+=8;}
        float[] array(){return Arrays.copyOf(data,n);}
    }
    RockModel(Context context) throws Exception {
        GLES30.glGenBuffers(15,instanceBuffers,0);
        for(int level=0;level<3;level++)for(int family=0;family<5;family++){
            if(level==0 && family>=3)continue;
            String path="rock_models/"+(level==0?"high_":level==1?"low_":"far_")+IDS[family]+".glb";
            shapes[level][family]=read(context,path,level*5+family);
        }
    }
    private Shape read(Context context,String path,int slot) throws Exception {
        byte[] bytes;
        try(InputStream in=context.getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] chunk=new byte[32768];int k;while((k=in.read(chunk))!=-1)out.write(chunk,0,k);
            bytes=out.toByteArray();
        }
        ByteBuffer bb=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if(bb.getInt(0)!=0x46546c67 || bb.getInt(4)!=2)throw new IllegalArgumentException("GLB invalido: "+path);
        int jsonSize=bb.getInt(12),binHdr=20+jsonSize;
        if(bb.getInt(binHdr+4)!=0x004e4942)throw new IllegalArgumentException("GLB sem BIN: "+path);
        int start=binHdr+8;
        JSONObject root=new JSONObject(new String(bytes,20,jsonSize,"UTF-8"));
        JSONArray views=root.getJSONArray("bufferViews"),accessors=root.getJSONArray("accessors");
        JSONObject attrs=root.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives").getJSONObject(0).getJSONObject("attributes");
        float[] positions=readVec3(bb,start,views,accessors,attrs.getInt("POSITION"));
        float[] normals=readVec3(bb,start,views,accessors,attrs.getInt("NORMAL"));
        if(positions.length!=normals.length)throw new IllegalArgumentException("Normais inconsistentes");
        int n=positions.length/3;
        FloatBuffer fb=ByteBuffer.allocateDirect(n*24).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for(int i=0;i<n;i++){
            fb.put(positions,i*3,3);fb.put(normals,i*3,3);
        }
        fb.position(0);
        Shape shape=new Shape();shape.vertices=n;
        int[] vao=new int[1],vbo=new int[1];
        GLES30.glGenVertexArrays(1,vao,0);GLES30.glGenBuffers(1,vbo,0);
        shape.vao=vao[0];shape.vbo=vbo[0];
        GLES30.glBindVertexArray(shape.vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,shape.vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,n*24,fb,GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,24,0);
        GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,24,12);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,instanceBuffers[slot]);
        for(int attr=2;attr<=3;attr++){
            GLES30.glEnableVertexAttribArray(attr);
            GLES30.glVertexAttribPointer(attr,4,GLES30.GL_FLOAT,false,32,(attr-2)*16);
            GLES30.glVertexAttribDivisor(attr,1);
        }
        GLES30.glBindVertexArray(0);
        return shape;
    }
    private static float[] readVec3(ByteBuffer bb,int binStart,JSONArray views,JSONArray accessors,int index) throws Exception{
        JSONObject a=accessors.getJSONObject(index);
        if(a.getInt("componentType")!=5126 || !"VEC3".equals(a.getString("type")))
            throw new IllegalArgumentException("GLB POSITION/NORMAL precisa VEC3 FLOAT32");
        JSONObject v=views.getJSONObject(a.getInt("bufferView"));
        int n=a.getInt("count"),p=binStart+v.optInt("byteOffset",0)+a.optInt("byteOffset",0);
        int stride=v.optInt("byteStride",12);
        float[] result=new float[n*3];
        for(int i=0;i<n;i++)for(int k=0;k<3;k++)result[i*3+k]=bb.getFloat(p+i*stride+k*4);
        return result;
    }
    /** Fonte 9 floats: X,Y,Z, sx,sy,sz, cos,sin, familia. */
    void drawCulled(int program,float[] source,float eyeX,float eyeZ,float forwardX,float forwardZ,float drawDistance) {
        if(source==null || source.length==0)return;
        float dl=(float)Math.hypot(forwardX,forwardZ);
        if(dl>.01f){forwardX/=dl;forwardZ/=dl;}
        boolean changed=source!=oldSource || Math.abs(eyeX-oldX)>5f || Math.abs(eyeZ-oldZ)>5f ||
            forwardX*oldFx+forwardZ*oldFz<.978f || oldMax!=drawDistance;
        if(changed){
            Floats[] groups=new Floats[15];for(int i=0;i<15;i++)groups[i]=new Floats();
            for(int o=0;o+8<source.length;o+=9){
                float dx=source[o]-eyeX,dz=source[o+2]-eyeZ,dist=(float)Math.hypot(dx,dz);
                float scale=Math.max(source[o+3],Math.max(source[o+4],source[o+5]));
                if(dist>drawDistance+scale*1.5f)continue;
                if(dist>32f && dx*forwardX+dz*forwardZ < -dist*.17f)continue;
                int level=dist<55f?0:dist<155f?1:2;
                int family=Math.max(0,Math.min(4,(int)source[o+8]));
                if(level==0 && family>=3)level=1;
                groups[level*5+family].add8(source,o);
            }
            for(int l=0;l<3;l++)for(int f=0;f<5;f++){
                int slot=l*5+f;batches[slot]=groups[slot].array();count[l][f]=batches[slot].length/8;
                if(count[l][f]>0){
                    float[] a=batches[slot];
                    FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                    b.put(a).position(0);
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,instanceBuffers[slot]);
                    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,a.length*4,b,GLES30.GL_DYNAMIC_DRAW);
                }
            }
            oldSource=source;oldX=eyeX;oldZ=eyeZ;oldFx=forwardX;oldFz=forwardZ;oldMax=drawDistance;
        }
        for(int l=0;l<3;l++)for(int f=0;f<5;f++){
            Shape s=shapes[l][f];int num=count[l][f];if(s==null||num==0)continue;
            GLES30.glBindVertexArray(s.vao);
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES,0,s.vertices,num);
        }
        GLES30.glBindVertexArray(0);
    }
}
