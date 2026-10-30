package com.solpixel.game;

/**
 * Solas v3: horizonte 2500m com anel radial em LOD. A malha interna continua
 * controlada pela qualidade do jogo. Coordenadas e seeds globais impedem saltos
 * de arvores ao mover a camera; nao gera vegetacao fina fora da zona proxima.
 */
final class FarWorld {
    static final float HORIZON=2500f;
    private static final int SIDE_STEPS=32, EDGE=SIDE_STEPS*4, RINGS=30;
    final World.Mesh ground;
    final float[] normalTrees,giantTrees;
    private FarWorld(World.Mesh g,float[] n,float[] p){ground=g;normalTrees=n;giantTrees=p;}

    static FarWorld build(float cx,float cz,float nearRadius){
        World.FloatList verts=new World.FloatList();World.ShortList ids=new World.ShortList();
        for(int ring=0;ring<RINGS;ring++){
            float t=ring/(float)(RINGS-1);
            // Curva quadratica: muitos vertices perto da faixa de transicao.
            float radius=nearRadius+(HORIZON-nearRadius)*t*t;
            for(int k=0;k<EDGE;k++){
                int side=k/SIDE_STEPS,step=k%SIDE_STEPS;
                float f=step/(float)SIDE_STEPS;
                float ux,uz;
                if(side==0){ux=-1f+2f*f;uz=-1f;}
                else if(side==1){ux=1f;uz=-1f+2f*f;}
                else if(side==2){ux=1f-2f*f;uz=1f;}
                else{ux=-1f;uz=1f-2f*f;}
                float px=cx+ux*radius,pz=cz+uz*radius;
                float y=World.height(px,pz);
                float dx=World.height(px-3f,pz)-World.height(px+3f,pz);
                float dz=World.height(px,pz-3f)-World.height(px,pz+3f);
                float normalLength=(float)Math.sqrt(dx*dx+dz*dz+36f);
                float slope=(float)Math.hypot(dx,dz)/6f;
                float rock=TerrainGeology.rockExposure(px,pz,slope,y);
                World.vert(verts,px,y,pz,dx/normalLength,6f/normalLength,dz/normalLength,
                           px*.21f,pz*.21f,.14f,rock);
            }
        }
        for(int ring=0;ring<RINGS-1;ring++)for(int k=0;k<EDGE;k++){
            int next=ring*EDGE+(k+1)%EDGE;
            int a=ring*EDGE+k,b=(ring+1)*EDGE+k,c=(ring+1)*EDGE+(k+1)%EDGE;
            // Mesma orientacao das faces do World.ground original.
            ids.add(a);ids.add(b);ids.add(next);
            ids.add(next);ids.add(b);ids.add(c);
        }
        World.FloatList normal=new World.FloatList(),pine=new World.FloatList();
        // Densidade da silhueta decresce em tres bandas independentes, evitando
        // percorrer uma grade de 4.000m com o espacamento de 4.55m do original.
        fillTrees(cx,cz,nearRadius-16f,850f,31f,.69f,normal,pine);
        fillTrees(cx,cz,850f,1600f,53f,.75f,normal,pine);
        fillTrees(cx,cz,1600f,HORIZON,83f,.80f,normal,pine);
        return new FarWorld(new World.Mesh(verts.array(),ids.array()),normal.array(),pine.array());
    }
    private static void fillTrees(float cx,float cz,float minR,float maxR,float grid,float chance,
                                  World.FloatList normal,World.FloatList pine){
        int minX=(int)Math.floor((cx-maxR)/grid)-1,maxX=(int)Math.ceil((cx+maxR)/grid)+1;
        int minZ=(int)Math.floor((cz-maxR)/grid)-1,maxZ=(int)Math.ceil((cz+maxR)/grid)+1;
        for(int iz=minZ;iz<=maxZ;iz++)for(int ix=minX;ix<=maxX;ix++){
            float px=(ix+.5f+hash(ix*43,iz*37)*.34f)*grid;
            float pz=(iz+.5f+hash(ix*47,iz*29)*.34f)*grid;
            float vx=px-cx,vz=pz-cz;
            float ds=vx*vx+vz*vz;
            if(ds<minR*minR||ds>maxR*maxR)continue;
            if(TerrainGeology.slope(px,pz)>.90f)continue;
            float forest=World.forestDensity(px,pz);
            if(forest<.27f)continue;
            float odds=chance*(.15f+.85f*forest);
            if(unit(ix*83+17,iz*59-73)>odds)continue;
            boolean isPine=unit(ix*13+91,iz*17-37)<World.pineMask(px,pz);
            float size=(isPine?.38f*1.20f:.77f)*(1f+.80f*unit(ix*29+71,iz*31-61));
            float offset=2.6f*size-.03f*(isPine?53f:13.3f)*size;
            float angle=unit(ix*71+13,iz*79-51)*6.2831853f;
            World.FloatList dest=isPine?pine:normal;
            dest.add(px);dest.add(World.height(px,pz)+offset);dest.add(pz);dest.add(size);
            dest.add((float)Math.cos(angle));dest.add((float)Math.sin(angle));dest.add(forest);dest.add(0f);
        }
    }
    private static float hash(int x,int z){
        int n=x*374761393+z*668265263+0x143A76CF;
        n=(n^(n>>>13))*1274126177;
        return (((n^(n>>>16))&0x7fffffff)/1073741824f)-1f;
    }
    private static float unit(int x,int z){return .5f+.5f*hash(x,z);}
}
