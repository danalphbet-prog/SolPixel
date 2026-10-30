package com.solpixel.game;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;

/** Macieiras gigantes (reta/torta/anterior): tres variantes e quatro LODs GLB reais.
 * Distribuicao global/estavel; gerada fora da GL thread junto do mundo.
 */
final class MacieiraRenderer {
    private static final float RANGE=4000f;
    private static final float CELL=480f;
    private static final String[] KIND={"reta","torta","anterior"};
    private static final String[] LOD={"A","B","C","D"};
    private final TreeModel[][] models=new TreeModel[3][4];
    private Placement[] lastSource;
    private float lastX=Float.MAX_VALUE,lastZ=Float.MAX_VALUE,lastFx=8f,lastFz=8f;
    private final float[][][] buckets=new float[3][4][];

    static final class Placement {
        final float x,y,z,scale,cos,sin,shade,tilt;
        final int kind;
        Placement(float x,float y,float z,float scale,float angle,int kind){
            this.x=x;this.y=y;this.z=z;this.scale=scale;
            this.cos=(float)Math.cos(angle);this.sin=(float)Math.sin(angle);
            this.shade=.06f;this.tilt=0f;this.kind=kind;
        }
    }

    MacieiraRenderer(Context context){
        for(int type=0;type<3;type++)for(int lod=0;lod<4;lod++){
            String name="macieira/"+KIND[type]+"/macieira_"+KIND[type]+"_LOD_"+LOD[lod]+".glb";
            try{
                // Macieira: 1o material e madeira, 2o e folhagem MASK.
                models[type][lod]=new TreeModel(context,name,true);
            }catch(Exception ex){
                Log.w("Solas","Macieira nao carregada: "+name,ex);
                // Nao usar LOD anterior muito pesado a grandes distancias.
                models[type][lod]=lod>0?models[type][lod-1]:null;
            }
        }
    }

    private static float hash(int x,int z){
        int n=x*374761393+z*668265263+0x165667B1;
        n=(n^(n>>>13))*1274126177;
        return ((n^(n>>>16))&0x7fffffff)/2147483647f;
    }
    private static int lod(float distance){
        if(distance<15f)return 0;
        if(distance<45f)return 1;
        if(distance<120f)return 2;
        return 3;
    }

    /** Computado uma vez por regeneracao do mundo, nao a cada frame. */
    static Placement[] generate(float centerX,float centerZ){
        ArrayList<Placement> out=new ArrayList<>();
        final float margin=RANGE+135f;
        int minX=(int)Math.floor((centerX-margin)/CELL);
        int maxX=(int)Math.ceil((centerX+margin)/CELL);
        int minZ=(int)Math.floor((centerZ-margin)/CELL);
        int maxZ=(int)Math.ceil((centerZ+margin)/CELL);
        for(int iz=minZ;iz<=maxZ;iz++)for(int ix=minX;ix<=maxX;ix++){
            // Prefiltro antes de qualquer consulta ao noise do terreno.
            if(hash(ix*31+17,iz*37-29)>.47f)continue;
            float px=(ix+.5f)*CELL+(hash(ix*19+5,iz*13+71)-.5f)*CELL*.62f;
            float pz=(iz+.5f)*CELL+(hash(ix*23-17,iz*41+29)-.5f)*CELL*.62f;
            float dx=px-centerX,dz=pz-centerZ;
            if(dx*dx+dz*dz>margin*margin || px*px+pz*pz<95f*95f)continue;
            float height=World.height(px,pz);
            boolean high=height>95f;
            boolean plains=height<74f;
            if(!high&&!plains)continue;
            // Topos de montanha e planicies, fora de paredoes.
            float sx=Math.abs(World.height(px+5f,pz)-World.height(px-5f,pz))/10f;
            float sz=Math.abs(World.height(px,pz+5f)-World.height(px,pz-5f))/10f;
            float slope=(float)Math.hypot(sx,sz);
            if(slope>(high?.55f:.38f))continue;
            if(plains && World.forestDensity(px,pz)>.22f)continue;
            if(hash(ix*43-7,iz*59+23)>(high?.62f:.45f))continue;

            // Altura do modelo 6,57 unidades; 8,75x ~57,5m (1,7x pinheiro medio).
            float scale=8.75f*(.95f+.13f*hash(ix*61+11,iz*67+3));
            float angle=hash(ix*71+19,iz*73-13)*6.2831853f;
            int kind=Math.min(2,(int)(hash(ix*7+101,iz*11-97)*3f));
            out.add(new Placement(px,height+scale*.54f,pz,scale,angle,kind));

            // 9% das formacoes tem apenas uma companheira, menor e com giro diferente.
            if(hash(ix*89-37,iz*97+13)<.09f){
                float companionAngle=angle+1.6f+2.6f*hash(ix*37+17,iz*41+5);
                float separation=10f+5f*hash(ix*59-7,iz*31+9);
                float xx=px+(float)Math.cos(companionAngle)*separation;
                float zz=pz+(float)Math.sin(companionAngle)*separation;
                float yy=World.height(xx,zz);
                float secondScale=scale*(.76f+.15f*hash(ix*47+3,iz*17+71));
                int kind2=(kind+1+(hash(ix*31,iz*43)>.5f?1:0))%3;
                float rotation2=angle+1.25f+2.4f*hash(ix*17-3,iz*19+17);
                out.add(new Placement(xx,yy+secondScale*.54f,zz,secondScale,rotation2,kind2));
            }
        }
        return out.toArray(new Placement[0]);
    }

