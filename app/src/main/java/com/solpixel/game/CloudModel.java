package com.solpixel.game;

import android.content.Context;
import android.opengl.GLES30;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Nuvens lowpoly: 6 modelos GLB (padrao, pequena, larga, alta, stratus e cumulonimbus). */
final class CloudModel {
    private static final String[] NAMES={"nuvem_padrao.glb","nuvem_pequena.glb","nuvem_larga.glb","nuvem_alta.glb","nuvem_stratus.glb","nuvem_cumulonimbus.glb"};
    private final Mesh[] meshes=new Mesh[NAMES.length];
    private static final class Mesh {int vao,vbo,ibo,count; float cx,cy,cz;}
    CloudModel(Context context) throws Exception {
        // Uma nuvem invalida nao deve apagar o ceu inteiro.
        int loaded=0;
        for(int k=0;k<NAMES.length;k++){
            try {
                meshes[k]=readGlb(context,NAMES[k]);
                loaded++;
                android.util.Log.i("SolasCloud","GLB carregado: "+NAMES[k]);
            } catch(Exception e) {
                android.util.Log.e("SolasCloud","Falha no GLB: "+NAMES[k],e);
            }
        }
        if(loaded==0)throw new IllegalStateException("Nenhum GLB de nuvem valido");
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
    /** Nuvens voxel estaticas bem alem do terreno, com afastamento suave.
     *  Coordenadas globais independentes do movimento/rotacao da camera.
     *  Nenhum limite fixo por frame: a aparicao depende apenas da distancia.
     */
    void draw(int program,float x,float eyeY,float z,float dx,float dz,float time,float visibility){
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        // A mascara de profundidade das nuvens visiveis oculta god rays.
        // O fragment shader usa alpha-dither para suavizar o contorno/fade.
        GLES30.glDepthMask(true);
        int pos=GLES30.glGetUniformLocation(program,"uCloudPosScale");
        int center=GLES30.glGetUniformLocation(program,"uCloudCenter");
        int alpha=GLES30.glGetUniformLocation(program,"uAlpha");
        float intro=fade(0f,2.8f,time)*visibility;
        if(intro>.001f){
            // Referencia fixa distante, acima do ponto de vista inicial.
            float fixedDist=(float)Math.hypot(350f-x,-1120f-z);
            float fixedFade=fade(340f,650f,fixedDist)*(1f-fade(3150f,3900f,fixedDist));
            if(fixedFade*intro>.014f)
                drawOne(meshes[1],pos,center,alpha,350f,340f,-1120f,86f,.90f*intro*fixedFade);

            final int cell=420;
            int cx=(int)Math.floor(x/cell),cz=(int)Math.floor(z/cell);
            // A borda de busca fica FORA da banda do fade: sem aparecimento repentino.
            for(int j=-8;j<=8;j++)for(int i=-8;i<=8;i++){
                int gx=cx+i,gz=cz+j;
                if(hash(gx*11+7,gz*19-5)>.78f)continue;
                float px=(gx+.5f)*cell+(hash(gx*13,gz*7)-.5f)*92f;
                float pz=(gz+.5f)*cell+(hash(gx*7-17,gz*23)-.5f)*92f;
                float dist=(float)Math.hypot(px-x,pz-z);
                float opacity=.90f*intro*fade(340f,650f,dist)*
                                          (1f-fade(3150f,3900f,dist));
                if(opacity<=.014f)continue;
                int type=Math.min(NAMES.length-1,(int)(hash(gx*17,gz*31)*NAMES.length));
                Mesh mesh=meshes[type];
                if(mesh==null){for(Mesh available:meshes)if(available!=null){mesh=available;break;}}
                float scale=96f+hash(gx*29,gz*17)*48f;
                float height=cloudHeight(gx,gz);
                drawOne(mesh,pos,center,alpha,px,height,pz,scale,opacity);
            }
        }
        GLES30.glBindVertexArray(0);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
    }
    // Alturas distribuidas pela mesma seed, sem camadas visiveis em bandas.
    private static float cloudHeight(int gx,int gz){
        float base=330f+hash(gx*37,gz*41)*102f;
        float mode=hash(gx*31+13,gz*17-51);
        if(mode<.28f)return 215f+hash(gx*41-11,gz*29+7)*48f;
        if(mode>.82f)return 450f+hash(gx*19-37,gz*13+23)*95f;
        return base;
    }
    /** Quatro sombras analiticas locais de nuvens baixas. Nao usa shadow map. */
    static void fillLowCloudShadows(float eyeX,float eyeZ,float[] out){
        java.util.Arrays.fill(out,0f);
        float[] dist=new float[]{Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE};
        final int cell=420;
        int cx=(int)Math.floor(eyeX/cell),cz=(int)Math.floor(eyeZ/cell);
        for(int j=-3;j<=3;j++)for(int i=-3;i<=3;i++){
            int gx=cx+i,gz=cz+j;
            if(hash(gx*11+7,gz*19-5)>.78f)continue;
            if(hash(gx*31+13,gz*17-51)>=.28f)continue; // apenas nuvens baixas
            float px=(gx+.5f)*cell+(hash(gx*13,gz*7)-.5f)*92f;
            float pz=(gz+.5f)*cell+(hash(gx*7-17,gz*23)-.5f)*92f;
            float h=cloudHeight(gx,gz);
            float projection=Math.max(55f,h-World.height(px,pz));
            // Sombra deslocada pelo angulo da luz solar fixa.
            float sx=px-.53f/.48f*projection,sz=pz+.47f/.48f*projection;
            float d2=(sx-eyeX)*(sx-eyeX)+(sz-eyeZ)*(sz-eyeZ);
            if(d2>650f*650f)continue;
            int insert=-1;
            for(int k=0;k<4;k++)if(d2<dist[k]){insert=k;break;}
            if(insert<0)continue;
            for(int k=3;k>insert;k--){
                dist[k]=dist[k-1];
                for(int q=0;q<4;q++)out[k*4+q]=out[(k-1)*4+q];
            }
            dist[insert]=d2;
            out[insert*4]=sx;out[insert*4+1]=sz;
            out[insert*4+2]=120f+20f*hash(gx*29,gz*17);
            out[insert*4+3]=.10f+.045f*hash(gx*13+7,gz*37-17);
        }
    }
    private static float fade(float start,float end,float value){
        float t=Math.max(0f,Math.min(1f,(value-start)/(end-start)));
        return t*t*(3f-2f*t);
    }
    private static void drawOne(Mesh mesh,int loc,int center,int alpha,float px,float py,float pz,float scale,float opacity){
        if(mesh==null)return;
        GLES30.glUniform4f(loc,px,py,pz,scale);
        GLES30.glUniform3f(center,mesh.cx,mesh.cy,mesh.cz);
        GLES30.glUniform1f(alpha,opacity);
        GLES30.glBindVertexArray(mesh.vao);
        GLES30.glDrawElements(GLES30.GL_TRIANGLES,mesh.count,GLES30.GL_UNSIGNED_INT,0);
    }
}
