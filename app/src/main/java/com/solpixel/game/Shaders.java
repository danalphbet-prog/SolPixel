package com.solpixel.game;

/** GLSL ES 3.0: nenhum pacote ou shader externo necessario. */
final class Shaders {
    static final String MESH_V = """
        #version 300 es
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec3 aNormal;
        layout(location=2) in vec2 aUv;
        layout(location=3) in float aMaterial;
        layout(location=4) in float aStoneMask;
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
        out float vStoneMask;
        float hashGround(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
        void main(){
            gl_Position=uMvp*vec4(aPos,1.0);
            vLightCoord=uLightVP*vec4(aPos,1.0);
            vPos=aPos;vNormal=aNormal;vUv=aUv;
            vMat=int(floor(aMaterial+0.001));
            vForest=fract(aMaterial);
            vPatch=mix(hashGround(floor(aPos.xz*.067)),
                       hashGround(floor(aPos.xz*.137+vec2(13.7,29.1))),.35);
            vStoneMask=aStoneMask;
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
        in float vStoneMask;
        in vec4 vLightCoord;
        uniform sampler2D uShadowMap;
        uniform float uShadowEnabled;
        uniform sampler2D uGround;
        uniform sampler2D uTerrainDirt,uTerrainMiddle,uTerrainGrass;
        uniform float uHasTerrainTextures;
        uniform sampler2D uRockSurfaceTex,uCliffRockTex;
        uniform float uHasRockSurface,uHasCliffRock,uCliffMoss;
        uniform vec3 uGrassDayTint;
        uniform vec3 uSun;
        uniform float uDrawDistance;
        uniform float uEclipse;
        uniform float uSwordGlow;
        uniform vec3 uSwordLightPos;
        uniform vec4 uCloudDiscs[4];
        uniform sampler2D uTerrainShadowMap;
        uniform vec4 uTerrainShadowBounds;
        uniform float uTerrainShadowEnabled;
        float bakedTerrainSun(vec2 xz){
            if(uTerrainShadowEnabled<.5)return 1.0;
            vec2 uv=(xz-uTerrainShadowBounds.xy)/uTerrainShadowBounds.zw;
            if(any(lessThan(uv,vec2(0.0)))||any(greaterThan(uv,vec2(1.0))))return 1.0;
            return mix(.22,1.0,texture(uTerrainShadowMap,uv).r);
        }
        out vec4 outColor;
        float cloudShadow(vec2 px){
            float darkness=0.0;
            for(int k=0;k<4;k++){
                vec4 disc=uCloudDiscs[k];
                if(disc.z>.001){
                    float d=length(px-disc.xy);
                    darkness=max(darkness,(1.0-smoothstep(disc.z*.37,disc.z*1.42,d))*disc.w);
                }
            }
            return 1.0-darkness;
        }
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
        uniform vec4 uSwordOccluders[12];
        float swordPointShadow(vec3 pos){
            vec3 ray=pos-uSwordLightPos;
            float rayLength=length(ray);
            if(rayLength<.12)return 1.0;
            vec3 dir=ray/max(rayLength,.001);
            float visibility=1.0;
            for(int i=0;i<12;i++){
                vec4 blocker=uSwordOccluders[i];
                if(blocker.w<=.05)continue;
                vec3 center=blocker.xyz-uSwordLightPos;
                float hit=dot(center,dir);
                if(hit<.08 || hit>rayLength-.10)continue;
                float separation=length(center-dir*hit);
                float pen=1.0-smoothstep(blocker.w*.72,blocker.w*1.20,separation);
                visibility*=1.0-.86*pen;
            }
            return clamp(visibility,.12,1.0);
        }
        vec3 swordPointLight(vec3 point,vec3 normal,vec3 albedo){
            if(uSwordGlow<=.012)return vec3(0.0);
            vec3 delta=uSwordLightPos-point;
            float distanceLight=length(delta);
            if(distanceLight>=5.0)return vec3(0.0);
            float falloff=pow(max(0.0,1.0-distanceLight/5.0),2.0);
            float ndotl=max(.10,dot(normalize(normal),delta/max(distanceLight,.001)));
            float shadow=swordPointShadow(point);
            return albedo*vec3(5.5,5.5,5.7)*uSwordGlow*falloff*ndotl*shadow;
        }

        float solasCloudHash(vec2 p){
            p=fract(p*vec2(123.34,456.21));
            p+=dot(p,p+45.32);
            return fract(p.x*p.y);
        }
        float cloudShadowMask(vec2 wp){
            vec2 q=floor((wp*0.0105 + vec2(.17,-.11))*26.0)/26.0;
            float n=solasCloudHash(floor(q*41.0));
            float n2=solasCloudHash(floor(q*17.0)+13.7);
            float c=smoothstep(.56,.86,n*.7+n2*.3);
            return mix(1.0,0.82,c);
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
                    // O atributo ja inclui inclinacao e provincias rochosas.
                    // Sem padrao generico em TODA parede: pedra so onde o campo indica.
                    float coverage=smoothstep(.105,.57,vStoneMask)*(.83+.17*vPatch);
                    if(uHasCliffRock>.5 && coverage>.03){
                        vec3 newRock=texture(uCliffRockTex,vPos.yz*.16).rgb*weight.x+
                                     texture(uCliffRockTex,vPos.xz*.16).rgb*weight.y+
                                     texture(uCliffRockTex,vPos.xy*.16).rgb*weight.z;
                        // Inicia em declive de ~50%, predomina acima de ~55 graus.
                        float cliffShare=smoothstep(.08,.45,1.0-n.y);
                        rockColor=mix(rockColor,newRock,cliffShare);
                        if(uCliffMoss>.5 && cliffShare>.10){
                            float moss=smoothstep(.65,.92,hash(floor(vPos.xz*.17)+
                                                              floor(vPos.y*.19)));
                            rockColor*=mix(vec3(1.0),vec3(.68,.86,.66),moss*.23);
                        }
                    }
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
            vec3 direct=vec3(1.08,1.01,.90)*ndotl*.84*lightFraction*cloudShadow(vPos.xz)*bakedTerrainSun(vPos.xz);
            vec3 color=albedo*(ambient+direct);
            color*=1.0-clamp(vForest,0.0,1.0)*.17;

            // solpixel-v08: nevoa atmosferica desde meia distancia.
            float fog=smoothstep(uDrawDistance*.28,uDrawDistance*.90,vDist)*.95;
            color=mix(color,vec3(.82,.92,1.0),fog);
            color=mix(color,color*vec3(.095,.16,.30),uEclipse*.96);
            // PointLight em tempo real com oclusão das pedras e troncos.
            color+=swordPointLight(vPos,n,albedo);
            float bayer=mod(floor(gl_FragCoord.x)+2.0*mod(floor(gl_FragCoord.y),2.0),4.0)/4.0;
            // 2.5-4 km: fewer color levels plus larger dither blocks.
            float farDetail=smoothstep(2500.0,4000.0,vDist);
            float farSteps=mix(63.0,17.0,farDetail);
            float farBayer=mod(floor(gl_FragCoord.x/mix(1.0,4.0,farDetail))+
                2.0*mod(floor(gl_FragCoord.y/mix(1.0,4.0,farDetail)),2.0),4.0)/4.0;
            color=floor(clamp(color,0.0,1.0)*farSteps+mix(bayer,farBayer,farDetail)*.65)/farSteps;
            color *= cloudShadowMask(vPos.xz);
            
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
        out vec3 vNormal,vLocal,vWorld;
        out float vLight,vDist,vRockVariant;
        void main(){
            float cs=aScaleRot.z,sn=aScaleRot.w;
            vec3 p=aPos*vec3(aLocationScaleX.w,aScaleRot.x,aScaleRot.y);
            vec3 r=vec3(p.x*cs-p.z*sn,p.y,p.x*sn+p.z*cs);
            vec3 n=vec3(aNormal.x/aLocationScaleX.w,aNormal.y/aScaleRot.x,aNormal.z/aScaleRot.y);
            vNormal=normalize(vec3(n.x*cs-n.z*sn,n.y,n.x*sn+n.z*cs));
            vec3 world=aLocationScaleX.xyz+r;
            vLocal=aPos*4.0;
            vRockVariant=fract(sin(dot(aLocationScaleX.xz,vec2(12.9898,78.233)))*43758.5453);
            vWorld=world;
            vLight=max(0.0,dot(vNormal,normalize(uSun)));
            vDist=distance(world,uEye);
            gl_Position=uMvp*vec4(world,1.0);
        }
        """;
    static final String ROCK_F = """
        #version 300 es
        precision highp float;
        in vec3 vNormal,vLocal,vWorld;
        in float vLight,vDist,vRockVariant;
        uniform sampler2D uRockTex,uCliffRockTex;
        uniform float uHasCliffRock;
        uniform float uDrawDistance,uEclipse,uSwordGlow;
        uniform vec3 uSwordLightPos;
        out vec4 outColor;
        uniform vec4 uSwordOccluders[12];
        float swordPointShadow(vec3 pos){
            vec3 ray=pos-uSwordLightPos;
            float rayLength=length(ray);
            if(rayLength<.12)return 1.0;
            vec3 dir=ray/max(rayLength,.001);
            float visibility=1.0;
            for(int i=0;i<12;i++){
                vec4 blocker=uSwordOccluders[i];
                if(blocker.w<=.05)continue;
                vec3 center=blocker.xyz-uSwordLightPos;
                float hit=dot(center,dir);
                if(hit<.08 || hit>rayLength-.10)continue;
                float separation=length(center-dir*hit);
                float pen=1.0-smoothstep(blocker.w*.72,blocker.w*1.20,separation);
                visibility*=1.0-.86*pen;
            }
            return clamp(visibility,.12,1.0);
        }
        vec3 swordPointLight(vec3 point,vec3 normal,vec3 albedo){
            if(uSwordGlow<=.012)return vec3(0.0);
            vec3 delta=uSwordLightPos-point;
            float distanceLight=length(delta);
            if(distanceLight>=5.0)return vec3(0.0);
            float falloff=pow(max(0.0,1.0-distanceLight/5.0),2.0);
            float ndotl=max(.10,dot(normalize(normal),delta/max(distanceLight,.001)));
            float shadow=swordPointShadow(point);
            return albedo*vec3(5.5,5.5,5.7)*uSwordGlow*falloff*ndotl*shadow;
        }

        float solasCloudHash(vec2 p){
            p=fract(p*vec2(123.34,456.21));
            p+=dot(p,p+45.32);
            return fract(p.x*p.y);
        }
        float cloudShadowMask(vec2 wp){
            vec2 q=floor((wp*0.0105 + vec2(.17,-.11))*26.0)/26.0;
            float n=solasCloudHash(floor(q*41.0));
            float n2=solasCloudHash(floor(q*17.0)+13.7);
            float c=smoothstep(.56,.86,n*.7+n2*.3);
            return mix(1.0,0.82,c);
        }

        void main(){
            vec3 w=pow(abs(normalize(vNormal)),vec3(4.0));w/=max(.001,w.x+w.y+w.z);
            vec3 tex=texture(uRockTex,vLocal.yz).rgb*w.x+
                     texture(uRockTex,vLocal.xz).rgb*w.y+
                     texture(uRockTex,vLocal.xy).rgb*w.z;
            if(uHasCliffRock>.5 && vRockVariant>.69){
                vec3 alt=texture(uCliffRockTex,vLocal.yz).rgb*w.x+
                         texture(uCliffRockTex,vLocal.xz).rgb*w.y+
                         texture(uCliffRockTex,vLocal.xy).rgb*w.z;
                tex=mix(tex,alt,.90);
            }
            float grain=dot(tex,vec3(.33333));
            vec3 stone=mix(vec3(.30,.30,.29),vec3(.65,.64,.61),grain);
            float ambient=.34+.12*clamp(vNormal.y,0.0,1.0);
            vec3 color=stone*(ambient+vec3(1.03,.99,.88)*vLight*.75);

            color=mix(color,vec3(.82,.92,1.0),smoothstep(uDrawDistance*.28,uDrawDistance*.9,vDist)*.87);
            color=mix(color,color*vec3(.095,.16,.30),uEclipse*.96);
            // PointLight em tempo real com oclusão das pedras e troncos.
            color+=swordPointLight(vWorld,normalize(vNormal),stone);
            float dither=mod(floor(gl_FragCoord.x)+2.0*mod(floor(gl_FragCoord.y),2.0),4.0)/4.0;
            color=floor(clamp(color,0.0,1.0)*63.0+dither*.45)/63.0;
            color *= cloudShadowMask(vWorld.xz);
            
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
    // Solas v0.4: star positions preserved from user Hipparcos/starmap EXRs.
    // Solas v0.4.1: dark blue procedural sky, star dots from EXR peak coordinates.
    static final String SKY_F = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform vec3 uForward,uRight,uUp,uSun,uMoonDir;
        uniform float uAspect,uFov,uEclipse,uSkyTime;
        uniform sampler2D uStarMap,uMoonTex;
        out vec4 outColor;
        const float PI=3.14159265359;
        float hash11(float n){return fract(sin(n*83.173+13.71)*43758.5453);}
        vec2 skyUV(vec3 dir){
            return vec2(fract(.5+atan(dir.z,dir.x)/(2.0*PI)),
                        .5-asin(clamp(dir.y,-1.0,1.0))/PI);
        }
        float meteor(vec2 uv){
            float id=floor(uSkyTime/37.0),t=fract(uSkyTime/37.0);
            if(hash11(id*1.71)<.35 || t>.20)return 0.0;
            vec2 start=vec2(.1+.75*hash11(id+16.7),.10+.36*hash11(id+4.9));
            vec2 md=normalize(vec2(.9,.25+.4*hash11(id+2.0)));
            vec2 delta=uv-(start+md*t*.85);delta.x=mod(delta.x+.5,1.0)-.5;
            float along=dot(delta,md);
            float crossDist=abs(delta.x*md.y-delta.y*md.x);
            return smoothstep(-.14,0.0,along)*(1.0-smoothstep(.0,.0025,crossDist)) *
                (1.0-smoothstep(-.01,.005,along))*(1.0-smoothstep(.15,.20,t));
        }
        void main(){
            vec2 q=(vUv*2.0-1.0)*vec2(uAspect,1.0)*uFov;
            vec3 dir=normalize(uForward+q.x*uRight+q.y*uUp);
            float alt=smoothstep(-.15,.90,dir.y);
            // Deliberately custom solid sky; no EXR RGB or HDR panorama background.
            vec3 day=mix(vec3(.72,.83,.94),vec3(.27,.51,.78),alt);
            vec3 night=mix(vec3(.039,.075,.126),vec3(.006,.011,.031),alt);
            vec3 sky=mix(day,night,uEclipse);
            vec2 uv=skyUV(dir);
            vec4 sprite=texture(uStarMap,uv);
            float p=max(sprite.r,max(sprite.g,sprite.b))*sprite.a;
            // Cintilações estáveis no céu com apagamentos de alguns segundos.
            float starId=hash11(floor(uv.x*2048.0)*.73+floor(uv.y*2048.0)*.31);
            float rate=.55+1.45*hash11(starId*31.7);
            float twinkle=smoothstep(-.34,.60,sin(uSkyTime*rate+starId*6.28318));
            vec3 starTint=mix(vec3(1.04,1.04,1.05),vec3(1.22,.52,.48),
                              step(.83,hash11(starId*87.7)));
            float visibility=uEclipse*smoothstep(-.20,.05,dir.y);
            sky+=sprite.rgb*starTint*(.8+1.8*p)*visibility*twinkle;
            // Brightest catalog stars are clearly visible and lightly haloed.
            sky+=vec3(1.0,1.04,1.15)*pow(p,7.0)*.65*visibility*twinkle;
            sky+=vec3(1.20,1.34,1.59)*meteor(uv)*visibility;
            float sun=dot(dir,normalize(uSun));
            float disc=smoothstep(.99972,.99985,sun);
            float halo=pow(max(sun,0.0),75.0)*.37+pow(max(sun,0.0),13.0)*.06;
            sky+=vec3(1.0,.73,.43)*halo*mix(1.0,.23,uEclipse);
            sky=mix(sky,vec3(1.62,1.30,.91),disc);
            vec3 mdir=normalize(uMoonDir);
            float moonDot=dot(dir,mdir);
            vec3 east=normalize(cross(vec3(0.0,1.0,0.0),mdir));
            vec3 north=cross(mdir,east);
            vec2 moonUV=vec2(dot(dir,east),dot(dir,north))/.052 + .5;
            if(all(greaterThanEqual(moonUV,vec2(0.0))) &&
               all(lessThanEqual(moonUV,vec2(1.0))) && moonDot>.9985){
                vec4 moon=texture(uMoonTex,moonUV);
                // Lunar disc obscures the sun, becoming black where solar overlap occurs.
                float overlap=smoothstep(.9983,.99975,sun);
                float coverage=smoothstep(.02,.95,uEclipse);
                float shaded=clamp(coverage*overlap,0.0,1.0);
                vec3 tint=moon.rgb*mix(1.0,.008,shaded);
                float dither=mod(floor(gl_FragCoord.x/3.0)+
                                 2.0*mod(floor(gl_FragCoord.y/3.0),2.0),4.0);
                tint*=mix(.91,1.02,dither/3.0);
                float moonOpacity=mix(.50,1.0,smoothstep(.04,.93,uEclipse));
                sky=mix(sky,tint,moon.a*moonOpacity);
            }
            // Only near totality is the solar corona especially bright.
            float ring=exp(-pow((1.0-sun-.00030)*8000.0,2.0));
            sky+=vec3(1.22,.75,.36)*ring*pow(uEclipse,3.0)*.80;
            outColor=vec4(sky,1.0);
        }
        """;

