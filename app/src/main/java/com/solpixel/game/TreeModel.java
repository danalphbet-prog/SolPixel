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

/** Le diretamente os GLB 2.0 enviados, preservando PNG e alpha MASK. OpenGL ES 3.0. */
final class TreeModel {
    private final Primitive[] primitives;
    private final int[] textures;
    private final int instanceBuffer;

    private static final class Primitive {
        int vao,vertices,indices,count,material;
    }
    TreeModel(Context context,String name) throws Exception {
        byte[] glb;
        try(InputStream stream=context.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[32768];int size;
            while((size=stream.read(buf))!=-1)out.write(buf,0,size);
            glb=out.toByteArray();
        }
        ByteBuffer file=ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        if(file.getInt(0)!=0x46546C67 || file.getInt(4)!=2)throw new IllegalArgumentException("GLB 2.0 invalido: "+name);
        int jsonLen=file.getInt(12);
        if(file.getInt(16)!=0x4E4F534A)throw new IllegalArgumentException("GLB sem JSON");
        JSONObject root=new JSONObject(new String(glb,20,jsonLen,"UTF-8"));
        int binHeader=20+jsonLen;
        if(file.getInt(binHeader+4)!=0x004E4942)throw new IllegalArgumentException("GLB sem BIN");
        int binStart=binHeader+8;
        JSONArray views=root.getJSONArray("bufferViews");
        JSONArray accessors=root.getJSONArray("accessors");
        JSONArray images=root.getJSONArray("images");
        textures=new int[images.length()];
        GLES30.glGenTextures(textures.length,textures,0);
        for(int k=0;k<textures.length;k++){
            JSONObject iv=views.getJSONObject(images.getJSONObject(k).getInt("bufferView"));
            int offset=binStart+iv.optInt("byteOffset",0),length=iv.getInt("byteLength");
            Bitmap bmp=BitmapFactory.decodeByteArray(glb,offset,length);
            if(bmp==null)throw new IllegalStateException("PNG da arvore nao pode ser decodificado");
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,textures[k]);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR_MIPMAP_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_REPEAT);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_REPEAT);
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,bmp,0);
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);bmp.recycle();
        }
        JSONArray meshPrimitives=root.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives");
        JSONArray materials=root.getJSONArray("materials");
        primitives=new Primitive[meshPrimitives.length()];
        for(int k=0;k<primitives.length;k++){
            JSONObject p=meshPrimitives.getJSONObject(k),attrs=p.getJSONObject("attributes");
            float[] pos=readFloats(file,binStart,views,accessors,attrs.getInt("POSITION"),3);
            float[] normals=readFloats(file,binStart,views,accessors,attrs.getInt("NORMAL"),3);
            float[] uv=readFloats(file,binStart,views,accessors,attrs.getInt("TEXCOORD_0"),2);
            int count=pos.length/3;
            FloatBuffer vertex=ByteBuffer.allocateDirect(count*8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            for(int i=0;i<count;i++)vertex.put(pos,i*3,3).put(normals,i*3,3).put(uv,i*2,2);
            vertex.position(0);
            JSONObject indexAccessor=accessors.getJSONObject(p.getInt("indices"));
            if(indexAccessor.getInt("componentType")!=5125)throw new IllegalArgumentException("Indice GLB precisa UINT32");
            JSONObject indexView=views.getJSONObject(indexAccessor.getInt("bufferView"));
            int nIndices=indexAccessor.getInt("count");
            ByteBuffer indexed=ByteBuffer.allocateDirect(nIndices*4).order(ByteOrder.nativeOrder());
            int off=binStart+indexView.optInt("byteOffset",0)+indexAccessor.optInt("byteOffset",0);
            for(int i=0;i<nIndices;i++)indexed.putInt(file.getInt(off+i*4));
            indexed.position(0);
            Primitive primitive=new Primitive();primitive.count=nIndices;
            int matId=p.getInt("material");
            primitive.material=materials.getJSONObject(matId).getJSONObject("pbrMetallicRoughness")
                .getJSONObject("baseColorTexture").getInt("index");
            int[] a=new int[1],b=new int[2];
            GLES30.glGenVertexArrays(1,a,0);GLES30.glGenBuffers(2,b,0);
            primitive.vao=a[0];primitive.vertices=b[0];primitive.indices=b[1];
            GLES30.glBindVertexArray(primitive.vao);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,primitive.vertices);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,count*32,vertex,GLES30.GL_STATIC_DRAW);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,primitive.indices);
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,nIndices*4,indexed,GLES30.GL_STATIC_DRAW);
            for(int i=0;i<3;i++)GLES30.glEnableVertexAttribArray(i);
            GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,32,0);
            GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,32,12);
            GLES30.glVertexAttribPointer(2,2,GLES30.GL_FLOAT,false,32,24);
            GLES30.glBindVertexArray(0);primitives[k]=primitive;
        }
        int[] id=new int[1];GLES30.glGenBuffers(1,id,0);instanceBuffer=id[0];
    }
    private static float[] readFloats(ByteBuffer bin,int start,JSONArray views,JSONArray accessors,int ai,int components) throws Exception{
        JSONObject ac=accessors.getJSONObject(ai);
        if(ac.getInt("componentType")!=5126)throw new IllegalArgumentException("Atributos devem ser FLOAT32");
        JSONObject view=views.getJSONObject(ac.getInt("bufferView"));
        int count=ac.getInt("count"),offset=start+view.optInt("byteOffset",0)+ac.optInt("byteOffset",0);
        int stride=view.optInt("byteStride",4*components);
        float[] arr=new float[count*components];
        for(int i=0;i<count;i++)for(int k=0;k<components;k++)arr[i*components+k]=bin.getFloat(offset+i*stride+k*4);
        return arr;
    }
    void draw(int program,float[] instances){
        int count=instances.length/8;
        if(count==0)return;
        FloatBuffer buffer=ByteBuffer.allocateDirect(instances.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(instances).position(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,instanceBuffer);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,instances.length*4,buffer,GLES30.GL_DYNAMIC_DRAW);
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"uTex"),1);
        for(int i=0;i<primitives.length;i++){
            Primitive p=primitives[i];
            GLES30.glBindVertexArray(p.vao);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,instanceBuffer);
            GLES30.glEnableVertexAttribArray(3);GLES30.glEnableVertexAttribArray(4);
            GLES30.glVertexAttribPointer(3,4,GLES30.GL_FLOAT,false,32,0);
            GLES30.glVertexAttribPointer(4,4,GLES30.GL_FLOAT,false,32,16);
            GLES30.glVertexAttribDivisor(3,1);GLES30.glVertexAttribDivisor(4,1);
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,textures[p.material]);
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uLeaf"),i==0?1f:0f);
            GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES,p.count,GLES30.GL_UNSIGNED_INT,0,count);
        }
        GLES30.glBindVertexArray(0);
    }
}
