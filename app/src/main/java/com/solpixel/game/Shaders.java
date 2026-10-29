package com.solpixel.game;

/** GLSL ES 3.0: nenhum pacote ou shader externo necessario. */
final class Shaders {
    static final String MESH_V = """
        #version 300 es
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec3 aNormal;
        layout(location=2) in vec2 aUv;
        layout(location=3) in float aMaterial;
        uniform mat4 uMvp;
        uniform mat4 uLightVP;
        uniform vec3 uEye;
        out vec4 vLightCoord;
        out vec3 vPos;
        out vec3 vNormal;
        out vec2 vUv;
        flat out int vMat;
        out float vForest;
        out float vDist;
        out float vPatch;
        float hashGround(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
        void main(){
            gl_Position=uMvp*vec4(aPos,1.0);
            vLightCoord=uLightVP*vec4(aPos,1.0);
            vPos=aPos;vNormal=aNormal;vUv=aUv;
            vMat=int(floor(aMaterial+0.001));
            vForest=fract(aMaterial);
            vPatch=mix(hashGround(floor(aPos.xz*.067)),
                       hashGround(floor(aPos.xz*.137+vec2(13.7,29.1))),.35);
            vDist=distance(aPos,uEye);
        }
        """;
    static final String MESH_F = """
        #version 300 es
        precision highp float;
        in vec3 vPos;
        in vec3 vNormal;
        in vec2 vUv;
        flat in int vMat;
        in float vForest;
        in float vDist;
        in float vPatch;
        in vec4 vLightCoord;
        uniform sampler2D uShadowMap;
        uniform float uShadowEnabled;
        uniform sampler2D uGround;
        uniform sampler2D uTerrainDirt,uTerrainMiddle,uTerrainGrass;
        uniform float uHasTerrainTextures;
        uniform sampler2D uRockSurfaceTex;
        uniform float uHasRockSurface;
        uniform vec3 uGrassDayTint;
        uniform vec3 uSun;
        uniform float uDrawDistance;
        out vec4 outColor;
        float hash(vec2 x){return fract(sin(dot(x,vec2(127.1,311.7)))*43758.5453);}
        float sunlightFraction(vec4 lightCoord,float distanceFromEye){
            if(uShadowEnabled<.5 || distanceFromEye>115.0)return 1.0;
            vec3 q=lightCoord.xyz/lightCoord.w*.5+.5;
            if(q.x<=.002||q.x>=.998||q.y<=.002||q.y>=.998||q.z<=0.0||q.z>=1.0)return 1.0;
            float result=0.0;
            for(int yy=0;yy<2;yy++)for(int xx=0;xx<2;xx++){
                vec2 off=(vec2(float(xx),float(yy))-.5)/1024.0;
                result+=step(q.z-.0025,texture(uShadowMap,q.xy+off).r);
            }
            return mix(1.0,.19+.81*result*.25,1.0-smoothstep(75.0,115.0,distanceFromEye));
        }
        void main(){
            vec3 n=normalize(vNormal);
            float ndotl=max(0.0,dot(n,normalize(uSun)));
            vec3 ambient=mix(vec3(.19,.17,.15),vec3(.40,.48,.57),
                             clamp(n.y*.5+.5,0.0,1.0));
            float grid=hash(floor(vPos.xz*2.5)+floor(vPos.y*2.5));
            vec3 albedo;
            if(vMat==0) {
                // Splat-mapping procedural por densidade de floresta e manchas suaves.
                // A textura antiga permanece como detalhe ate as 3 texturas chegarem.
                float lush=clamp((1.0-vForest)*.99+(vPatch-.5)*.31-.08,0.0,1.0);
                float groundDetail=dot(texture(uGround,vUv).rgb,vec3(.33333));
                vec3 soil=vec3(.32,.26,.17)*(.75+groundDetail*.48);
                vec3 mixed=vec3(.25,.34,.145)*(.79+groundDetail*.43);
                vec3 meadow=vec3(.15,.37,.108)*(.77+groundDetail*.49);
                soil*=mix(.84,1.14,vPatch);
                mixed*=mix(.89,1.14,vPatch);
                meadow*=mix(.82,1.19,vPatch);
                if(uHasTerrainTextures>.5){
                    soil=texture(uTerrainDirt,vUv).rgb;
                    mixed=texture(uTerrainMiddle,vUv).rgb;
                    meadow=texture(uTerrainGrass,vUv).rgb;
                }
                float mid=smoothstep(.10,.48,lush);
                float green=smoothstep(.43,.86,lush);
                albedo=mix(mix(soil,mixed,mid),meadow,green);
                // Terra praticamente neutra; grama acompanha a temperatura do dia.
                albedo*=mix(vec3(1.0),uGrassDayTint,smoothstep(.16,.79,lush));
                // Paredoes expondo rocha em placas, com varias cristas ainda verdes.
                if(uHasRockSurface>.5){
                    vec3 weight=pow(abs(n),vec3(4.0));weight/=max(.001,weight.x+weight.y+weight.z);
                    vec3 rTex=texture(uRockSurfaceTex,vPos.yz*.20).rgb*weight.x+
                              texture(uRockSurfaceTex,vPos.xz*.20).rgb*weight.y+
                              texture(uRockSurfaceTex,vPos.xy*.20).rgb*weight.z;
                    float rockGray=dot(rTex,vec3(.33333));
                    vec3 rockColor=mix(vec3(.26,.25,.25),vec3(.66,.65,.62),rockGray);
                    float steep=smoothstep(.23,.61,1.0-n.y);
                    float coverage=steep*(.43+.57*vPatch);
                    albedo=mix(albedo,rockColor,coverage);
                }
            } else if(vMat==1){
                albedo=vec3(.35,.23,.15);
                albedo*=.8+.28*hash(floor(vPos.xz*9.0)+floor(vPos.y*7.0));
            } else if(vMat==2 || vMat==4){
                float f=hash(floor(vPos.xz*3.5)+floor(vPos.y*3.0));
                albedo=mix(vec3(.23,.34,.19),vec3(.38,.48,.26),f);
                if(vMat==4)albedo*=1.28;
            } else if(vMat==3){
                albedo=mix(vec3(.34,.34,.31),vec3(.55,.52,.44),grid);
            } else {
                // Sombra geometrica pixelada dos objetos.
                float alpha= .34*(.8+.2*step(.5,grid));
                outColor=vec4(vec3(.07,.11,.08),alpha); return;
            }
            // Sol real bloqueado pelos GLB proximos + ceu ocluido pelo dossel.
            float lightFraction=sunlightFraction(vLightCoord,vDist);
            ambient*=1.0-clamp(vForest,0.0,1.0)*.36;
            vec3 direct=vec3(1.08,1.01,.90)*ndotl*.84*lightFraction;
            vec3 color=albedo*(ambient+direct);
            color*=1.0-clamp(vForest,0.0,1.0)*.17;
            // solpixel-v08: nevoa atmosferica desde meia distancia.
            float fog=smoothstep(uDrawDistance*.28,uDrawDistance*.90,vDist)*.95;
            color=mix(color,vec3(.82,.92,1.0),fog);
            float bayer=mod(floor(gl_FragCoord.x)+2.0*mod(floor(gl_FragCoord.y),2.0),4.0)/4.0;
            color=floor(clamp(color,0.0,1.0)*63.0+bayer*.45)/63.0;
            outColor=vec4(color,1.0);
        }
        """;
    // Solas Rocks v1 - Pedras GLB: tri-planar real usando a textura fornecida, iluminacao e LOD instanciado.
    static final String ROCK_V = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec3 aNormal;
        layout(location=2) in vec4 aLocationScaleX;
        layout(location=3) in vec4 aScaleRot;
        uniform mat4 uMvp;
        uniform vec3 uEye,uSun;
        out vec3 vNormal,vLocal;
        out float vLight,vDist;
        void main(){
            float cs=aScaleRot.z,sn=aScaleRot.w;
            vec3 p=aPos*vec3(aLocationScaleX.w,aScaleRot.x,aScaleRot.y);
            vec3 r=vec3(p.x*cs-p.z*sn,p.y,p.x*sn+p.z*cs);
            vec3 n=vec3(aNormal.x/aLocationScaleX.w,aNormal.y/aScaleRot.x,aNormal.z/aScaleRot.y);
            vNormal=normalize(vec3(n.x*cs-n.z*sn,n.y,n.x*sn+n.z*cs));
            vec3 world=aLocationScaleX.xyz+r;
            vLocal=aPos*4.0;
            vLight=max(0.0,dot(vNormal,normalize(uSun)));
            vDist=distance(world,uEye);
            gl_Position=uMvp*vec4(world,1.0);
        }
        """;
    static final String ROCK_F = """
        #version 300 es
        precision highp float;
        in vec3 vNormal,vLocal;
        in float vLight,vDist;
        uniform sampler2D uRockTex;
        uniform float uDrawDistance;
        out vec4 outColor;
        void main(){
            vec3 w=pow(abs(normalize(vNormal)),vec3(4.0));w/=max(.001,w.x+w.y+w.z);
            vec3 tex=texture(uRockTex,vLocal.yz).rgb*w.x+
                     texture(uRockTex,vLocal.xz).rgb*w.y+
                     texture(uRockTex,vLocal.xy).rgb*w.z;
            float grain=dot(tex,vec3(.33333));
            vec3 stone=mix(vec3(.30,.30,.29),vec3(.65,.64,.61),grain);
            float ambient=.34+.12*clamp(vNormal.y,0.0,1.0);
            vec3 color=stone*(ambient+vec3(1.03,.99,.88)*vLight*.75);
            color=mix(color,vec3(.82,.92,1.0),smoothstep(uDrawDistance*.28,uDrawDistance*.9,vDist)*.95);
            float dither=mod(floor(gl_FragCoord.x)+2.0*mod(floor(gl_FragCoord.y),2.0),4.0)/4.0;
            color=floor(clamp(color,0.0,1.0)*63.0+dither*.45)/63.0;
            outColor=vec4(color,1.0);
        }
        """;

    static final String FULL_V = """
        #version 300 es
        precision highp float;
        out vec2 vUv;
        void main(){
            vec2 p;
            if(gl_VertexID==0)p=vec2(-1.0,-1.0);
            else if(gl_VertexID==1)p=vec2(3.0,-1.0);
            else p=vec2(-1.0,3.0);
            vUv=p*.5+.5;
            gl_Position=vec4(p,0.0,1.0);
        }
        """;
    static final String SKY_F = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform vec3 uForward,uRight,uUp,uSun;
        uniform float uAspect,uFov;
        out vec4 outColor;
        void main(){
            // APENAS gradiente azul: sem panorama, nuvens artificiais, disco solar ou ruido.
            vec2 q=(vUv*2.0-1.0)*vec2(uAspect,1.0)*uFov;
            vec3 dir=normalize(uForward+q.x*uRight+q.y*uUp);
            float altitude=smoothstep(-.22,.85,dir.y);
            vec3 horizon=vec3(.82,.92,1.0);
            vec3 zenith=vec3(.32,.59,.89);
            // SolPixel v0.10: 15% mais escuro no ceu, sol preservado.
            vec3 sky=mix(horizon,zenith,altitude)*.85;
            // solpixel-v08: disco solar e corona reais no shader de ceu.
            float towards=dot(dir,normalize(uSun));
            float disc=smoothstep(.99920,.99985,towards);
            float innerHalo=pow(max(towards,0.0),75.0);
            float outerHalo=pow(max(towards,0.0),13.0);
            sky+=vec3(1.0,.73,.44)*(innerHalo*.40+outerHalo*.075);
            sky=mix(sky,vec3(2.0,1.70,1.24),disc);
            outColor=vec4(sky,1.0);
        }
        """;
    static final String BRIGHT_F = """
        #version 300 es
        precision mediump float;
        in vec2 vUv;uniform sampler2D uScene;uniform vec2 uTexel;
        out vec4 outColor;
        void main(){
            vec3 c=vec3(0.0);
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)
                c+=texture(uScene,vUv+vec2(float(x),float(y))*uTexel*2.0).rgb/9.0;
            float l=dot(c,vec3(.2126,.7152,.0722));
            // Bloom seleciona o sol e os pontos mais luminosos, nao todo o ceu.
            c=max(c-vec3(.90),vec3(0.0))*smoothstep(.85,.98,l)*2.7;
            outColor=vec4(c,1.0);
        }
        """;
    static final String BLUR_F = """
        #version 300 es
        precision mediump float;
        in vec2 vUv;uniform sampler2D uInput;uniform vec2 uDirection;
        out vec4 outColor;
        void main(){
            vec3 c=texture(uInput,vUv).rgb*.227027;
            c+=texture(uInput,vUv+uDirection*1.384615).rgb*.316216;
            c+=texture(uInput,vUv-uDirection*1.384615).rgb*.316216;
            c+=texture(uInput,vUv+uDirection*3.230769).rgb*.070270;
            c+=texture(uInput,vUv-uDirection*3.230769).rgb*.070270;
            outColor=vec4(c,1.0);
        }
        """;
    static final String FINAL_F = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uScene,uDepth,uBloom;
        uniform vec2 uTexel,uSunUv;
        uniform float uSunVisible;
        uniform float uForestNear;
        out vec4 outColor;
        float linearDepth(float d){
            float near=.12,far=2200.0,z=d*2.0-1.0;
            return 2.0*near*far/(far+near-z*(far-near));
        }
        // Usar profundidade para separar ceu de nuvens e montanhas:
        // as nuvens com dithering escrevem profundidade nos pixels visiveis.
        float skyOpen(vec2 uv){
            if(uv.x<0.0||uv.x>1.0||uv.y<0.0||uv.y>1.0)return 0.0;
            return step(.999997,texture(uDepth,uv).r);
        }
        void main(){
            vec3 color=texture(uScene,vUv).rgb;
            float d=texture(uDepth,vUv).r;
            float dist=linearDepth(d);
            float blur=smoothstep(18.0,145.0,dist)*.85;
            if(d>.9997)blur=.15;
            blur=max(blur,(1.0-smoothstep(1.0,5.0,dist))*.18);
            if(blur>.025){
                vec3 sum=vec3(0.0);
                const vec2 kernel[8]=vec2[8](vec2(1,0),vec2(-1,0),vec2(0,1),vec2(0,-1),
                  vec2(.707,.707),vec2(-.707,.707),vec2(.707,-.707),vec2(-.707,-.707));
                for(int i=0;i<8;i++)sum+=texture(uScene,vUv+kernel[i]*uTexel*(1.0+blur*4.5)).rgb;
                color=mix(color,sum*.125,blur*.63);
            }
            color+=texture(uBloom,vUv).rgb*.95;
            if(uSunVisible>.001){
                vec2 toSun=uSunUv-vUv;
                float radius=length(toSun);
                float angle=atan(toSun.y,toSun.x);
                // Visibilidade parcial do disco permite raios pelas aberturas das nuvens.
                const vec2 ring[9]=vec2[9](vec2(0,0),
                  vec2(.012,0),vec2(-.012,0),vec2(0,.012),vec2(0,-.012),
                  vec2(.009,.009),vec2(-.009,.009),vec2(.009,-.009),vec2(-.009,-.009));
                float openSun=0.0;
                for(int i=0;i<9;i++)openSun+=skyOpen(uSunUv+ring[i]);
                openSun/=9.0;
                float sunGate=smoothstep(.028,.33,openSun);
                float halo=exp(-radius*5.6)*.32+exp(-radius*16.0)*.25;
                float spikes=pow(max(0.0,cos(angle*10.0)),32.0)*exp(-radius*4.4)*.19;
                float shaftSamples=0.0;
                for(int i=1;i<=12;i++){
                    float t=float(i)/13.0;
                    vec2 sampleUv=vUv+toSun*t;
                    // Folhas, montanhas e as proprias nuvens recortam os raios.
                    float clear=skyOpen(sampleUv);
                    shaftSamples+=clear*(.38+.62*t)/12.0;
                }
                float irregular=.75+.25*pow(max(0.0,cos(angle*13.0+.5)),4.0);
                float shafts=shaftSamples*exp(-radius*2.7)*irregular*.72;
                // Feixes extras nas florestas proximas. Bloqueio de folhas
                // pelo depth buffer; influencia apenas objetos ate 100m.
                float nearby=1.0-smoothstep(46.0,100.0,dist);
                float forestShafts=shafts*uForestNear*nearby*.95;
                color+=vec3(1.0,.79,.55)*uSunVisible*
                       (sunGate*(halo+spikes+shafts)+forestShafts);
            }
            color=color/(color+vec3(1.0));
            color=pow(color,vec3(.85));
            float vignette=1.0-.20*dot(vUv-.5,vUv-.5);
            outColor=vec4(color*vignette,1.0);
        }
        """;
    static final String SHADOW_V = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPosition;
        layout(location=2) in vec2 aUv;
        layout(location=3) in vec4 aLocation;
        layout(location=4) in vec4 aRotation;
        uniform mat4 uLightVP;
        out vec2 vUv;
        void main(){
            float cs=cos(aRotation.w),sn=sin(aRotation.w);
            vec3 p=aPosition*aLocation.w;
            vec3 tilted=vec3(p.x,p.y*cs-p.z*sn,p.y*sn+p.z*cs);
            vec3 rotated=vec3(tilted.x*aRotation.x-tilted.z*aRotation.y,
                              tilted.y,tilted.x*aRotation.y+tilted.z*aRotation.x);
            vUv=aUv;
            gl_Position=uLightVP*vec4(aLocation.xyz+rotated,1.0);
        }
        """;
    static final String SHADOW_F = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uTex;
        uniform float uLeaf;
        void main(){
            if(uLeaf>.5 && texture(uTex,vUv).a<.5)discard;
        }
        """;

    static final String DUST_V = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aSeed;
        uniform mat4 uMvp;
        uniform vec3 uEye;
        uniform float uTime;
        out float vAlpha;
        void main(){
            vec3 p=uEye+vec3(aSeed.x*55.0,(aSeed.y-.5)*9.0,aSeed.z*55.0);
            p.x+=sin(uTime*.21+aSeed.z*27.0)*1.4;
            p.y+=sin(uTime*.43+aSeed.x*43.0)*.9;
            p.z+=cos(uTime*.17+aSeed.x*31.0)*1.5;
            gl_Position=uMvp*vec4(p,1.0);
            float dist=length(p-uEye);
            gl_PointSize=clamp(83.0/dist,1.5,6.0);
            vAlpha=clamp((65.0-dist)/45.0,0.0,1.0)*(.35+.65*aSeed.y);
        }
        """;
    static final String DUST_F = """
        #version 300 es
        precision mediump float;
        in float vAlpha;out vec4 outColor;
        void main(){
            vec2 p=gl_PointCoord*2.0-1.0;
            float shape=1.0-smoothstep(.35,1.0,length(p));
            outColor=vec4(1.0,.82,.55,shape*vAlpha*.44);
        }
        """;

