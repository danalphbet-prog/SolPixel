package com.solpixel.game;

import java.util.ArrayList;
import java.util.Arrays;

/** Relevo e biomas deterministas. Coordenadas globais estaveis em todos os chunks. */
public final class World {
    static final int SAMPLES = 105;       // 105x105: custo reduzido; horizonte via skybox
    static final float STEP = 3f; // compatibilidade com versoes anteriores
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
        // Terreno de frequencias baixas, com topo arredondado em vez de "agulhas".
        float wx=x+noise(x*.0025f+7,z*.0025f+9)*43f;
        float wz=z+noise(x*.0025f-8,z*.0025f+27)*43f;
        float continent=noise(wx*.0020f+58,wz*.0020f-43);
        float mountain=smooth(.02f,.46f,continent);
        float plains=1f-smooth(-.39f,-.07f,continent);
        float hills=noise(wx*.007f,wz*.007f)*7.5f + noise(wx*.022f+31,wz*.022f-29)*2.1f;
        float dome=.5f+.5f*noise(wx*.0048f+40,wz*.0048f+33);
        dome=smooth(.18f,.95f,dome);
        float mountainTop=mountain*(13f+84f*dome+8f*noise(wx*.011f,wz*.011f));
        float plain=(noise(wx*.012f-41,wz*.012f+19)*.24f);
        float result=(1f-plains)*(hills+mountainTop)+plains*plain;
        float spawn=1f-smooth(35,88,(float)Math.hypot(x,z));
        return result*(1-spawn)+.15f*spawn;
    }
    /** Densidade multi-escala: manchas extensas, microclareiras e bordas orgânicas.
     *  Nao existe um contador de arvores por cluster: cada ponto e aceito pela mascara. */
    static float forestDensity(float x,float z){
        float wx=x+noise(x*.0029f+31,z*.0029f-22)*66f;
        float wz=z+noise(x*.0029f-71,z*.0029f+17)*66f;
        float region=noise(wx*.0048f+19,wz*.0048f-83)*.78f +
                     noise(wx*.014f-6,wz*.014f+9)*.22f;
        float canopy=smooth(-.32f,.15f,region);
        float smaller=noise(wx*.025f+29,wz*.025f-31);
        canopy*=.62f+.38f*smooth(-.43f,.28f,smaller);
        // Vazios amplos que surgem mesmo no interior da floresta.
        float gaps=smooth(-.44f,-.17f,noise(wx*.011f+93,wz*.011f+21));
        float slope=Math.abs(height(x+3,z)-height(x-3,z))+
                    Math.abs(height(x,z+3)-height(x,z-3));
        float altitude=1f-smooth(60,105,height(x,z));
        float steep=1f-smooth(6,18,slope);
        float spawn=smooth(33,90,(float)Math.hypot(x,z));
        return Math.max(0,Math.min(1,canopy*gaps*steep*altitude*spawn));
    }
    static float pineMask(float x,float z){
        float warp=noise(x*.0038f+114,z*.0038f-41)*38f;
        return smooth(-.24f,.28f,noise((x+warp)*.0028f+184,(z-warp)*.0028f-71));
    }
    static Mesh ground(float cx,float cz){return ground(cx,cz,156f);}
    static Mesh ground(float cx,float cz,float radius){
        float step=(radius*2f)/(SAMPLES-1);
        float left=cx-radius,top=cz-radius;
        FloatList v=new FloatList();ShortList ids=new ShortList();
        for(int z=0;z<SAMPLES;z++)for(int x=0;x<SAMPLES;x++){
            float wx=left+x*step,wz=top+z*step,h=height(wx,wz);
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
    static Decoration decorate(float cx,float cz){return decorate(cx,cz,156f);}
    static Decoration decorate(float cx,float cz,float radius){
        FloatList v=new FloatList(),shadows=new FloatList(),broad=new FloatList(),pine=new FloatList();
        ShortList ids=new ShortList(),shadowIds=new ShortList();
        ArrayList<Tree> trees=new ArrayList<>();
        // Limite visual proprio para arvores; distancia do solo configuravel em separado.
        float range=Math.min(radius-12f,236f);
        final float spacing=8.6f;
        int minX=(int)Math.floor((cx-range)/spacing)-1,maxX=(int)Math.ceil((cx+range)/spacing)+1;
        int minZ=(int)Math.floor((cz-range)/spacing)-1,maxZ=(int)Math.ceil((cz+range)/spacing)+1;
        for(int iz=minZ;iz<=maxZ;iz++)for(int ix=minX;ix<=maxX;ix++){
            // Jitter espacial determinista: distribui naturalmente sem padrao de grade.
            float px=(ix+.5f)*spacing+hash(ix*17+33,iz*31-22)*spacing*.40f;
            float pz=(iz+.5f)*spacing+hash(ix*19-31,iz*23+52)*spacing*.40f;
            if(Math.abs(px-cx)>range||Math.abs(pz-cz)>range)continue;
            float density=forestDensity(px,pz);
            if(density<.13f)continue;
            float distance=(float)Math.hypot(px-cx,pz-cz);
            // LOD estocastico e estavel: menos copias nas manchas longinquas.
            float lod=1f-.63f*smooth(100,205,distance);
            float probability=density*.61f*lod;
            if(n01(ix*29+5,iz*37+9)>probability)continue;
            // Variacao por arvore, com ecotonos mistos em vez de linhas duras.
            float pineProbability=pineMask(px,pz);
            int kind=n01(ix*11+91,iz*13-17)<pineProbability?PINE:BROADLEAF;
            float py=height(px,pz);
            float base=(kind==PINE?.38f:.77f);
            float size=base*(.79f+n01(ix*31+7,iz*37+11)*.48f);
            if(kind==PINE)size*=1.20f; // pinheiros 20% maiores
            float radiusTrunk=(kind==PINE?1.60f:.52f)*size;
            float rotation=n01(ix*13+4,iz*13-2)*6.283185f;
            // Origem GLB = -2.6. Enterrar 3% da altura total de cada arvore.
            float modelHeight=(kind==PINE?53f:13.3f);
            float groundOffset=2.6f*size-.03f*modelHeight*size;
            Tree tree=new Tree(kind,px,py+groundOffset,pz,size,rotation,density,radiusTrunk);
            trees.add(tree);
            shadow(shadows,shadowIds,px,pz,(kind==PINE?10f:5f)*size,(kind==PINE?4f:2.7f)*size);
            FloatList dest=kind==PINE?pine:broad;
            dest.add(tree.x);dest.add(tree.y);dest.add(tree.z);dest.add(tree.scale);
            dest.add(tree.cos);dest.add(tree.sin);dest.add(density);dest.add(0);
        }
        // Pedras nao colidem, dispersas em locais abertos.
        final float rockGrid=39f;
        for(int rz=(int)Math.floor((cz-range)/rockGrid);rz<=(int)Math.ceil((cz+range)/rockGrid);rz++)
            for(int rx=(int)Math.floor((cx-range)/rockGrid);rx<=(int)Math.ceil((cx+range)/rockGrid);rx++){
                if(n01(rx*17+51,rz*23-31)<.86f)continue;
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