    // Solas v0.4: GLB em primeira pessoa, emissivo durante eclipse.
    static final String SWORD_V = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec3 aNormal;
        layout(location=2) in vec2 aUv;
        uniform mat4 uProjection;
        uniform float uGlow,uTime,uSwing,uWalk;
        out vec2 vUv;
        out vec3 vNormal;
        void main(){
            float angle=atan(.700,.714)*.80; // 20% mais vertical
            float cs=cos(angle),sn=sin(angle);
            vec3 p=aPos*1.357; // +15% sobre 1.18
            vec3 blade=vec3(p.x*cs-p.y*sn,p.x*sn+p.y*cs,p.z);
            // Rotação de golpe da direita para esquerda, ao redor do punho.
            vec2 pivot=vec2(.03,-.10);
            float swing=-1.10*uSwing;
            float ca=cos(swing),sa=sin(swing);
            vec2 off=blade.xy-pivot;
            blade.xy=vec2(off.x*ca-off.y*sa,off.x*sa+off.y*ca)+pivot;
            float sway=sin(uTime*3.0)*.018*uWalk+sin(uTime*1.25)*.005;
            blade+=vec3(1.29+sway,-.895+cos(uTime*2.5)*.012*uWalk,-1.70);
            gl_Position=uProjection*vec4(blade,1.0);
            vUv=aUv;
            vNormal=normalize(vec3(aNormal.x*cs-aNormal.y*sn,aNormal.x*sn+aNormal.y*cs,aNormal.z));
        }
        """;
    static final String SWORD_F = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        in vec3 vNormal;
        uniform sampler2D uSwordTexture;
        uniform float uGlow;
        out vec4 outColor;
        void main(){
            vec4 tex=texture(uSwordTexture,vUv);
            vec3 n=normalize(vNormal);
            float diffuse=.48+.52*max(0.0,dot(n,normalize(vec3(-.4,.75,1.0))));
            // Mantém cor e detalhes da textura enquanto o bloom realça a lâmina.
            float edge=pow(1.0-abs(n.z),2.0);
            vec3 base=tex.rgb*diffuse;
            vec3 radiance=base*(1.0+uGlow*1.75)+vec3(.57,.63,.74)*uGlow*(.35+edge*.5);
            outColor=vec4(radiance,tex.a);
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
        uniform vec2 uTexel,uSunUv,uSwordTipUv;
        uniform float uSwordRays,uSwordSwing;
        uniform float uSunVisible;
        uniform float uForestNear,uEclipse;
        out vec4 outColor;
        float linearDepth(float d){
            float near=.12,far=4300.0,z=d*2.0-1.0;
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
                // During eclipse reduce the original sunlight god-rays by 30%.
                color+=vec3(1.0,.79,.55)*uSunVisible*(1.0-uSwordRays)*
                       mix(1.0,.70,uEclipse)*(sunGate*(halo+spikes+shafts)+forestShafts);
            }
            // Feixes solares de verdade convergem do SOL até a ESPADA,
            // não criam um círculo branco/emissivo ou PNG radial na arma.
            if(uSwordRays>.001 && uSunVisible>.001){
                vec2 beamVector=uSwordTipUv-uSunUv;
                float beamLen=dot(beamVector,beamVector);
                float along=clamp(dot(vUv-uSunUv,beamVector)/max(beamLen,.0001),0.0,1.0);
                vec2 beamPoint=uSunUv+beamVector*along;
                float transverse=length(vUv-beamPoint);
                float width=mix(.032,.004,along);
                float shape=exp(-pow(transverse/max(width,.0007),2.0));
                float taper=smoothstep(.0,.09,along)*(1.0-smoothstep(.95,1.02,along));
                float sourceOpen=(skyOpen(uSunUv)+skyOpen(mix(uSunUv,uSwordTipUv,.28)))*.5;
                float shaft=shape*taper*uSwordRays*uSunVisible*(.36+.64*sourceOpen);
                color+=vec3(1.34,1.18,1.08)*shaft;
            }
            // Rastro branco temporário da lâmina ao executar o golpe.
            if(uSwordSwing>.005){
                vec2 pivot=vec2(.86,.08);
                vec2 a=vUv-pivot;
                vec2 b=uSwordTipUv-pivot;
                float radial=length(a);
                float r=length(b);
                float angleA=atan(a.y,a.x),angleB=atan(b.y,b.x);
                float sep=abs(atan(sin(angleA-angleB),cos(angleA-angleB)));
                float ribbon=exp(-pow((radial-r)/.013,2.0))*(1.0-smoothstep(.03,1.04,sep));
                color+=vec3(1.26,1.32,1.42)*ribbon*uSwordSwing*.73;
            }
            color=color/(color+vec3(1.0));
            color=pow(color,vec3(.85));
            // The night remains dark; the local white illumination is added per material.
            color*=mix(1.0,.68,uEclipse);
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

    // Solas 0.3: vaga-lumes PS1, emissivos e sutis, sem shadow map ou textura.
    static final String FIREFLY_V = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec4 aPositionPhase;
        uniform mat4 uMvp;
        uniform vec3 uEye;
        uniform float uTime,uNight;
        out float vAlpha,vHue;
        void main(){
            vec3 p=aPositionPhase.xyz;
            float phase=aPositionPhase.w;
            p.x+=sin(uTime*.63+phase)*.65;
            p.z+=cos(uTime*.49+phase*1.31)*.64;
            p.y+=sin(uTime*.88+phase*1.67)*.29;
            float dist=distance(p,uEye);
            gl_Position=uMvp*vec4(p,1.0);
            float flicker=.55+.45*pow(max(0.0,sin(uTime*2.1+phase*3.8)),2.0);
            vAlpha=uNight*(1.0-smoothstep(28.0,43.0,dist))*flicker;
            vHue=fract(phase*.37);
            gl_PointSize=clamp(5.5-0.06*dist,1.5,5.5);
        }
        """;
    static final String FIREFLY_F = """
        #version 300 es
        precision mediump float;
        in float vAlpha,vHue;
        out vec4 outColor;
        void main(){
            float r=length(gl_PointCoord-.5)*2.0;
            float core=1.0-smoothstep(.08,.49,r);
            float glow=1.0-smoothstep(.30,.98,r);
            vec3 hue=mix(vec3(.95,.95,.45),vec3(.56,.99,.35),vHue);
            float alpha=vAlpha*(core*.92+glow*.18);
            if(alpha<.035)discard;
            outColor=vec4(hue,alpha);
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
        uniform float uWindTime,uLeaf;
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
            float leafDistance=distance(aLocation.xz,uEye.xz);
            if(uLeaf>.5 && leafDistance<15.0){
                float fade=1.0-smoothstep(9.0,15.0,leafDistance);
                float tip=smoothstep(.4,3.5,aPosition.y);
                float wave=uWindTime*1.37+aLocation.x*.17+aLocation.z*.24;
                world.x+=sin(wave+aPosition.y*.27)*.088*tip*fade;
                world.z+=cos(wave*.77+aPosition.x*.30)*.064*tip*fade;
            }
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
        uniform float uEclipse;
        uniform float uLeaf;
        uniform float uSwordGlow;
        uniform vec3 uSwordLightPos;
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
        uniform vec4 uSwordOccluders[12];
        float swordPointShadow(vec3 pos){
            vec3 ray=pos-uSwordLightPos;
            float rayLength=length(ray);
            if(rayLength<.12)return 1.0;
            vec3 dir=ray/max(rayLength,.001);
            float visibility=1.0;
            for(int i=0;i<12;i++){
                vec4 blocker=uSwordOccluders[i];
                if(blocker.w<=.05)continue;
                vec3 center=blocker.xyz-uSwordLightPos;
                float hit=dot(center,dir);
                if(hit<.08 || hit>rayLength-.10)continue;
                float separation=length(center-dir*hit);
                float pen=1.0-smoothstep(blocker.w*.72,blocker.w*1.20,separation);
                visibility*=1.0-.86*pen;
            }
            return clamp(visibility,.12,1.0);
        }
        vec3 swordPointLight(vec3 point,vec3 normal,vec3 albedo){
            if(uSwordGlow<=.012)return vec3(0.0);
            vec3 delta=uSwordLightPos-point;
            float distanceLight=length(delta);
            if(distanceLight>=5.0)return vec3(0.0);
            float falloff=pow(max(0.0,1.0-distanceLight/5.0),2.0);
            float ndotl=max(.10,dot(normalize(normal),delta/max(distanceLight,.001)));
            float shadow=swordPointShadow(point);
            return albedo*vec3(5.5,5.5,5.7)*uSwordGlow*falloff*ndotl*shadow;
        }

        float solasCloudHash(vec2 p){
            p=fract(p*vec2(123.34,456.21));
            p+=dot(p,p+45.32);
            return fract(p.x*p.y);
        }
        float cloudShadowMask(vec2 wp){
            vec2 q=floor((wp*0.0105 + vec2(.17,-.11))*26.0)/26.0;
            float n=solasCloudHash(floor(q*41.0));
            float n2=solasCloudHash(floor(q*17.0)+13.7);
            float c=smoothstep(.56,.86,n*.7+n2*.3);
            return mix(1.0,0.82,c);
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

            color=mix(color,color*vec3(.095,.16,.30),uEclipse*.96);
            // PointLight em tempo real com oclusão das pedras e troncos.
            color+=swordPointLight(vWorld,normalize(vNormal),tex.rgb);
            // solpixel-v08: nevoa tambem nas folhas GLB originais.
            float fog=smoothstep(uDrawDistance*.28,uDrawDistance*.90,vDist)*.95;
            color=mix(color,vec3(.82,.92,1.0),fog);
            float dither=mod(floor(gl_FragCoord.x)+2.0*mod(floor(gl_FragCoord.y),2.0),4.0)/4.0;
            color=floor(clamp(color,0.0,1.0)*63.0+dither*.45)/63.0;
            color *= cloudShadowMask(vWorld.xz);
            
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
        out vec3 vWorld;
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
            vWorld=world;
            vShade=aInstanceExtra.w;
            vKind=int(n);
        }
        """;
    static final String GRASS_F = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        in float vDist,vShade;
        in vec3 vWorld;
        flat in int vKind;
        uniform sampler2D uGrassAtlas;
        uniform vec3 uGrassTint;
        uniform float uGrassLimit,uEclipse,uSwordGlow;
        uniform vec3 uSwordLightPos;
        out vec4 outColor;
        uniform vec4 uSwordOccluders[12];
        float swordPointShadow(vec3 pos){
            vec3 ray=pos-uSwordLightPos;
            float rayLength=length(ray);
            if(rayLength<.12)return 1.0;
            vec3 dir=ray/max(rayLength,.001);
            float visibility=1.0;
            for(int i=0;i<12;i++){
                vec4 blocker=uSwordOccluders[i];
                if(blocker.w<=.05)continue;
                vec3 center=blocker.xyz-uSwordLightPos;
                float hit=dot(center,dir);
                if(hit<.08 || hit>rayLength-.10)continue;
                float separation=length(center-dir*hit);
                float pen=1.0-smoothstep(blocker.w*.72,blocker.w*1.20,separation);
                visibility*=1.0-.86*pen;
            }
            return clamp(visibility,.12,1.0);
        }
        vec3 swordPointLight(vec3 point,vec3 normal,vec3 albedo){
            if(uSwordGlow<=.012)return vec3(0.0);
            vec3 delta=uSwordLightPos-point;
            float distanceLight=length(delta);
            if(distanceLight>=5.0)return vec3(0.0);
            float falloff=pow(max(0.0,1.0-distanceLight/5.0),2.0);
            float ndotl=max(.10,dot(normalize(normal),delta/max(distanceLight,.001)));
            float shadow=swordPointShadow(point);
            return albedo*vec3(5.5,5.5,5.7)*uSwordGlow*falloff*ndotl*shadow;
        }
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