    static final String TREE_V = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPosition;
        layout(location=1) in vec3 aNormal;
        layout(location=2) in vec2 aUv;
        layout(location=3) in vec4 aLocation;
        layout(location=4) in vec4 aRotation;
        uniform mat4 uMvp;
        uniform mat4 uLightVP;
        uniform vec3 uEye;
        out vec4 vLightCoord;
        uniform vec3 uSun;
        out vec2 vUv;
        out vec3 vNormal;
        out float vForest;
        out float vDist;
        out vec3 vWorld;
        out float vGouraud;
        void main(){
            // Inclinar o tronco no eixo X local antes de aplicar o yaw da arvore.
            float cs=cos(aRotation.w),sn=sin(aRotation.w);
            vec3 p=aPosition*aLocation.w;
            vec3 tilted=vec3(p.x,p.y*cs-p.z*sn,p.y*sn+p.z*cs);
            vec3 rotated=vec3(tilted.x*aRotation.x-tilted.z*aRotation.y,
                              tilted.y,tilted.x*aRotation.y+tilted.z*aRotation.x);
            vec3 world=aLocation.xyz+rotated;
            vLightCoord=uLightVP*vec4(world,1.0);
            vec3 n=vec3(aNormal.x,aNormal.y*cs-aNormal.z*sn,
                        aNormal.y*sn+aNormal.z*cs);
            vNormal=normalize(vec3(n.x*aRotation.x-n.z*aRotation.y,n.y,
                                   n.x*aRotation.y+n.z*aRotation.x));
            vWorld=world;vUv=aUv;vForest=aRotation.z;
            vGouraud=max(0.0,dot(vNormal,normalize(uSun)));
            vDist=distance(world,uEye);
            gl_Position=uMvp*vec4(world,1.0);
        }
        """;
    static final String TREE_F = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        in vec3 vNormal,vWorld;
        in float vForest,vDist;
        in float vGouraud;
        in vec4 vLightCoord;
        uniform sampler2D uTex,uShadowMap;
        uniform float uShadowEnabled;
        uniform vec3 uSun;
        uniform float uDrawDistance;
        uniform float uLeaf;
        out vec4 outColor;
        float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
        float sunlightFraction(vec4 lightCoord,float distanceFromEye){
            if(uShadowEnabled<.5 || distanceFromEye>115.0)return 1.0;
            vec3 q=lightCoord.xyz/lightCoord.w*.5+.5;
            if(q.x<=.002||q.x>=.998||q.y<=.002||q.y>=.998||q.z<=0.0||q.z>=1.0)return 1.0;
            float result=0.0;
            for(int yy=0;yy<2;yy++)for(int xx=0;xx<2;xx++){
                vec2 off=(vec2(float(xx),float(yy))-.5)/1024.0;
                result+=step(q.z-.0025,texture(uShadowMap,q.xy+off).r);
            }
            return mix(1.0,.19+.81*result*.25,1.0-smoothstep(75.0,115.0,distanceFromEye));
        }
        void main(){
            vec4 tex=texture(uTex,vUv);
            if(uLeaf>.5 && tex.a<.5)discard;
            float nl=vGouraud;
            float upward=clamp(normalize(vNormal).y*.5+.5,0.0,1.0);
            vec3 ambient=mix(vec3(.22,.20,.17),vec3(.43,.50,.57),upward);
            float sunDiffuse=clamp(nl+(uLeaf>.5?.12:0.0),0.0,1.0);
            float sunlightVis=sunlightFraction(vLightCoord,vDist);
            vec3 sunlight=vec3(1.08,1.01,.89)*sunDiffuse*.82*sunlightVis;
            ambient*=1.0-vForest*.30;
            float canopy=1.-vForest*(uLeaf>.5?.30:.37);
            vec3 color=tex.rgb*canopy*(ambient+sunlight);
            // solpixel-v08: nevoa tambem nas folhas GLB originais.
            float fog=smoothstep(uDrawDistance*.28,uDrawDistance*.90,vDist)*.95;
            color=mix(color,vec3(.82,.92,1.0),fog);
            float dither=mod(floor(gl_FragCoord.x)+2.0*mod(floor(gl_FragCoord.y),2.0),4.0)/4.0;
            color=floor(clamp(color,0.0,1.0)*63.0+dither*.45)/63.0;
            outColor=vec4(color,1.0);
        }
        """;

