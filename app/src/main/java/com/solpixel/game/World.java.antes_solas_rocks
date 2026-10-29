package com.solpixel.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/** Relevo e biomas deterministas. Coordenadas globais estaveis em todos os chunks. */
public final class World {
    static final int SAMPLES = 129; // solpixel-v06       // 129x129: terreno distante, horizonte azul
    static final float STEP = 3f; // compatibilidade com versoes anteriores
    private static final float SUN_X=.53f, SUN_Z=-.47f;
    static final int BROADLEAF=0, PINE=1;
    // solpixel-v08: parametros procedurais fixos, nao expostos nas configuracoes.
    static final float TREE_DENSITY=1.60f;
    static final float CLUSTER_SCALE=1.65f;
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
        // Planicies, colinas baixas/altas e montanhas com cumes largos.
        float wx=x+noise(x*.0025f+7,z*.0025f+9)*39f;
        float wz=z+noise(x*.0025f-8,z*.0025f+27)*39f;
        float region=noise(wx*.00175f+58,wz*.00175f-43);
        float mountain=smooth(-.13f,.31f,region);
        float plains=1f-smooth(-.56f,-.28f,region);
        float lowHills=noise(wx*.008f,wz*.008f)*6.4f+
                       noise(wx*.019f+31,wz*.019f-29)*1.8f;
        float tallHillsMask=smooth(-.31f,.13f,noise(wx*.0039f-27,wz*.0039f+58));
        float tallHillsShape=smooth(.13f,.82f,.5f+.5f*noise(wx*.0051f+76,wz*.0051f-43));
        float tallHills=tallHillsMask*(4f+34f*tallHillsShape);
        float dome=smooth(.10f,.84f,.5f+.5f*noise(wx*.00255f+40,wz*.00255f+33));
        float mountainTop=mountain*(16f+106f*dome);
        float plain=noise(wx*.009f-41,wz*.009f+19)*.22f;
        float result=(1f-plains)*(lowHills+tallHills+mountainTop)+plains*plain;
        if(result>65f)result=65f+74f*(1f-(float)Math.exp(-(result-65f)/74f));
        // Macicos raros e largos que podem ultrapassar a camada de nuvens (~220-280m).
        // Soft ramp + noise lento evitam o formato de agulha / pico pontudo.
        float giantRegion=smooth(.31f,.61f,noise(wx*.00172f+317,wz*.00172f-229));
        float giantCrown=smooth(.03f,.79f,.5f+.5f*noise(wx*.00275f-141,wz*.00275f+211));
        result+=giantRegion*(63f+225f*giantCrown)*(1f-plains);
        float spawn=1f-smooth(35,88,(float)Math.hypot(x,z));
        return result*(1-spawn)+.15f*spawn;
    }
    /** Densidade multi-escala: manchas extensas, microclareiras e bordas orgânicas.
     *  Nao existe um contador de arvores por cluster: cada ponto e aceito pela mascara. */
    static float forestDensity(float x,float z){
        // Campos correlacionados, sem limite de arvores por cluster.
        // Noise continental = macicos extensos; ruido secundario = clareiras e bordas.
        float scale=Math.max(.65f,CLUSTER_SCALE);
        float bx=x/scale,bz=z/scale;
        float wx=bx+noise(bx*.0021f+31,bz*.0021f-22)*94f;
        float wz=bz+noise(bx*.0021f-71,bz*.0021f+17)*94f;
        float macro=noise(wx*.00325f+19,wz*.00325f-83)*.82f+
                    noise(wx*.009f-6,wz*.009f+9)*.18f;
        float mass=smooth(-.42f,.06f,macro);
        // Subgrupos aumentam o fechamento da copa em vez de criar pontos uniformes.
        float subgroup=smooth(-.62f,.14f,noise(wx*.018f+29,wz*.018f-31));
        // Clareiras grandes e medias em florestas densas.
        float openings=smooth(-.42f,-.18f,noise(wx*.009f+93,wz*.009f+21));
        float wideClear=1f-smooth(.29f,.52f,noise(wx*.0044f-63,wz*.0044f+115));
        float smallClear=1f-.90f*smooth(.34f,.58f,noise(wx*.018f+61,wz*.018f-34));
        float coverage=mass*(.70f+.30f*subgroup)*openings*wideClear*smallClear;
        float h=height(x,z);
        float slope=Math.abs(height(x+3,z)-height(x-3,z))+
                    Math.abs(height(x,z+3)-height(x,z-3));
        float altitude=1f-smooth(76,120,h);
        float steep=1f-smooth(9,23,slope);
        float spawn=smooth(34,83,(float)Math.hypot(x,z));
        return Math.max(0,Math.min(1,coverage*steep*altitude*spawn));
    }
    static float pineMask(float x,float z){
        // Pinheirais de centenas de metros: um bioma predominante por macico.
        float bx=x/Math.max(.65f,CLUSTER_SCALE),bz=z/Math.max(.65f,CLUSTER_SCALE);
        float warp=noise(bx*.0020f+114,bz*.0020f-41)*88f;
        float field=noise((bx+warp)*.00165f+184,(bz-warp)*.00165f-71)*.79f+
                    noise(bx*.00365f-31,bz*.00365f+14)*.21f;
        // Faixa mista apenas nas bordas (ecotono); interior quase monoespecifico.
        return smooth(-.095f,.095f,field);
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
        final HashMap<Long,Tree[]> collisionCells=new HashMap<>();
        float[] broadleafInstances,pineInstances,grassInstances;
        Decoration(Mesh o,Mesh s,Tree[] trees,float[] broad,float[] pine,float[] grass){
            this.objects=o;this.shadows=s;this.trees=trees;
            broadleafInstances=broad;pineInstances=pine;grassInstances=grass;
            // Grade espacial construida no worker: colisao O(arvores proximas).
            HashMap<Long,ArrayList<Tree>> cells=new HashMap<>();
            for(Tree t:trees){
                int gx=(int)Math.floor(t.x/14f),gz=(int)Math.floor(t.z/14f);
                long key=cellKey(gx,gz);
                ArrayList<Tree> list=cells.get(key);
                if(list==null){list=new ArrayList<>();cells.put(key,list);}
                list.add(t);
            }
            for(java.util.Map.Entry<Long,ArrayList<Tree>> e:cells.entrySet())
                collisionCells.put(e.getKey(),e.getValue().toArray(new Tree[0]));
        }
    }
    static Decoration decorate(float cx,float cz){return decorate(cx,cz,156f);}
    static Decoration decorate(float cx,float cz,float radius){
        FloatList v=new FloatList(),shadows=new FloatList(),broad=new FloatList(),pine=new FloatList();
        ShortList ids=new ShortList(),shadowIds=new ShortList();
        ArrayList<Tree> trees=new ArrayList<>();
        // O limite acompanha a distancia configurada, sem teto antigo de 236m.
        float range=Math.max(48f,radius-12f);
        // Geracao global independente da distancia ao jogador.
        final float spacing=4.55f;
        int minX=(int)Math.floor((cx-range)/spacing)-1,maxX=(int)Math.ceil((cx+range)/spacing)+1;
        int minZ=(int)Math.floor((cz-range)/spacing)-1,maxZ=(int)Math.ceil((cz+range)/spacing)+1;
        float densityControl=Math.max(.60f,Math.min(2.40f,TREE_DENSITY));
        for(int iz=minZ;iz<=maxZ;iz++)for(int ix=minX;ix<=maxX;ix++){
            float px=(ix+.5f)*spacing+hash(ix*17+33,iz*31-22)*spacing*.43f;
            float pz=(iz+.5f)*spacing+hash(ix*19-31,iz*23+52)*spacing*.43f;
            float dx=px-cx,dz=pz-cz,dist2=dx*dx+dz*dz;
            if(Math.abs(dx)>range||Math.abs(dz)>range||dist2>range*range)continue;
            float distance=(float)Math.sqrt(dist2);
            // Nao afinar as arvores conforme o player se aproxima.
            float density=forestDensity(px,pz);
            if(density<.11f)continue;
            float core=smooth(.16f,.74f,density);
            float probability=Math.min(.96f,(.18f+.82f*core)*densityControl);
            probability*=smooth(.07f,.32f,density);
            // solpixel-v08: 20% menos arvores que a floresta padrao da v0.7.
            probability*=.80f;
            if(n01(ix*29+5,iz*37+9)>probability)continue;
            float pineProbability=pineMask(px,pz);
            int kind=n01(ix*11+91,iz*13-17)<pineProbability?PINE:BROADLEAF;
            // Variacao de escala de 1.0x ate 1.8x, com semente global estavel.
            float randomScale=1f+.8f*n01(ix*31+7,iz*37+11);
            float base=(kind==PINE?.38f:.77f);
            float size=base*(kind==PINE?1.20f:1f)*randomScale;
            float py=height(px,pz);
            float radiusTrunk=(kind==PINE?1.60f:.52f)*size+.18f*size;
            float rotation=n01(ix*13+4,iz*13-2)*6.283185f;
            // 50% inclinadas: angulo de 0 a 18 graus, sinal e eixo aleatorios.
            float tilt=0f;
            if(n01(ix*41-3,iz*53+17)<.50f){
                float degrees=18f*n01(ix*67+11,iz*71-23);
                float sign=n01(ix*31-91,iz*47+39)<.50f?-1f:1f;
                tilt=sign*degrees*.01745329252f;
            }
            float modelHeight=(kind==PINE?53f:13.3f);
            float groundOffset=2.6f*size-.03f*modelHeight*size;
            Tree tree=new Tree(kind,px,py+groundOffset,pz,size,rotation,density,radiusTrunk);
            trees.add(tree);
            // Sombras de objetos so perto; sombreado florestal do solo ja e procedural.
            if(distance<115f)shadow(shadows,shadowIds,px,pz,(kind==PINE?10f:5f)*size,
                (kind==PINE?4f:2.7f)*size);
            FloatList dest=kind==PINE?pine:broad;
            dest.add(tree.x);dest.add(tree.y);dest.add(tree.z);dest.add(tree.scale);
            dest.add(tree.cos);dest.add(tree.sin);dest.add(density);dest.add(tilt);
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
        // SolPixel vegetation v0.11: ilhas de grama, trigo em campos extensos,
        // samambaias em florestas e cogumelos proximos a troncos.
        // Toda a geracao ocorre no worker existente de World.decorate().
        FloatList grass=new FloatList();
        final float grassRadius=Math.min(range-8f,Math.max(145f,radius*.29f+72f));
        final float grid=2.25f;
        int minGX=(int)Math.floor((cx-grassRadius)/grid)-1;
        int maxGX=(int)Math.ceil((cx+grassRadius)/grid)+1;
        int minGZ=(int)Math.floor((cz-grassRadius)/grid)-1;
        int maxGZ=(int)Math.ceil((cz+grassRadius)/grid)+1;
        // Indice espacial para cogumelos: apenas arvores reais proximas (2-11 m).
        final float treeCell=16f;
        HashMap<Long,ArrayList<Tree>> nearTrees=new HashMap<>();
        for(Tree t:trees){
            int tx=(int)Math.floor(t.x/treeCell),tz=(int)Math.floor(t.z/treeCell);
            long key=cellKey(tx,tz);
            ArrayList<Tree> list=nearTrees.get(key);
            if(list==null){list=new ArrayList<>();nearTrees.put(key,list);}
            list.add(t);
        }
        for(int gz=minGZ;gz<=maxGZ;gz++)for(int gx=minGX;gx<=maxGX;gx++){
            float px=(gx+.5f)*grid+hash(gx*13+19,gz*17-37)*grid*.40f;
            float pz=(gz+.5f)*grid+hash(gx*37-11,gz*29+61)*grid*.40f;
            float dx=px-cx,dz=pz-cz;
            if(dx*dx+dz*dz>grassRadius*grassRadius)continue;
            float slope=Math.abs(height(px+1f,pz)-height(px-1f,pz))+
                        Math.abs(height(px,pz+1f)-height(px,pz-1f));
            if(slope>2.9f)continue;
            float elevation=height(px,pz);
            float forest=forestDensity(px,pz);
            float wood=smooth(.18f,.70f,forest);
            float open=1f-wood;
            // Mascaras em duas escalas: cada ilha tem centro denso e borda rala.
            float island=smooth(-.28f,.23f,noise(px*.014f+201,pz*.014f-97));
            float details=smooth(-.33f,.13f,noise(px*.066f-29,pz*.066f+183));
            float grassCoverage=island*(.30f+.70f*details);
            // Arquipelagos de trigo em regioes abertas; manchas com centenas de m.
            float wheatMacro=smooth(-.09f,.36f,noise(px*.0049f-155,pz*.0049f+177));
            float wheatIslands=smooth(-.33f,.13f,noise(px*.017f+55,pz*.017f-89));
            float wheatField=wheatMacro*(.58f+.42f*wheatIslands)*open;
            // Em campos de trigo, trigo supera grama; em prados, o contrario.
            float wheatChance=(.05f+.91f*wheatField)*open*smooth(.22f,.80f,wheatField);
            // Centro dos arquipelagos mais fechado, sem afetar os biomas de floresta.
            wheatChance=Math.min(.985f,wheatChance*1.28f);
            if(elevation>88f||slope>1.65f)wheatChance=0f;
            float grassChance=.10f+.78f*grassCoverage;
            grassChance*=1f-.77f*smooth(.29f,.80f,wheatField);
            // Dentro da floresta a grama fica 60% menos frequente.
            // A atenuacao progressiva evita limites bruscos entre biomas.
            grassChance*=1f-.60f*wood;
            // Samambaia 70% menos frequente nos campos e clareiras.
            float fernChance=.265f*(.30f+.70f*wood)*(.30f+.70f*island);
            // Flores raras, comparaveis em chance aos cogumelos por celula.
            float flowerChance=.027f*(.30f+.70f*open);
            // Cogumelos exclusivamente em floresta/borda, perto de troncos,
            // mas nunca colados na base da arvore.
            boolean nearTrunk=false;
            if(forest>.14f){
                int tx=(int)Math.floor(px/treeCell),tz=(int)Math.floor(pz/treeCell);
                for(int tz2=tz-1;tz2<=tz+1 && !nearTrunk;tz2++)
                    for(int tx2=tx-1;tx2<=tx+1 && !nearTrunk;tx2++){
                        ArrayList<Tree> candidates=nearTrees.get(cellKey(tx2,tz2));
                        if(candidates==null)continue;
                        for(Tree t:candidates){
                            float tdx=px-t.x,tdz=pz-t.z;
                            float dist2=tdx*tdx+tdz*tdz;
                            float minimum=t.radius+1.45f;
                            if(dist2>minimum*minimum && dist2<10.8f*10.8f){
                                nearTrunk=true;break;
                            }
                        }
                    }
            }
            float mushroomChance=nearTrunk?.028f*(.35f+.65f*wood):0f;
            int type=-1;
            if(n01(gx*73+51,gz*61-19)<wheatChance)type=9;
            else if(n01(gx*31+91,gz*47-59)<grassChance)type=(int)(n01(gx*37-7,gz*43+19)*3f);
            else if(n01(gx*29+27,gz*67-41)<fernChance)type=8;
            else if(n01(gx*53+7,gz*79-13)<flowerChance)type=3+(int)(n01(gx*17-13,gz*67+51)*3f);
            else if(n01(gx*83+13,gz*89-7)<mushroomChance)type=6+(int)(n01(gx*43-33,gz*41+7)*2f);
            if(type<0)continue;
            float size=.75f+n01(gx*73+11,gz*61+7)*.50f;
            if(type==8)size*=.50f; // Samambaia: 50% da grama.
            if(type==9)size*=1.42f; // Trigo alto.
            if(type>=6 && type<=7)size*=1.05f;
            float angle=n01(gx*17-19,gz*13+5)*6.2831853f;
            float variation=n01(gx*19+37,gz*11-23);
            addVegetation(grass,px,elevation,pz,size,angle,type,variation);
            // Vegetacao vizinha no centro das ilhas, formando conjuntos.
            // As bordas continuam mais vazias, sem interrupcao abrupta.
            float cluster=type==9?wheatField:grassCoverage;
            if((type<=2||type==9) && cluster>.38f &&
               n01(gx*71-39,gz*17+23)<.63f*cluster){
                float a2=n01(gx*11+55,gz*37-17)*6.2831853f;
                float separation=.30f+.42f*n01(gx*13-19,gz*31+7);
                float x2=px+(float)Math.cos(a2)*separation;
                float z2=pz+(float)Math.sin(a2)*separation;
                float y2=height(x2,z2);
                float s2=size*(.81f+.30f*n01(gx*29+31,gz*67-7));
                int species2=type==9?9:(int)(n01(gx*73-11,gz*83+19)*3f);
                addVegetation(grass,x2,y2,z2,s2,angle+a2*.3f,species2,variation);
            }
            // Mais hastes agrupadas nas ilhas de trigo, sem quota fixa de plantas.
            if(type==9 && wheatField>.45f){
                float fill=smooth(.45f,.90f,wheatField);
                for(int k=0;k<3;k++){
                    float odds=n01(gx*(43+8*k)+21,gz*(47+6*k)-51);
                    if(odds>(.58f+.34f*fill))continue;
                    float aa=n01(gx*(67+6*k)-17,gz*(37+6*k)+23)*6.2831853f;
                    float rr=.25f+(.34f+.16f*k)*n01(gx*(31+6*k)+7,gz*(53+4*k)-13);
                    float xx=px+(float)Math.cos(aa)*rr;
                    float zz=pz+(float)Math.sin(aa)*rr;
                    float ss=size*(.76f+.40f*n01(gx*(11+6*k),gz*(17+4*k)));
                    addVegetation(grass,xx,height(xx,zz),zz,ss,angle+aa*.27f,9,variation);
                }
            }
        }
        return new Decoration(new Mesh(v.array(),ids.array()),new Mesh(shadows.array(),shadowIds.array()),
               trees.toArray(new Tree[0]),broad.array(),pine.array(),grass.array());
    }
    /** Campo continuo 0..1 para aumentar poeira sobre trigo sem depender da camera. */
    static float wheatField(float x,float z){
        float forest=forestDensity(x,z);
        float open=1f-smooth(.18f,.70f,forest);
        float macro=smooth(-.09f,.36f,noise(x*.0049f-155,z*.0049f+177));
        float islands=smooth(-.33f,.13f,noise(x*.017f+55,z*.017f-89));
        return macro*(.58f+.42f*islands)*open;
    }
    private static void addVegetation(FloatList result,float x,float groundY,float z,
                                      float size,float angle,int type,float shade){
        // Alinha a origem dos sprites ao terreno, sem colisao ou fisica adicional.
        result.add(x);result.add(groundY-.045f*size);result.add(z);result.add(size);
        result.add((float)Math.cos(angle));result.add((float)Math.sin(angle));
        result.add(type);result.add(shade);
    }
    static long cellKey(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    /** Resolve apenas os 6 troncos mais proximos, com grade espacial 14m. */
    static void collide(float[] position,float radius,Decoration decoration){
        if(decoration==null)return;
        float px=position[0],pz=position[1];
        Tree[] nearest=new Tree[6];float[] distances={999,999,999,999,999,999};
        int gx=(int)Math.floor(px/14f),gz=(int)Math.floor(pz/14f);
        for(int zz=gz-1;zz<=gz+1;zz++)for(int xx=gx-1;xx<=gx+1;xx++){
            Tree[] local=decoration.collisionCells.get(cellKey(xx,zz));
            if(local==null)continue;
            for(Tree t:local){
                float dx=px-t.x,dz=pz-t.z,ds=dx*dx+dz*dz;
                if(ds>64f)continue;
                for(int i=0;i<6;i++)if(ds<distances[i]){
                    for(int j=5;j>i;j--){distances[j]=distances[j-1];nearest[j]=nearest[j-1];}
                    distances[i]=ds;nearest[i]=t;break;
                }
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
