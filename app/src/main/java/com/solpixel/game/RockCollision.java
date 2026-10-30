package com.solpixel.game;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Solas v0.3: colisor de superficie (triangulos GLB originais), nao esferas nem convex hull.
 * Consulta apenas pedras dentro de 5 m da caixa horizontal do modelo e somente
 * quando o jogador se move; grade espacial evita examinar rochas distantes.
 * Aproximacao 2.5D: raios verticais sobre triangulos, sem colisao volumetrica 3D.
 */
final class RockCollision {
    private static final String[] NAMES={"high_a.glb","high_b.glb","high_c.glb","low_d.glb","low_f.glb"};
    private static final float CELL=14f, ACTIVE=5f, PLAYER_RADIUS=.43f;
    private static final float[] SAMPLE_X={0f,1f,-1f,0f,0f,.7071f,.7071f,-.7071f,-.7071f};
    private static final float[] SAMPLE_Z={0f,0f,0f,1f,-1f,.7071f,-.7071f,.7071f,-.7071f};
    private final float[][] mesh=new float[5][];
    private final float[] horizontalBound=new float[5];
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static int cell(float x){return (int)Math.floor(x/CELL);}

    // Construtor de teste: triangulos sintéticos para validar formas concavas.
    RockCollision(float[][] triangles){
        for(int k=0;k<5;k++){
            mesh[k]=triangles[k];
            float maximum=0f;
            for(int i=0;i+2<mesh[k].length;i+=3)
                maximum=Math.max(maximum,(float)Math.hypot(mesh[k][i],mesh[k][i+2]));
            horizontalBound[k]=Math.max(.25f,maximum);
        }
    }
    RockCollision(Context context) throws Exception {
        for(int k=0;k<5;k++){
            mesh[k]=loadPositions(context,"rock_models/"+NAMES[k]);
            float max=0f;
            for(int v=0;v+2<mesh[k].length;v+=3)
                max=Math.max(max,(float)Math.hypot(mesh[k][v],mesh[k][v+2]));
            horizontalBound[k]=Math.max(.25f,max);
        }
    }
    private static byte[] readBytes(Context c,String name) throws Exception {
        try(InputStream in=c.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[16384];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);
            return out.toByteArray();
        }
    }
    private static float[] loadPositions(Context c,String path) throws Exception {
        byte[] bytes=readBytes(c,path);
        ByteBuffer bb=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if(bb.getInt(0)!=0x46546c67||bb.getInt(4)!=2)throw new IllegalArgumentException("GLB invalido: "+path);
        int jsonLength=bb.getInt(12),header=20+jsonLength;
        if(bb.getInt(header+4)!=0x004e4942)throw new IllegalArgumentException("BIN ausente: "+path);
        int binary=header+8;
        JSONObject root=new JSONObject(new String(bytes,20,jsonLength,"UTF-8"));
        JSONArray accessors=root.getJSONArray("accessors"),views=root.getJSONArray("bufferViews");
        JSONObject attr=root.getJSONArray("meshes").getJSONObject(0)
            .getJSONArray("primitives").getJSONObject(0).getJSONObject("attributes");
        JSONObject a=accessors.getJSONObject(attr.getInt("POSITION"));
        if(a.getInt("componentType")!=5126||!"VEC3".equals(a.getString("type")))
            throw new IllegalArgumentException("POSITION nao e FLOAT32 VEC3: "+path);
        JSONObject view=views.getJSONObject(a.getInt("bufferView"));
        int n=a.getInt("count"),at=binary+view.optInt("byteOffset",0)+a.optInt("byteOffset",0);
        int stride=view.optInt("byteStride",12);
        if(n%3!=0)throw new IllegalArgumentException("Modelo deve usar triangulos sem indices: "+path);
        float[] result=new float[n*3];
        for(int i=0;i<n;i++)for(int j=0;j<3;j++)result[i*3+j]=bb.getFloat(at+i*stride+4*j);
        return result;
    }
    static final class Index {
        final float[] source;
        final HashMap<Long,int[]> cells=new HashMap<>();
        Index(float[] data,float[] bounds){
            HashMap<Long,ArrayList<Integer>> staging=new HashMap<>();
            source=data;
            if(data==null)return;
            for(int o=0;o+8<data.length;o+=9){
                int family=Math.max(0,Math.min(4,Math.round(data[o+8])));
                float radius=bounds[family]*Math.max(data[o+3],data[o+5])+ACTIVE+.7f;
                // Uma esfera em XZ engloba os triangulos mesmo apos rotacao.
                int xa=cell(data[o]-radius),xb=cell(data[o]+radius);
                int za=cell(data[o+2]-radius),zb=cell(data[o+2]+radius);
                for(int iz=za;iz<=zb;iz++)for(int ix=xa;ix<=xb;ix++){
                    long key=key(ix,iz);
                    ArrayList<Integer> group=staging.get(key);
                    if(group==null){group=new ArrayList<>();staging.put(key,group);}
                    group.add(o);
                }
            }
            for(java.util.Map.Entry<Long,ArrayList<Integer>> e:staging.entrySet()){
                int[] offsets=new int[e.getValue().size()];
                for(int i=0;i<offsets.length;i++)offsets[i]=e.getValue().get(i);
                cells.put(e.getKey(),offsets);
            }
            staging.clear();
        }
    }
    Index index(float[] rocks){return new Index(rocks,horizontalBound);}

    /** Altura da intersecao superior do GLB no ponto horizontal, ou -infinito. */
    private float topAtRock(float px,float pz,float[] r,int o){
        int family=Math.max(0,Math.min(4,Math.round(r[o+8])));
        float[] vertices=mesh[family];
        float dx=px-r[o],dz=pz-r[o+2];
        float cs=r[o+6],sn=r[o+7];
        float sx=Math.max(.08f,r[o+3]),sy=Math.max(.08f,r[o+4]),sz=Math.max(.08f,r[o+5]);
        float lx=(cs*dx+sn*dz)/sx,lz=(-sn*dx+cs*dz)/sz;
        float yMax=Float.NEGATIVE_INFINITY;
        for(int i=0;i+8<vertices.length;i+=9){
            float ax=vertices[i],az=vertices[i+2];
            float bx=vertices[i+3],bz=vertices[i+5];
            float cx=vertices[i+6],cz=vertices[i+8];
            // Pula triangulos cuja caixa projetada nao contem a amostra.
            if(lx<Math.min(ax,Math.min(bx,cx))-1e-5f||lx>Math.max(ax,Math.max(bx,cx))+1e-5f||
               lz<Math.min(az,Math.min(bz,cz))-1e-5f||lz>Math.max(az,Math.max(bz,cz))+1e-5f)continue;
            float d=(bz-cz)*(ax-cx)+(cx-bx)*(az-cz);
            if(Math.abs(d)<1e-8f)continue;
            float u=((bz-cz)*(lx-cx)+(cx-bx)*(lz-cz))/d;
            float v=((cz-az)*(lx-cx)+(ax-cx)*(lz-cz))/d;
            if(u<-.0001f||v<-.0001f||u+v>1.0001f)continue;
            float localY=u*vertices[i+1]+v*vertices[i+4]+(1f-u-v)*vertices[i+7];
            yMax=Math.max(yMax,r[o+1]+localY*sy);
        }
        return yMax;
    }
    private float heightAt(float px,float pz,Index index){
        float result=Float.NEGATIVE_INFINITY;
        if(index==null||index.source==null)return result;
        int[] offsets=index.cells.get(key(cell(px),cell(pz)));
        if(offsets==null)return result;
        float[] r=index.source;
        for(int o:offsets){
            int family=Math.max(0,Math.min(4,Math.round(r[o+8])));
            float bound=horizontalBound[family]*Math.max(r[o+3],r[o+5]);
            if(Math.hypot(px-r[o],pz-r[o+2])>bound+ACTIVE)continue;
            result=Math.max(result,topAtRock(px,pz,r,o));
        }
        return result;
    }
    float surfaceAt(float px,float pz,float base,float allowedTop,Index index){
        float top=heightAt(px,pz,index);
        // Sem teleporte para o topo de pedras muito altas.
        return top>base+.035f && top<=allowedTop+.02f?top:base;
    }
    boolean blocked(float px,float pz,float footY,Index index){
        for(int s=0;s<SAMPLE_X.length;s++){
            float qx=px+SAMPLE_X[s]*PLAYER_RADIUS;
            float qz=pz+SAMPLE_Z[s]*PLAYER_RADIUS;
            float y=heightAt(qx,qz,index);
            if(y>footY+.58f && y>World.height(qx,qz)+.25f)return true;
        }
        return false;
    }
}
