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
        out float vDist;
        void main(){
            gl_Position=uMvp*vec4(aPos,1.0);
            vPos=aPos;vNormal=aNormal;vUv=aUv;
            vMat=int(aMaterial+0.5);
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
        in float vDist;
        uniform sampler2D uGround;
        uniform vec3 uSun;
        out vec4 outColor;
        float hash(vec2 x){return fract(sin(dot(x,vec2(127.1,311.7)))*43758.5453);}
        void main(){
            vec3 n=normalize(vNormal);
            float light=clamp(dot(n,uSun)*.85+.26,.10,1.0);
            // Tons quantizados: iluminacao e sombra com bordas pixeladas.
            float grid=hash(floor(vPos.xz*2.5)+floor(vPos.y*2.5));
            light=floor((light+grid*.075)*5.0)/5.0;
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
            vec3 warm=vec3(1.08,1.02,.91);
            vec3 color=albedo*(vec3(.37,.43,.47)+warm*light*.82);
            float fog=clamp((vDist-50.0)/185.0,0.0,.87);
            color=mix(color,vec3(.67,.74,.75),fog);
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
        float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
        float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
            return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),
              mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
        void main(){
            vec2 q=(vUv*2.0-1.0)*vec2(uAspect,1.0)*uFov;
            vec3 dir=normalize(uForward+q.x*uRight+q.y*uUp);
            float altitude=smoothstep(-.23,.8,dir.y);
            vec3 sky=mix(vec3(.80,.75,.65),vec3(.31,.56,.75),altitude);
            sky=mix(sky,vec3(.14,.33,.58),smoothstep(.3,1.0,dir.y)*.34);
            float clouds= noise(dir.xz*13.0+dir.y*4.0)*.7+
                noise(dir.xz*26.0+dir.y*9.0)*.3;
            float cloudAmount=smoothstep(.58,.75,clouds)*smoothstep(-.04,.26,dir.y)*.37;
            sky=mix(sky,vec3(.98,.89,.73),cloudAmount);
            float towards=dot(dir,uSun);
            float disc=smoothstep(.9992,.99984,towards);
            float corona=pow(max(towards,0.0),65.0);
            float halo=pow(max(towards,0.0),8.0);
            sky+=vec3(1.0,.74,.38)*corona*.38+vec3(1.0,.54,.26)*halo*.09;
            sky=mix(sky,vec3(1.0,.94,.76)*2.0,disc);
            // Neblina leve no horizonte e luz solar fisicamente estilizada.
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
            float near=.12,far=420.0,z=d*2.0-1.0;
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
}