            color=mix(color,color*vec3(.095,.16,.30),uEclipse*.96);
            // PointLight em tempo real com oclusão das pedras e troncos.
            color+=swordPointLight(vWorld,vec3(0.0,1.0,0.0),tex.rgb);
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
        uniform vec3 uCloudCenter,uEye;
        out vec3 vLocal,vNormal;
        out float vElevation,vCloudDistance;
        void main(){
            vec3 pos=(aPos-uCloudCenter)*uCloudPosScale.w+uCloudPosScale.xyz;
            vLocal=aPos;vNormal=aNormal;
            vElevation=pos.y;
            vCloudDistance=distance(pos,uEye);
            gl_Position=uMvp*vec4(pos,1.0);
        }
        """;
    // SOLAS_V053_TOON_CLOUDS_3_TONES
    // ColorRamp Constant: tres cores, sem degradê entre sombra, base e luz.
    static final String CLOUD_F = """
        #version 300 es
        precision highp float;
        in vec3 vLocal,vNormal;
        in float vElevation,vCloudDistance;
        uniform sampler2D uCloudMask;
        uniform vec3 uSun;
        uniform float uAlpha,uEclipse;
        out vec4 outColor;
        float bayer4(vec2 pixel){
            const float b[16]=float[16](0.,8.,2.,10.,12.,4.,14.,6.,3.,11.,1.,9.,15.,7.,13.,5.);
            ivec2 p=ivec2(mod(floor(pixel),4.0));
            return (b[p.x+4*p.y]+.5)/16.0;
        }
        void main(){
            vec3 n=normalize(vNormal);
            vec3 sun=normalize(uSun);
            vec2 uv=abs(n.y)>.5?vLocal.xz:(abs(n.x)>.5?vLocal.zy:vLocal.xy);
            vec2 uvPixel=floor(uv*72.0)/72.0;
            float mask=texture(uCloudMask,uvPixel*.82).r;

            // Corrigido: faces voltadas ao sol e o topo ficam mais claros.
            float sunFacing=max(0.0,dot(n,sun));
            float opposite=max(0.0,-dot(n,sun));
            float top=max(0.0,n.y);
            float bottom=max(0.0,-n.y);
            float baseShade=smoothstep(-22.0,6.0,-vElevation);
            float exposure=0.20 + sunFacing*0.54 + top*0.30;
            exposure -= opposite*0.28 + bottom*0.34 + baseShade*0.14;
            exposure += (mask-.5)*0.03;

            vec3 dayShadow=vec3(.60,.67,.77);
            vec3 dayMid=vec3(.86,.92,.98);
            vec3 dayLight=vec3(1.0,1.0,1.0);
            vec3 nightShadow=vec3(.12,.16,.22);
            vec3 nightMid=vec3(.34,.40,.50);
            vec3 nightLight=vec3(.56,.62,.70);
            float night=smoothstep(.10,.98,uEclipse);
            vec3 shadow=mix(dayShadow,nightShadow,night);
            vec3 mid=mix(dayMid,nightMid,night);
            vec3 light=mix(dayLight,nightLight,night);

            float midStep=step(.36,exposure);
            float lightStep=step(.72,exposure);
            vec3 color=mix(shadow,mid,midStep);
            color=mix(color,light,lightStep);

            // Dithering apenas na borda/fade das nuvens, nao no preenchimento inteiro.
            float visible=clamp(uAlpha,0.0,1.0);
            float nearFade=smoothstep(18.0,34.0,vCloudDistance);
            float farFade=1.0-smoothstep(3300.0,3700.0,vCloudDistance);
            float visibility=visible*nearFade*farFade;
            if(visibility<0.03) discard;
            if(visibility<0.985){
                float d=bayer4(gl_FragCoord.xy*0.5);
                if(d>visibility) discard;
            }
            outColor=vec4(color,1.0);
        }
        """;
}
