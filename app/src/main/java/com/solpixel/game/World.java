package com.solpixel.game;

import java.util.ArrayList;
import java.util.Arrays;

/** Relevo e biomas deterministas. Coordenadas globais estaveis em todos os chunks. */
public final class World {
    static final int SAMPLES = 105;       // 105x105: custo reduzido; horizonte via skybox
    static final float STEP = 3f;
    private static final float SUN_X=.53f, SUN_Z=-.47f;
    static final int BROADLEAF=0, PINE=1;
    static class Mesh {
        final float[] vertices; final short[] indices;
        Mesh(float[] v,short[] i){vertices=v;indices=i;}
    }
    static class FloatList {
        float[] a=new float[32000]; int n;
        void add(float v){if(n==a.length)a=Arrays.copyOf(a,a.length*2);a[n++]=v;}
        float[] array(){return Arrays.copyOf(a,n);}
    }
    static class ShortList {
        short[] a=new short[32000]; int n;
        void add(int v){if(n==a.length)a=Arrays.copyOf(a,a.length*2);a[n++]=(short)v;}
        short[] array(){return Arrays.copyOf(a,n);}
    }
    private static float hash(int x,int z){
        int n=x*374761393+z*668265263+0x165667B1;
        n=(n^(n>>>13))*1274126177;
        return ((n^(n>>>16))&0x7fffffff)/1073741824f-1f;
    }
    private static float n01(int x,int y){return .5f+.5f*hash(x,y);}
    private static float noise(float x,float z){
        int xi=(int)Math.floor(x),zi=(int)Math.floor(z);
        float a=x-xi,b=z-zi;a=a*a*(3-2*a);b=b*b*(3-2*b);
        return (hash(xi,zi)*(1-a)+hash(xi+1,zi)*a)*(1-b)+
               (hash(xi,zi+1)*(1-a)+hash(xi+1,zi+1)*a)*b;
    }
    private static float smooth(float a,float b,float x){
        float t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);
    }
    static float height(float x,float z){
        // Domain warping evita colinas quadriculadas; continentes combinam tres macrozonas.
        float wx=x+noise(x*.003f+7,z*.003f+9)*48f;
        float wz=z+noise(x*.003f-8,z*.003f+27)*48f;
        float macro=noise(wx*.0027f+58,wz*.0027f-43);
        float mountain=smooth(.08f,.46f,macro);
        float plain=1f-smooth(-.45f,-.09f,macro);
        float hill=noise(wx*.009f,wz*.009f)*9f +noise(wx*.028f+31,wz*.028f-29)*2.5f;
        float ridged=1f-Math.abs(noise(wx*.008f+40,wz*.008f+33));
        ridged=(float)Math.pow(ridged,2.6);
        float peaks=mountain*(26f+ridged*88f+noise(wx*.018f,wz*.018f)*13f);
        float flat=noise(wx*.018f-41,wz*.018f+19)*.30f;
        float h=(1f-plain)*(hill+peaks)+plain*flat;
        // Planicie inicial realmente plana, com transicao gradual.
        float start=1f-smooth(32,75,(float)Math.hypot(x,z));
        return h*(1-start)+.15f*start;
    }
    /** Densidade varia nas bordas, com vazios naturais, clareiras e teto de altitude. */
    static float forestDensity(float x,float z){
        float n=noise(x*.0062f+19,z*.0062f-83)*.72f +noise(x*.025f-6,z*.025f+9)*.28f;
        float edge=smooth(-.20f,.30f,n);
        float clearing=1f-smooth(28,64,(float)Math.hypot(x,z));
        float holes=smooth(-.38f,-.12f,noise(x*.012f+93,z*.012f+21));
        float altitude=1f-smooth(32,85,height(x,z));
        return Math.max(0f,Math.min(1f,edge*holes*altitude*(1f-clearing)));
    }
    /** >0.5 designa o bioma de coniferas; macromascara muda suavemente. */
    static float pineMask(float x,float z){
        return smooth(-.12f,.40f,noise(x*.0038f+184,z*.0038f-71));
    }
    static Mesh ground(float cx,float cz){
        float left=cx-(SAMPLES-1)*STEP*.5f,top=cz-(SAMPLES-1)*STEP*.5f;
        FloatList v=new FloatList();ShortList ids=new ShortList();
        for(int z=0;z<SAMPLES;z++)for(int x=0;x<SAMPLES;x++){
            float wx=left+x*STEP,wz=top+z*STEP,h=height(wx,wz);
            float dx=height(wx-1,wz)-height(wx+1,wz);
            float dz=height(wx,wz-1)-height(wx,wz+1);
            float len=(float)Math.sqrt(dx*dx+dz*dz+4);
            // Fracao do atributo material codifica o sombreamento de vegetacao.
            vert(v,wx,h,wz,dx/len,2/len,dz/len,wx*.21f,wz*.21f,forestDensity(wx,wz)*.95f);
        }
        for(int z=0;z<SAMPLES-1;z++)for(int x=0;x<SAMPLES-1;x++){
            int a=z*SAMPLES+x,b=a+SAMPLES;
            ids.add(a);ids.add(b);ids.add(a+1);
            ids.add(a+1);ids.add(b);ids.add(b+1);
        }
        return new Mesh(v.array(),ids.array());
    }
    static class Tree {
        float x,y,z,scale,cos,sin,shade,radius;
        int kind;
        Tree(int kind,float x,float y,float z,float scale,float rotation,float shade,float radius){
            this.kind=kind;this.x=x;this.y=y;this.z=z;this.scale=scale;
            this.cos=(float)Math.cos(rotation);this.sin=(float)Math.sin(rotation);
            this.shade=shade;this.radius=radius;
        }
    }
    static final class Decoration {
        Mesh objects,shadows;
        Tree[] trees;
        float[] broadleafInstances,pineInstances;
        Decoration(Mesh o,Mesh s,Tree[] trees,float[] broad,float[] pine){
            this.objects=o;this.shadows=s;this.trees=trees;
            broadleafInstances=broad;pineInstances=pine;
        }
    }
    static Decoration decorate(float cx,float cz){
        FloatList v=new FloatList(),shadows=new FloatList(),broad=new FloatList(),pine=new FloatList();
        ShortList ids=new ShortList(),shadowIds=new ShortList();
        ArrayList<Tree> trees=new ArrayList<>();
        float range=(SAMPLES-1)*STEP*.5f-10f;
        final float grid=38f;
        int minX=(int)Math.floor((cx-range-15)/grid),maxX=(int)Math.ceil((cx+range+15)/grid);
        int minZ=(int)Math.floor((cz-range-15)/grid),maxZ=(int)Math.ceil((cz+range+15)/grid);
        // Cada celula pode conter um pequeno bosque de 4-6 arvores.
        for(int iz=minZ;iz<=maxZ;iz++)for(int ix=minX;ix<=maxX;ix++){
            float coreX=(ix+.5f)*grid+hash(ix+33,iz-22)*7;
            float coreZ=(iz+.5f)*grid+hash(ix-31,iz+52)*7;
            float density=forestDensity(coreX,coreZ);
            if(density<.32f||n01(ix*7,iz*11) > density*.80f)continue;
            int kind=pineMask(coreX,coreZ)>.55f?PINE:BROADLEAF;
            int count=4+(int)(n01(ix*21+4,iz*9-9)*3f);  // 4,5,6
            for(int i=0;i<count;i++){
                float angle=(float)(i*2.39996323+n01(ix+89,iz+43)*6.28318);
                float dist=2.9f+(i%3)*2.8f+n01(ix*19+i,iz*21-i)*2.4f;
                float px=coreX+(float)Math.cos(angle)*dist;
                float pz=coreZ+(float)Math.sin(angle)*dist;
                if(Math.abs(px-cx)>range||Math.abs(pz-cz)>range)continue;
                float local=forestDensity(px,pz);
                if(local<.32f)continue;
                float py=height(px,pz);
                if(Math.abs(height(px+1.5f,pz)-height(px-1.5f,pz))>3.1f ||
                   Math.abs(height(px,pz+1.5f)-height(px,pz-1.5f))>3.1f)continue;
                float size=(kind==PINE?.38f:.77f)*( .87f +n01(ix*31+i,iz*37+i)*.33f);
                float radius=(kind==PINE?2.20f:.52f)*size;
                float rotation=n01(ix*13+i*4,iz*13-i*2)*6.28318f;
                // GLB tem tronco minimo em y=-2.6.
                Tree tree=new Tree(kind,px,py+2.6f*size,pz,size,rotation,local,radius);
                trees.add(tree);
                // Escurecimento de contato; plano no solo segue o relevo.
                shadow(shadows,shadowIds,px,pz,(kind==PINE?6f:4.5f)*size,(kind==PINE?3f:2.6f)*size);
                FloatList dest=kind==PINE?pine:broad;
                dest.add(tree.x);dest.add(tree.y);dest.add(tree.z);dest.add(tree.scale);
                dest.add(tree.cos);dest.add(tree.sin);dest.add(local);dest.add(0);
            }
        }
        // Pedras decorativas sem colisao, espalhadas sobretudo fora das florestas.
        final float rockGrid=34f;
        for(int rz=(int)Math.floor((cz-range)/rockGrid);rz<=(int)Math.ceil((cz+range)/rockGrid);rz++)
            for(int rx=(int)Math.floor((cx-range)/rockGrid);rx<=(int)Math.ceil((cx+range)/rockGrid);rx++){
                if(n01(rx*17+51,rz*23-31)<.82f)continue;
                float px=(rx+.5f)*rockGrid+hash(rx+77,rz-71)*10f;
                float pz=(rz+.5f)*rockGrid+hash(rx-44,rz+59)*10f;
                if(Math.abs(px-cx)>range||Math.abs(pz-cz)>range)continue;
                float size=.5f+n01(rx*29,rz*37)*1.25f,py=height(px,pz);
                box(v,ids,px-size,py-.18f,pz-size*.8f,px+size,py+size*.68f,pz+size*.8f,3);
            }
        return new Decoration(new Mesh(v.array(),ids.array()),new Mesh(shadows.array(),shadowIds.array()),
               trees.toArray(new Tree[0]),broad.array(),pine.array());
    }
    /** Resolve apenas 6 troncos proximos. Isso evita colisao com floresta distante. */
    static void collide(float[] position,float radius,Decoration decoration){
        if(decoration==null)return;
        float px=position[0],pz=position[1];
        Tree[] nearest=new Tree[6];float[] distances={999,999,999,999,999,999};
        for(Tree t:decoration.trees){
            float dx=px-t.x,dz=pz-t.z,ds=dx*dx+dz*dz;
            if(ds>64f)continue;
            for(int i=0;i<6;i++)if(ds<distances[i]){
                for(int j=5;j>i;j--){distances[j]=distances[j-1];nearest[j]=nearest[j-1];}
                distances[i]=ds;nearest[i]=t;break;
            }
        }
        for(Tree t:nearest)if(t!=null){
            float dx=px-t.x,dz=pz-t.z,limit=radius+t.radius,ds=dx*dx+dz*dz;
            if(ds<limit*limit){
                float d=(float)Math.sqrt(ds);
                if(d<.001f){dx=limit;dz=0;d=limit;}
                px=t.x+dx/d*limit;pz=t.z+dz/d*limit;
            }
        }
        position[0]=px;position[1]=pz;
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
        float dx=-SUN_X*length,dz=-SUN_Z*length;
        float sideX=-dz/length*width,sideZ=dx/length*width;
        int base=v.n/9;
        float[][] corners={{x+sideX,z+sideZ},{x-sideX,z-sideZ},{x+dx-sideX,z+dz-sideZ},{x+dx+sideX,z+dz+sideZ}};
        for(float[] a:corners)vert(v,a[0],height(a[0],a[1])+.085f,a[1],0,1,0,0,0,5);
        ids.add(base);ids.add(base+1);ids.add(base+2);
        ids.add(base);ids.add(base+2);ids.add(base+3);
    }
}