    private static final class Builder {
        float[] data=new float[64];int n;
        void add(Placement p){
            if(n+8>data.length)data=Arrays.copyOf(data,data.length*2);
            data[n++]=p.x;data[n++]=p.y;data[n++]=p.z;data[n++]=p.scale;
            data[n++]=p.cos;data[n++]=p.sin;data[n++]=p.shade;data[n++]=p.tilt;
        }
        float[] finish(){return Arrays.copyOf(data,n);}
    }

    void draw(int program,Placement[] source,float eyeX,float eyeZ,float fx,float fz){
        if(source==null||source.length==0)return;
        float fl=(float)Math.hypot(fx,fz);
        if(fl>.0001f){fx/=fl;fz/=fl;}
        boolean refresh=source!=lastSource||Math.abs(eyeX-lastX)>7f||Math.abs(eyeZ-lastZ)>7f||
            fx*lastFx+fz*lastFz<.988f;
        if(refresh){
            Builder[][] output=new Builder[3][4];
            for(int type=0;type<3;type++)for(int level=0;level<4;level++)output[type][level]=new Builder();
            for(Placement p:source){
                float dx=p.x-eyeX,dz=p.z-eyeZ;
                float ds=dx*dx+dz*dz;
                if(ds>RANGE*RANGE)continue;
                float distance=(float)Math.sqrt(ds);
                if(distance>45f && dx*fx+dz*fz<-distance*.18f)continue;
                output[p.kind][lod(distance)].add(p);
            }
            for(int type=0;type<3;type++)for(int level=0;level<4;level++)
                buckets[type][level]=output[type][level].finish();
            lastSource=source;lastX=eyeX;lastZ=eyeZ;lastFx=fx;lastFz=fz;
        }
        for(int type=0;type<3;type++)for(int level=0;level<4;level++){
            TreeModel m=models[type][level];float[] group=buckets[type][level];
            if(m!=null&&group!=null&&group.length>0)m.draw(program,group);
        }
    }

    /** Colisao simplificada de troncos (sem colisores distantes). */
    static void collide(float[] point,float playerRadius,Placement[] source){
        if(source==null)return;
        float x=point[0],z=point[1];
        for(Placement t:source){
            float dx=x-t.x,dz=z-t.z;
            if(Math.abs(dx)>8f||Math.abs(dz)>8f)continue;
            float radius=playerRadius+Math.min(4.5f,t.scale*.40f);
            float d2=dx*dx+dz*dz;
            if(d2>=radius*radius)continue;
            float d=(float)Math.sqrt(d2);
            if(d<.001f){dx=radius;dz=0f;d=radius;}
            x=t.x+dx*radius/d;z=t.z+dz*radius/d;
        }
        point[0]=x;point[1]=z;
    }
}
