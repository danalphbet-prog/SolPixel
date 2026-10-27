package com.solpixel.game;

import java.util.Arrays;

/** Terreno deterministico: as mesmas coordenadas produzem as mesmas montanhas e arvores. */
public final class World {
    static final int SAMPLES=145;
    static final float STEP=2f;
    private static final float SUN_X=.53f, SUN_Z=-.47f;
    static class Mesh {
        final float[] vertices;
        final short[] indices;
        Mesh(float[] v,short[] i){vertices=v;indices=i;}
    }
    static class FloatList {
        float[] a=new float[40000]; int n;
        void add(float v){if(n==a.length)a=Arrays.copyOf(a,a.length*2);a[n++]=v;}
        float[] array(){return Arrays.copyOf(a,n);}
    }
    static class ShortList {
        short[] a=new short[24000]; int n;
        void add(int v){if(n==a.length)a=Arrays.copyOf(a,a.length*2);a[n++]=(short)v;}
        short[] array(){return Arrays.copyOf(a,n);}
    }
    private static float hash(int x,int z){
        int n=x*374761393+z*668265263+0x165667B1;
        n=(n^(n>>>13))*1274126177;
        return ((n^(n>>>16))&0x7fffffff)/1073741824f-1f;
    }
    private static float noise(float x,float z){
        int xi=(int)Math.floor(x), zi=(int)Math.floor(z);
        float a=x-xi,b=z-zi;
        a=a*a*(3-2*a); b=b*b*(3-2*b);
        float v0=hash(xi,zi)*(1-a)+hash(xi+1,zi)*a;
        float v1=hash(xi,zi+1)*(1-a)+hash(xi+1,zi+1)*a;
        return v0*(1-b)+v1*b;
    }
    static float height(float x,float z){
        float large=noise(x*.0115f,z*.0115f)*8.2f;
        float medium=noise(x*.032f+43,z*.032f-72)*2.5f;
        float tiny=noise(x*.082f-12,z*.082f+23)*.65f;
        return large+medium+tiny;
    }
    static Mesh ground(float cx,float cz){
        float left=cx-(SAMPLES-1)*STEP*.5f;
        float top=cz-(SAMPLES-1)*STEP*.5f;
        FloatList verts=new FloatList(); ShortList indices=new ShortList();
        for(int z=0;z<SAMPLES;z++) for(int x=0;x<SAMPLES;x++){
            float wx=left+x*STEP,wz=top+z*STEP, h=height(wx,wz);
            float dx=height(wx-1,wz)-height(wx+1,wz);
            float dz=height(wx,wz-1)-height(wx,wz+1);
            float len=(float)Math.sqrt(dx*dx+dz*dz+4);
            vert(verts,wx,h,wz,dx/len,2/len,dz/len,wx*.21f,wz*.21f,0);
        }
        for(int z=0;z<SAMPLES-1;z++)for(int x=0;x<SAMPLES-1;x++){
            int a=z*SAMPLES+x,b=a+SAMPLES;
            indices.add(a);indices.add(b);indices.add(a+1);
            indices.add(a+1);indices.add(b);indices.add(b+1);
        }
        return new Mesh(verts.array(),indices.array());
    }
    static final class Decoration {
        Mesh objects,shadows;
        Decoration(Mesh o,Mesh s){objects=o;shadows=s;}
    }
    static Decoration decorate(float cx,float cz){
        FloatList verts=new FloatList(), shadows=new FloatList();
        ShortList tris=new ShortList(), shadowTris=new ShortList();
        float r=(SAMPLES-1)*STEP*.5f-12f;
        int minX=(int)Math.floor((cx-r)/17f), maxX=(int)Math.ceil((cx+r)/17f);
        int minZ=(int)Math.floor((cz-r)/17f), maxZ=(int)Math.ceil((cz+r)/17f);
        for(int iz=minZ;iz<=maxZ;iz++)for(int ix=minX;ix<=maxX;ix++){
            float chance=(hash(ix*17+7,iz*23+8)+1)*.5f;
            float px=ix*17+hash(ix+4,iz+17)*5.2f;
            float pz=iz*17+hash(ix+34,iz+2)*5.2f;
            if(Math.abs(px-cx)>r||Math.abs(pz-cz)>r)continue;
            if(px*px+pz*pz<235f) continue; // clareira inicial
            float py=height(px,pz);
            if(chance>.33f && chance<.86f){
                float size=.83f+(hash(ix+126,iz+39)+1)*.24f;
                // sombra no sentido oposto ao sol
                shadow(shadows,shadowTris,px, pz, 2.7f*size,1.2f*size);
                box(verts,tris,px-.18f*size,py,pz-.18f*size,px+.18f*size,py+2.15f*size,pz+.18f*size,1);
                box(verts,tris,px-1.10f*size,py+1.75f*size,pz-1.10f*size,
                    px+1.10f*size,py+3.0f*size,pz+1.10f*size,2);
                box(verts,tris,px-.74f*size,py+2.78f*size,pz-.74f*size,
                    px+.74f*size,py+3.75f*size,pz+.74f*size,4);
            } else if(chance>.91f){
                float rr=.45f+(hash(ix+72,iz+1)+1)*.5f;
                box(verts,tris,px-rr,py-.20f,pz-rr,px+rr,py+rr*.9f,pz+rr,3);
            }
        }
        return new Decoration(new Mesh(verts.array(),tris.array()),new Mesh(shadows.array(),shadowTris.array()));
    }
    static void vert(FloatList v,float x,float y,float z,float nx,float ny,float nz,float u,float w,float mat){
        v.add(x);v.add(y);v.add(z);v.add(nx);v.add(ny);v.add(nz);v.add(u);v.add(w);v.add(mat);
    }
    static void face(FloatList v,ShortList ids,float[] corners,float[] n,float mat){
        int base=v.n/9;
        vert(v,corners[0],corners[1],corners[2],n[0],n[1],n[2],0,0,mat);
        vert(v,corners[3],corners[4],corners[5],n[0],n[1],n[2],1,0,mat);
        vert(v,corners[6],corners[7],corners[8],n[0],n[1],n[2],1,1,mat);
        vert(v,corners[9],corners[10],corners[11],n[0],n[1],n[2],0,1,mat);
        ids.add(base);ids.add(base+1);ids.add(base+2);
        ids.add(base);ids.add(base+2);ids.add(base+3);
    }
    static void box(FloatList v,ShortList id,float x0,float y0,float z0,float x1,float y1,float z1,float mat){
        face(v,id,new float[]{x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1},new float[]{0,0,1},mat);
        face(v,id,new float[]{x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0},new float[]{0,0,-1},mat);
        face(v,id,new float[]{x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0},new float[]{-1,0,0},mat);
        face(v,id,new float[]{x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1},new float[]{1,0,0},mat);
        face(v,id,new float[]{x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0},new float[]{0,1,0},mat);
        face(v,id,new float[]{x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1},new float[]{0,-1,0},mat);
    }
    static void shadow(FloatList v,ShortList ids,float x,float z,float length,float width){
        // Quad atravessando o solo; amostragem de altura dos quatro cantos.
        float dx=-SUN_X*length,dz=-SUN_Z*length;
        float sideX=-dz/length*width,sideZ=dx/length*width;
        int base=v.n/9;
        float[] ax={x+sideX,z+sideZ};
        float[] bx={x-sideX,z-sideZ};
        float[] cx={x+dx-sideX,z+dz-sideZ};
        float[] dd={x+dx+sideX,z+dz+sideZ};
        for(float[] a:new float[][]{ax,bx,cx,dd})
            vert(v,a[0],height(a[0],a[1])+.085f,a[1],0,1,0,0,0,5);
        ids.add(base);ids.add(base+1);ids.add(base+2);
        ids.add(base);ids.add(base+2);ids.add(base+3);
    }
}