    // SolPixel vegetation v0.11 - quatro colunas, dez texturas em atlas 1024px.
    static final String GRASS_V = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec2 aUv;
        layout(location=2) in vec4 aInstancePosSize;
        layout(location=3) in vec4 aInstanceExtra;
        uniform mat4 uMvp;
        uniform vec3 uEye;
        uniform float uWindTime;
        uniform vec2 uPlayerXZ,uPlayerVelocity;
        out vec2 vUv;
        out float vDist,vShade;
        flat out int vKind;
        void main(){
            vec3 p=aPos*aInstancePosSize.w;
            vec3 rotated=vec3(p.x*aInstanceExtra.x-p.z*aInstanceExtra.y,
                              p.y,p.x*aInstanceExtra.y+p.z*aInstanceExtra.x);
            vec3 world=aInstancePosSize.xyz+rotated;
            float dist=length(world.xz-uPlayerXZ);
            float nearby=1.0-smoothstep(12.0,50.0,dist);
            // O topo dobra, a base fica enraizada. Parado alem de 50m.
            float tip=pow(clamp(aPos.y,0.0,1.0),1.55);
            float wave=sin(uWindTime*1.65+world.x*.27+world.z*.18+aInstanceExtra.w*5.0);
            vec2 sway=vec2(.115,.062)*wave*nearby;
            float passing=(1.0-smoothstep(.45,2.1,dist))*
                          clamp(length(uPlayerVelocity)*.20,0.0,1.0);
            sway+=uPlayerVelocity*.065*passing;
            world.xz+=tip*sway;
            gl_Position=uMvp*vec4(world,1.0);
            float n=floor(aInstanceExtra.z+.5);
            float col=mod(n,4.0),row=floor(n/4.0);
            // Cada sprite ocupa 240x240 e tem borda de 8 pixels contra bleed.
            vUv=(vec2(col,row)*256.0+vec2(8.0)+aUv*240.0)/1024.0;
            vDist=distance(world,uEye);
            vShade=aInstanceExtra.w;
            vKind=int(n);
        }
        """;
    static final String GRASS_F = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        in float vDist,vShade;
        flat in int vKind;
        uniform sampler2D uGrassAtlas;
        uniform vec3 uGrassTint;
        uniform float uGrassLimit;
        out vec4 outColor;
        void main(){
            vec4 tex=texture(uGrassAtlas,vUv);
            if(tex.a<.46)discard;
            // Dissolve distante com dithering Bayer verdadeiro, sem alpha blending.
            float fade=1.0-smoothstep(uGrassLimit*.70,uGrassLimit,vDist);
            const float bayer[16]=float[16](
                 0., 8., 2.,10.,12., 4.,14., 6.,
                 3.,11., 1., 9.,15., 7.,13., 5.);
            int ix=int(mod(floor(gl_FragCoord.x),4.0));
            int iy=int(mod(floor(gl_FragCoord.y),4.0));
            if(fade<(bayer[iy*4+ix]+.5)/16.0)discard;
            // Preserva vermelho das flores e branco dos cogumelos.
            float influence=vKind<=2?.87:(vKind==8?.78:(vKind==9?.52:.34));
            vec3 dayTint=mix(vec3(1.0),uGrassTint,influence);
            vec3 color=tex.rgb*dayTint;
            color*=mix(.83,1.15,clamp(vShade,0.0,1.0));
            outColor=vec4(clamp(color,0.0,1.0),1.0);
        }
        """;

