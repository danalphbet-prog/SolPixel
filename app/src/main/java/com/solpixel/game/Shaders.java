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
        void main(){
            gl_Position=uMvp*vec4(aPos,1.0);
            vPos=aPos;vNormal=aNormal;vUv=aUv;
            vMat=int(floor(aMaterial+0.001));
            vForest=fract(aMaterial);
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
        uniform sampler2D uGround;
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
                albedo=texture(uGround,vUv).rgb;
                // Pequenas variacoes deterministicas na vegetacao do solo.
                float grass=hash(floor(vPos.xz*1.5));
                albedo*=vec3(.95+grass*.12,.96+grass*.11,.96+grass*.035);
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
            float fog=smoothstep(uDrawDistance*.53,uDrawDistance*1.05,vDist)*.68;
            color=mix(color,vec3(.77,.89,.99),fog);
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
            outColor=vec4(mix(horizon,zenith,altitude),1.0);
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
            c=max(c-vec3(.60),vec3(0.0))*smoothstep(.52,.95,l)*1.6;
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
                float spikes=pow(max(.0,cos(angle*13.0)),24.0);
                float glow=exp(-radius*3.7)*(0.21+spikes*.26);
                // Raios: amostragem radial com oclusao simplificada pela profundidade.
                float shafts=0.0;
                for(int i=1;i<=9;i++){
                    float t=float(i)/10.0;
                    vec2 sampleUv=vUv+toSun*t;
                    if(sampleUv.x>=0.0&&sampleUv.x<=1.0&&sampleUv.y>=0.0&&sampleUv.y<=1.0){
                        float occlusion=step(.9998,texture(uDepth,sampleUv).r);
                        vec3 luminance=texture(uScene,sampleUv).rgb;
                        shafts+=occlusion*smoothstep(.45,1.3,dot(luminance,vec3(.3,.5,.2)))*.112;
                    }
                }
                color+=vec3(1.0,.70,.39)*(glow+shafts*.20);
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
            float fog=smoothstep(uDrawDistance*.53,uDrawDistance*1.05,vDist)*.68;
            color=mix(color,vec3(.77,.89,.99),fog);
            float dither=mod(floor(gl_FragCoord.x)+2.0*mod(floor(gl_FragCoord.y),2.0),4.0)/4.0;
            color=floor(clamp(color,0.0,1.0)*63.0+dither*.45)/63.0;
            outColor=vec4(color,1.0);
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
            float light=max(.0,dot(n,normalize(uSun)));
            float cel=floor((.40+light*.60)*5.0)/5.0;
            vec3 ivory=vec3(.99,.962,.87),gray=vec3(.52,.55,.54);
            vec3 color=mix(gray,ivory,cel);
            color*=mix(.77,1.05,mask);
            float dither=mod(gl_FragCoord.x+2.0*mod(gl_FragCoord.y,2.0),4.0)/4.0;
            color=floor(clamp(color,0.0,1.0)*31.0+dither)/31.0;
            outColor=vec4(color,uAlpha);
        }
        """;
}
