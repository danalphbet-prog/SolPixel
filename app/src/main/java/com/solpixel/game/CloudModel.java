package com.solpixel.game;

import android.content.Context;
import android.opengl.GLES30;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Nuvens volumetricas: 3 modelos GLB 2.0 convertidos dos OBJ originais. */
final class CloudModel {
    private static final String[] NAMES={"nuvem_pequena.glb","nuvem_larga.glb","nuvem_alta.glb"};
    private final Mesh[] meshes=new Mesh[3];
    private static final class Mesh {int vao,vbo,ibo,count; float cx,cy,cz;}
    CloudModel(Context context) throws Exception {
        for(int k=0;k<NAMES.length;k++) meshes[k]=readGlb(context,NAMES[k]);
    }
    private static Mesh readGlb(Context context,String name) throws Exception {
        byte[] data;
        try(InputStream in=context.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[32768];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            data=out.toByteArray();
        }
        ByteBuffer src=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if(data.length<32||src.getInt(0)!=0x46546c67||src.getInt(4)!=2)
            throw new IllegalArgumentException("GLB 2.0 invalido: "+name);
        int jsonSize=src.getInt(12);
        if(src.getInt(16)!=0x4E4F534A||jsonSize<1||20+jsonSize+8>data.length)
            throw new IllegalArgumentException("JSON do GLB invalido: "+name);
        JSONObject json=new JSONObject(new String(data,20,jsonSize,"UTF-8"));
        int binStart=20+jsonSize;
        if(src.getInt(binStart+4)!=0x004E4942)throw new IllegalArgumentException("BIN do GLB invalido");
        binStart+=8;
        JSONArray views=json.getJSONArray("bufferViews"),accessors=json.getJSONArray("accessors");
        JSONObject primitive=json.getJSONArray("meshes").getJSONObject(0)
                .getJSONArray("primitives").getJSONObject(0);
        JSONObject attrs=primitive.getJSONObject("attributes");
        JSONObject pa=accessors.getJSONObject(attrs.getInt("POSITION"));
        JSONObject na=accessors.getJSONObject(attrs.getInt("NORMAL"));
        JSONObject ia=accessors.getJSONObject(primitive.getInt("indices"));
        int vertexCount=pa.getInt("count"),indexCount=ia.getInt("count");
        if(vertexCount<3||vertexCount>200000||indexCount<3||indexCount>700000||indexCount%3!=0)
            throw new IllegalArgumentException("Contagem de malha fora dos limites: "+name);
        if(na.getInt("count")!=vertexCount||pa.getInt("componentType")!=5126||
           na.getInt("componentType")!=5126||ia.getInt("componentType")!=5125)
            throw new IllegalArgumentException("Formato de acessores GLB inesperado: "+name);
        JSONObject pv=views.getJSONObject(pa.getInt("bufferView"));
        JSONObject nv=views.getJSONObject(na.getInt("bufferView"));
        JSONObject iv=views.getJSONObject(ia.getInt("bufferView"));
        int po=binStart+pv.optInt("byteOffset",0)+pa.optInt("byteOffset",0);
        int no=binStart+nv.optInt("byteOffset",0)+na.optInt("byteOffset",0);
        int io=binStart+iv.optInt("byteOffset",0)+ia.optInt("byteOffset",0);
        int ps=pv.optInt("byteStride",12),ns=nv.optInt("byteStride",12);
        if(po<0||no<0||io<0||po+(vertexCount-1)*ps+12>data.length||
           no+(vertexCount-1)*ns+12>data.length||io+indexCount*4>data.length)
            throw new IllegalArgumentException("GLB truncado: "+name);
        ByteBuffer verts=ByteBuffer.allocateDirect(vertexCount*24).order(ByteOrder.nativeOrder());
        ByteBuffer inds=ByteBuffer.allocateDirect(indexCount*4).order(ByteOrder.nativeOrder());
        float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,minZ=Float.MAX_VALUE;
        float maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE,maxZ=-Float.MAX_VALUE;
        for(int i=0;i<vertexCount;i++){
            float x=src.getFloat(po+i*ps),y=src.getFloat(po+i*ps+4),z=src.getFloat(po+i*ps+8);
            minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);
            maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);
            verts.putFloat(x).putFloat(y).putFloat(z);
            verts.putFloat(src.getFloat(no+i*ns)).putFloat(src.getFloat(no+i*ns+4)).putFloat(src.getFloat(no+i*ns+8));
        }
        verts.position(0);
        for(int i=0;i<indexCount;i++)inds.putInt(src.getInt(io+i*4));
        inds.position(0);
        Mesh mesh=new Mesh();mesh.count=indexCount;
        mesh.cx=(minX+maxX)*.5f;mesh.cy=(minY+maxY)*.5f;mesh.cz=(minZ+maxZ)*.5f;
        int[] a=new int[1],b=new int[2];
        GLES30.glGenVertexArrays(1,a,0);GLES30.glGenBuffers(2,b,0);
        mesh.vao=a[0];mesh.vbo=b[0];mesh.ibo=b[1];
        GLES30.glBindVertexArray(mesh.vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,mesh.vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,verts.capacity(),verts,GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,mesh.ibo);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,inds.capacity(),inds,GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,24,0);
        GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,24,12);
        GLES30.glBindVertexArray(0);
        return mesh;
    }
    private static float hash(int x,int z){
        int n=x*374761393+z*668265263+0x165667B1;
        n=(n^(n>>>13))*1274126177;
        return ((n^(n>>>16))&0x7fffffff)/2147483648f;
    }
    /** Coordenadas estaveis no mundo, 3 GLB estaticos, aparecimento gradual sem trocar mesh. */
    void draw(int program,float x,float eyeY,float z,float dx,float dz,float time){
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glDepthMask(true);
        int location=GLES30.glGetUniformLocation(program,"uCloudPosScale");
        int center=GLES30.glGetUniformLocation(program,"uCloudCenter");
        int alpha=GLES30.glGetUniformLocation(program,"uAlpha");
        float fade=Math.min(1f,Math.max(0f,time*.8f));
        // Formacao assegurada no horizonte diante da orientacao inicial, acima das montanhas.
        if(visible(220f,-590f,x,z,dx,dz,1000f))
            drawOne(meshes[1],location,center,alpha,220f,230f,-590f,75f,.90f*fade); // solpixel-v06: 3x
        final int cell=245;
        int cx=(int)Math.floor(x/cell),cz=(int)Math.floor(z/cell);
        int rendered=0;
        for(int radius=0;radius<=3 && rendered<9;radius++){
            for(int j=-radius;j<=radius && rendered<9;j++)for(int i=-radius;i<=radius && rendered<9;i++){
                if(Math.max(Math.abs(i),Math.abs(j))!=radius)continue;
                int gx=cx+i,gz=cz+j;
                if(hash(gx*11+7,gz*19-5)>.84f)continue;
                float px=(gx+.5f)*cell+(hash(gx*13,gz*7)-.5f)*65f;
                float pz=(gz+.5f)*cell+(hash(gx*7-17,gz*23)-.5f)*65f;
                if(!visible(px,pz,x,z,dx,dz,1100f))continue;
                int type=Math.min(2,(int)(hash(gx*17,gz*31)*3f));
                Mesh mesh=meshes[type];
                float scale=54f+hash(gx*29,gz*17)*27f; // 3x tamanho
                float height=214f+hash(gx*37,gz*41)*63f;
                drawOne(mesh,location,center,alpha,px,height,pz,scale,.90f*fade);
                rendered++;
            }
        }
        GLES30.glBindVertexArray(0);
        GLES30.glDisable(GLES30.GL_BLEND);
    }
    private static boolean visible(float px,float pz,float x,float z,float dx,float dz,float maxDist){
        float vx=px-x,vz=pz-z,dist=(float)Math.hypot(vx,vz);
        return dist>145f && dist<maxDist && vx*dx+vz*dz > dist*.05f;
    }
    private static void drawOne(Mesh mesh,int loc,int center,int alpha,float px,float py,float pz,float scale,float opacity){
        GLES30.glUniform4f(loc,px,py,pz,scale);
        GLES30.glUniform3f(center,mesh.cx,mesh.cy,mesh.cz);
        GLES30.glUniform1f(alpha,opacity);
        GLES30.glBindVertexArray(mesh.vao);
        GLES30.glDrawElements(GLES30.GL_TRIANGLES,mesh.count,GLES30.GL_UNSIGNED_INT,0);
    }
}
