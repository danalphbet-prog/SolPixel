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
        uniform vec3 uEye;
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
        uniform sampler2D uGround;
        uniform sampler2D uTerrainDirt,uTerrainMiddle,uTerrainGrass;
        uniform float uHasTerrainTextures;
        uniform vec3 uGrassDayTint;
        uniform vec3 uSun;
        uniform float uDrawDistance;
        out vec4 outColor;
        float hash(vec2 x){return fract(sin(dot(x,vec2(127.1,311.7)))*43758.5453);}
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
            vec3 direct=vec3(1.08,1.01,.90)*ndotl*.84;
            vec3 color=albedo*(ambient+direct);
            color*=1.0-clamp(vForest,0.0,1.0)*.30;
            // solpixel-v08: nevoa atmosferica desde meia distancia.
            float fog=smoothstep(uDrawDistance*.28,uDrawDistance*.90,vDist)*.95;
            color=mix(color,vec3(.82,.92,1.0),fog);
            float bayer=mod(floor(gl_FragCoord.x)+2.0*mod(floor(gl_FragCoord.y),2.0),4.0)/4.0;
            color=floor(clamp(color,0.0,1.0)*63.0+bayer*.45)/63.0;
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
            vec3 sky=mix(horizon,zenith,altitude);
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
        out vec4 outColor;
        float linearDepth(float d){
            float near=.12,far=1100.0,z=d*2.0-1.0;
            return 2.0*near*far/(far+near-z*(far-near));
        }
        void main(){
            vec3 color=texture(uScene,vUv).rgb;
            float d=texture(uDepth,vUv).r;
            float dist=linearDepth(d);
            float blur=smoothstep(18.0,145.0,dist)*.85;
            if(d>.99995)blur=.15;
            blur=max(blur,(1.0-smoothstep(1.0,5.0,dist))*.18);
            if(blur>.025){
                vec3 sum=vec3(0.0);
                const vec2 kernel[8]=vec2[8](vec2(1,0),vec2(-1,0),vec2(0,1),vec2(0,-1),
                  vec2(.707,.707),vec2(-.707,.707),vec2(.707,-.707),vec2(-.707,-.707));
                for(int i=0;i<8;i++)sum+=texture(uScene,vUv+kernel[i]*uTexel*(1.0+blur*4.5)).rgb;
                color=mix(color,sum*.125,blur*.63);
            }
            vec3 bloom=texture(uBloom,vUv).rgb;
            color+=bloom*.95;
            if(uSunVisible>.5){
                vec2 toSun=uSunUv-vUv;
                float radius=length(toSun);
                float angle=atan(toSun.y,toSun.x);
                float spikes=pow(max(0.0,cos(angle*11.0)),18.0);
                // Sol oculto pelo relevo nao deve iluminar atraves da montanha.
                float centerDepth=texture(uDepth,clamp(uSunUv,vec2(.001),vec2(.999))).r;
                float sunExposed=step(.9997,centerDepth);
                float bloomHalo=exp(-radius*5.0)*(.27+spikes*.42);
                float shafts=0.0;
                for(int i=1;i<=12;i++){
                    float t=float(i)/13.0;
                    vec2 sampleUv=vUv+toSun*t;
                    if(sampleUv.x>=0.0&&sampleUv.x<=1.0&&sampleUv.y>=0.0&&sampleUv.y<=1.0){
                        float clear=step(.9997,texture(uDepth,sampleUv).r);
                        float nearSun=exp(-length(sampleUv-uSunUv)*7.0);
                        shafts+=clear*(.30+.70*nearSun)*.08333;
                    }
                }
                float beams=shafts*(.50+.50*spikes)*exp(-radius*2.4);
                color+=vec3(1.0,.78,.52)*sunExposed*(bloomHalo+beams*.85);
            }
            // Filmic simples / tone mapping seguido de gamma.
            color=color/(color+vec3(1.0));
            color=pow(color,vec3(.85));
            float vignette=1.0-.20*dot(vUv-.5,vUv-.5);
            outColor=vec4(color*vignette,1.0);
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
        uniform vec3 uEye;
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
        uniform sampler2D uTex;
        uniform vec3 uSun;
        uniform float uDrawDistance;
        uniform float uLeaf;
        out vec4 outColor;
        float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
        void main(){
            vec4 tex=texture(uTex,vUv);
            if(uLeaf>.5 && tex.a<.5)discard;
            float nl=vGouraud;
            float upward=clamp(normalize(vNormal).y*.5+.5,0.0,1.0);
            vec3 ambient=mix(vec3(.22,.20,.17),vec3(.43,.50,.57),upward);
            float sunDiffuse=clamp(nl+(uLeaf>.5?.12:0.0),0.0,1.0);
            vec3 sunlight=vec3(1.08,1.01,.89)*sunDiffuse*.82;
            float canopy=1.-vForest*(uLeaf>.5?.28:.35);
            vec3 color=tex.rgb*canopy*(ambient+sunlight);
            // solpixel-v08: nevoa tambem nas folhas GLB originais.
            float fog=smoothstep(uDrawDistance*.28,uDrawDistance*.90,vDist)*.95;
            color=mix(color,vec3(.82,.92,1.0),fog);
            float dither=mod(floor(gl_FragCoord.x)+2.0*mod(floor(gl_FragCoord.y),2.0),4.0)/4.0;
            color=floor(clamp(color,0.0,1.0)*63.0+dither*.45)/63.0;
            outColor=vec4(color,1.0);
        }
        """;

    static final String GRASS_V = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec2 aUv;
        layout(location=2) in vec4 aInstancePosSize;
        layout(location=3) in vec4 aInstanceExtra;
        uniform mat4 uMvp;
        uniform vec3 uEye;
        out vec2 vUv;
        out float vDist,vShade;
        void main(){
            float size=aInstancePosSize.w;
            vec3 p=aPos*size;
            vec3 rotated=vec3(p.x*aInstanceExtra.x-p.z*aInstanceExtra.y,
                              p.y,p.x*aInstanceExtra.y+p.z*aInstanceExtra.x);
            vec3 world=aInstancePosSize.xyz+rotated;
            gl_Position=uMvp*vec4(world,1.0);
            float n=floor(aInstanceExtra.z+.5);
            float col=mod(n,3.0),row=floor(n/3.0);
            vUv=(vec2(col,row)*80.0+vec2(8.0)+aUv*64.0)/256.0;
            vDist=distance(world,uEye);
            vShade=aInstanceExtra.w;
        }
        """;
    static final String GRASS_F = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        in float vDist,vShade;
        uniform sampler2D uGrassAtlas;
        uniform vec3 uGrassTint;
        uniform float uGrassLimit;
        out vec4 outColor;
        void main(){
            vec4 tex=texture(uGrassAtlas,vUv);
            if(tex.a<.48)discard;
            // Dissolve gradual na distancia, sem transparencia ordenada.
            float fade=1.0-smoothstep(uGrassLimit*.72,uGrassLimit,vDist);
            float px=mod(floor(gl_FragCoord.x),4.0),py=mod(floor(gl_FragCoord.y),4.0);
            float threshold=(px+py*4.0+.5)/16.0;
            if(fade<threshold)discard;
            float highlight=smoothstep(.20,.71,tex.r);
            vec3 color=vec3(.14,.365,.105)*uGrassTint;
            color*=mix(.67,1.20,highlight)*mix(.80,1.20,vShade);
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
        void main(){
            vec3 n=normalize(vNormal);
            vec2 localUv=abs(n.y)>.5?vLocal.xz:(abs(n.x)>.5?vLocal.zy:vLocal.xy);
            // Mascara halftone enviada pelo usuario, repetida nas faces voxel.
            float mask=texture(uCloudMask,localUv*.95).r;
            // solpixel-v08: ColorRamp de dois pontos com iluminacao direcional.
            float light=max(0.0,dot(n,normalize(uSun)));
            float skyFill=.18+.18*max(n.y,0.0);
            float ramp=smoothstep(.18,.83,light*.84+skyFill);
            vec3 shadowBlueGray=vec3(.48,.58,.69);
            vec3 sunWhite=vec3(.99,.995,1.0);
            vec3 color=mix(shadowBlueGray,sunWhite,ramp);
            // Textura original halftone; pouca modulacao para preservar os dois tons.
            color*=mix(.91,1.04,mask);
            float dither=mod(gl_FragCoord.x+2.0*mod(gl_FragCoord.y,2.0),4.0)/4.0;
            color=floor(clamp(color,0.0,1.0)*47.0+dither*.60)/47.0;
            outColor=vec4(color,uAlpha);
        }
        """;
}