    static final String CLOUD_V = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec3 aNormal;
        uniform mat4 uMvp;
        uniform vec4 uCloudPosScale;
        uniform vec3 uCloudCenter;
        out vec3 vLocal,vNormal;
        out float vElevation;
        void main(){
            vec3 pos=(aPos-uCloudCenter)*uCloudPosScale.w+uCloudPosScale.xyz;
            vLocal=aPos;vNormal=aNormal;
            vElevation=pos.y;
            gl_Position=uMvp*vec4(pos,1.0);
        }
        """;
    static final String CLOUD_F = """
        #version 300 es
        precision highp float;
        in vec3 vLocal,vNormal;
        in float vElevation;
        uniform sampler2D uCloudMask;
        uniform vec3 uSun;
        uniform float uAlpha;
        out vec4 outColor;
        float bayer(vec2 pixel){
            // Matriz Bayer 4x4 fixa. O padrao nao varia no tempo: sem cintilacao.
            const float b[16]=float[16](0.,8.,2.,10.,12.,4.,14.,6.,3.,11.,1.,9.,15.,7.,13.,5.);
            ivec2 p=ivec2(mod(floor(pixel),4.0));
            return (b[p.x+4*p.y]+.5)/16.0;
        }
        void main(){
            float dither=bayer(gl_FragCoord.xy);
            // Dissolve graduado em pixels, apenas nas faixas de transicao.
            // Na visibilidade maxima a nuvem permanece 90% opaca.
            float visibility=clamp(uAlpha/.90,0.0,1.0);
            float coverage=smoothstep(0.0,1.0,visibility);
            if(dither>coverage)discard;
            vec3 n=normalize(vNormal);
            vec2 uv=abs(n.y)>.5?vLocal.xz:(abs(n.x)>.5?vLocal.zy:vLocal.xy);
            float mask=texture(uCloudMask,uv*.95).r;
            float light=max(0.0,dot(n,normalize(uSun)));
            float skyFill=.18+.18*max(n.y,0.0);
            // Dithering tambem no ColorRamp azul-acinzentado -> branco.
            float ramp=smoothstep(.18,.83,light*.84+skyFill+(dither-.5)*.115);
            vec3 shadowBlueGray=vec3(.48,.58,.69);
            vec3 sunWhite=vec3(.99,.995,1.0);
            vec3 color=mix(shadowBlueGray,sunWhite,ramp);
            color*=mix(.91,1.04,mask);
            color=min(color*1.10,vec3(1.0)); // +10% de claridade
            color=floor(color*47.0+dither*.60)/47.0;
            outColor=vec4(color,.90);
        }
        """;
}
