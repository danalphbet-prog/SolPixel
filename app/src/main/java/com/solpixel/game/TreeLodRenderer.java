package com.solpixel.game;

import android.content.Context;
import android.util.Log;
import java.util.Arrays;

/** Solas v3: um draw instanciado por faixa LOD e especie, sem mudar colisao/sombra dos originais. */
final class TreeLodRenderer {
    private static final int LEVELS=7;
    private static final String[] PERCENT={"20","50","80","90","95","99"};
    private static final float MAX_DIST=4000f; // pinheiro distante ate 4 km
    private final TreeModel[][] models=new TreeModel[2][LEVELS];
    private final float[][][] packed=new float[2][LEVELS][];
    private float[] nearNormal,nearGiant,farNormal,farGiant;
    private float lastX=Float.MAX_VALUE,lastZ=Float.MAX_VALUE,lastFx=9f,lastFz=9f;

    TreeLodRenderer(Context context,TreeModel normal,TreeModel giant){
        models[0][0]=normal;models[1][0]=giant;
        for(int kind=0;kind<2;kind++){
            String prefix=kind==0?"normal":"gigante";
            for(int lod=1;lod<LEVELS;lod++){
                try{models[kind][lod]=new TreeModel(context,"tree_lod/"+prefix+"_LOD"+PERCENT[lod-1]+".glb");}
                catch(Exception ex){
                    Log.w("Solas","LOD "+prefix+" "+PERCENT[lod-1]+" indisponivel; usando modelo anterior",ex);
                    models[kind][lod]=models[kind][lod-1];
                }
            }
        }
    }
    private static int level(float distance,boolean giant){
        if(giant){
            if(distance<60f)return 0;
            if(distance<70f)return 1;   // LOD 20
            if(distance<220f)return 2;  // LOD 50
            if(distance<380f)return 3;  // LOD 80
            if(distance<700f)return 4;  // LOD 90
            if(distance<3000f)return 5; // LOD 95, incluindo 1300-2500m
            return 6;                   // LOD 99, 3000-4000m
        }
        if(distance<45f)return 0;
        if(distance<90f)return 1;
        if(distance<180f)return 2;
        if(distance<300f)return 3;
        if(distance<600f)return 4;
        if(distance<1100f)return 5;
        return 6;
    }
    private static final class Builder {
        float[] values=new float[128];int count=0;
        void add(float[] source,int offset){
            if(count+8>values.length)values=Arrays.copyOf(values,values.length*2);
            System.arraycopy(source,offset,values,count,8);count+=8;
        }
        float[] end(){return Arrays.copyOf(values,count);}
    }
    private void split(float[] source,Builder[][] builders,int kind,float eyeX,float eyeZ,float fx,float fz){
        if(source==null)return;
        for(int offset=0;offset+7<source.length;offset+=8){
            float dx=source[offset]-eyeX,dz=source[offset+2]-eyeZ;
            float ds=dx*dx+dz*dz;
            float limit=kind==1?MAX_DIST:2500f; // arvores normais mantem alcance anterior
            if(ds>limit*limit)continue;
            float distance=(float)Math.sqrt(ds);
            if(distance>45f && dx*fx+dz*fz < -distance*.18f)continue;
            builders[kind][level(distance,kind==1)].add(source,offset);
        }
    }
    void draw(int program,float[] normal,float[] giant,float[] farN,float[] farG,
              float eyeX,float eyeZ,float fx,float fz){
        float d=(float)Math.hypot(fx,fz);
        if(d>.001f){fx/=d;fz/=d;}
        boolean dirty=normal!=nearNormal||giant!=nearGiant||farN!=farNormal||farG!=farGiant||
             Math.abs(eyeX-lastX)>9f||Math.abs(eyeZ-lastZ)>9f||
             fx*lastFx+fz*lastFz<.988f;
        if(dirty){
            Builder[][] groups=new Builder[2][LEVELS];
            for(int kind=0;kind<2;kind++)for(int level=0;level<LEVELS;level++)groups[kind][level]=new Builder();
            split(normal,groups,0,eyeX,eyeZ,fx,fz);
            split(giant,groups,1,eyeX,eyeZ,fx,fz);
            split(farN,groups,0,eyeX,eyeZ,fx,fz);
            split(farG,groups,1,eyeX,eyeZ,fx,fz);
            for(int kind=0;kind<2;kind++)for(int level=0;level<LEVELS;level++)
                packed[kind][level]=groups[kind][level].end();
            nearNormal=normal;nearGiant=giant;farNormal=farN;farGiant=farG;
            lastX=eyeX;lastZ=eyeZ;lastFx=fx;lastFz=fz;
        }
        for(int kind=0;kind<2;kind++)for(int level=0;level<LEVELS;level++){
            TreeModel model=models[kind][level];
            if(model!=null&&packed[kind][level]!=null&&packed[kind][level].length>0)
                model.draw(program,packed[kind][level]);
        }
    }
}
