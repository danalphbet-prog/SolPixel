package com.solpixel.game;

import android.content.Context;
import android.opengl.GLES30;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Tres malhas OBJ convertidas offline para BIN. Desenhadas gradualmente sem rebuild. */
final class CloudModel {
    private static final String[] NAMES={"cloud_pequena.bin","cloud_larga.bin","cloud_alta.bin"};
    private final Mesh[] meshes=new Mesh[3];
    private static final class Mesh {int vao,vbo,ibo,count;float cx,cy,cz;}
    CloudModel(Context context) throws Exception {
        for(int k=0;k<3;k++){
            byte[] data;
            try(InputStream input=context.getAssets().open(NAMES[k]); ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[16384];int n;
                while((n=input.read(buf))>0)out.write(buf,0,n);
                data=out.toByteArray();
            }
            ByteBuffer bin=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int count=bin.getInt(),ids=bin.getInt();
            if(count<=0 || count>500000 || ids<=0 || ids>1500000)throw new IllegalArgumentException("cloud BIN invalido");
            ByteBuffer vertex=ByteBuffer.allocateDirect(count*24).order(ByteOrder.nativeOrder());
            float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY;
            float minZ=Float.POSITIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
            for(int i=0;i<count;i++){
                float x=bin.getFloat(),y=bin.getFloat(),z=bin.getFloat();
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                vertex.putFloat(x).putFloat(y).putFloat(z);
                vertex.putFloat(bin.getFloat()).putFloat(bin.getFloat()).putFloat(bin.getFloat());
            }
            vertex.position(0);
            ByteBuffer indices=ByteBuffer.allocateDirect(ids*4).order(ByteOrder.nativeOrder());
            for(int i=0;i<ids;i++)indices.putInt(bin.getInt());
            indices.position(0);
            Mesh mesh=new Mesh(); mesh.count=ids;
            mesh.cx=(minX+maxX)*.5f;mesh.cy=0;mesh.cz=(minZ+maxZ)*.5f;
            int[] vao=new int[1],vbo=new int[2];
            GLES30.glGenVertexArrays(1,vao,0);GLES30.glGenBuffers(2,vbo,0);
            mesh.vao=vao[0];mesh.vbo=vbo[0];mesh.ibo=vbo[1];
            GLES30.glBindVertexArray(mesh.vao);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,mesh.vbo);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,count*24,vertex,GLES30.GL_STATIC_DRAW);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,mesh.ibo);
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,ids*4,indices,GLES30.GL_STATIC_DRAW);
            GLES30.glEnableVertexAttribArray(0);GLES30.glEnableVertexAttribArray(1);
            GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,24,0);
            GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,24,12);
            GLES30.glBindVertexArray(0);meshes[k]=mesh;
        }
    }
    private static float hash(int x,int z){
        int n=x*374761393+z*668265263+0x165667B1;
        n=(n^(n>>>13))*1274126177;
        return ((n^(n>>>16))&0x7fffffff)/2147483648f;
    }
    /** Nuvens fixas acima das montanhas, limitadas por frustum e distancia. */
    void draw(int program,float x,float y,float z,float dx,float dz,float time){
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glDepthMask(false);
        int loc=GLES30.glGetUniformLocation(program,"uCloudPosScale");
        int center=GLES30.glGetUniformLocation(program,"uCloudCenter");
        int alpha=GLES30.glGetUniformLocation(program,"uAlpha");
        final int cell=195;
        int cx=(int)Math.floor(x/cell),cz=(int)Math.floor(z/cell);
        int rendered=0;
        for(int radius=0;radius<=3 && rendered<10;radius++){
            for(int j=-radius;j<=radius && rendered<10;j++)for(int i=-radius;i<=radius && rendered<10;i++){
                if(Math.max(Math.abs(i),Math.abs(j))!=radius)continue;
                int gx=cx+i,gz=cz+j;
                if(hash(gx*11+7,gz*19-5)>.77f)continue;
                float px=(gx+.5f)*cell+hash(gx*13,gz*7)*57f;
                float pz=(gz+.5f)*cell+hash(gx*7-17,gz*23)*57f;
                float vx=px-x,vz=pz-z,dist=(float)Math.hypot(vx,vz);
                if(dist>700f||dist<95f)continue;
                if(vx*dx+vz*dz < -dist*.45f)continue;
                int type=(int)(hash(gx*17,gz*31)*3f);
                Mesh mesh=meshes[Math.min(type,2)];
                float scale=(9f+hash(gx*29,gz*17)*4.5f);
                float height=137f+hash(gx*37,gz*41)*65f;
                // Nuvens aparecem de maneira gradual ao iniciar, depois permanecem fixas.
                float fade=Math.min(1f,Math.max(0f,(time-rendered*.22f)*.7f));
                GLES30.glUniform4f(loc,px,height,pz,scale);
                GLES30.glUniform3f(center,mesh.cx,mesh.cy,mesh.cz);
                GLES30.glUniform1f(alpha,.90f*fade);
                GLES30.glBindVertexArray(mesh.vao);
                GLES30.glDrawElements(GLES30.GL_TRIANGLES,mesh.count,GLES30.GL_UNSIGNED_INT,0);
                rendered++;
            }
        }
        GLES30.glBindVertexArray(0);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
    }
}
