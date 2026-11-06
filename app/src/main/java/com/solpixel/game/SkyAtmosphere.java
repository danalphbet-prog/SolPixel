package com.solpixel.game;

/**
 * Solas v0.5.5: ceu procedural GLSL ES 3.0 para OpenGL ES (Android).
 * Nao e ShaderLab/CG: compilar com Shaders.FULL_V no GameRenderer.
 * O Bayer 4x4 opera nos gradientes do ceu; nao interfere na textura lunar.
 */
final class SkyAtmosphere {
    private SkyAtmosphere() {}

    static final String SKY_F = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform vec3 uForward,uRight,uUp,uSun,uMoonDir;
        uniform float uAspect,uFov,uEclipse,uNight,uSkyTime,uDitherStrength;
        uniform float uSkyGrain;
        uniform sampler2D uStarMap,uMoonTex;
        out vec4 outColor;
        const float PI=3.141592653589793;

        struct Palette {
            vec3 inner,outer,horizon,zenith,nadir,opposite;
        };
        Palette dawnPalette(){
            return Palette(vec3(1.00,.95,.55),vec3(1.00,.62,.78),
                vec3(1.00,.60,.30),vec3(.45,.72,.96),
                vec3(.30,.22,.32),vec3(.40,.60,.90));
        }
        Palette noonPalette(){
            return Palette(vec3(1.00,1.00,.96),vec3(1.00,.88,.55),
                vec3(.70,.92,1.00),vec3(.04,.24,.68),
                vec3(.25,.36,.42),vec3(.04,.24,.68));
        }
        Palette duskPalette(){
            return Palette(vec3(1.00,.33,.10),vec3(1.00,.18,.60),
                vec3(1.00,.52,.08),vec3(.32,.10,.48),
                vec3(.16,.10,.20),vec3(.05,.07,.26));
        }
        Palette nightPalette(){
            return Palette(vec3(.62,.70,.90),vec3(.10,.14,.28),
                vec3(.05,.10,.30),vec3(.01,.02,.06),
                vec3(.00,.00,.02),vec3(.01,.02,.06));
        }
        Palette blendPalette(Palette a,Palette b,float t){
            return Palette(mix(a.inner,b.inner,t),mix(a.outer,b.outer,t),
                mix(a.horizon,b.horizon,t),mix(a.zenith,b.zenith,t),
                mix(a.nadir,b.nadir,t),mix(a.opposite,b.opposite,t));
        }
        float hash11(float n){return fract(sin(n*83.173+13.71)*43758.5453);}
        vec2 skyUV(vec3 dir){
            return vec2(fract(.5+atan(dir.z,dir.x)/(2.0*PI)),
                .5-asin(clamp(dir.y,-1.0,1.0))/PI);
        }
        // Matriz Bayer 4x4 ordenada: pontinhos fixos, nao ruido temporal.
        float bayer4(vec2 pixel){
            const float b[16]=float[16](0.,8.,2.,10.,12.,4.,14.,6.,3.,11.,1.,9.,15.,7.,13.,5.);
            ivec2 p=ivec2(mod(floor(pixel),4.0));
            return (b[p.x+4*p.y]+.5)/16.0;
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
            vec3 sunDir=normalize(uSun);
            float sunDot=clamp(dot(dir,sunDir),-1.0,1.0);
            float sunAngle=sqrt(max(0.0,2.0*(1.0-sunDot)));

            // Quatro paletas, transicoes continuas mesmo no limite do dia.
            float sunrise=step(0.0,sunDir.x);
            float horizonWarm=(1.0-smoothstep(.08,.65,abs(sunDir.y)))*(1.0-uNight);
            Palette dawn=dawnPalette(),noon=noonPalette(),dusk=duskPalette();
            Palette warm=blendPalette(dusk,dawn,sunrise);
            Palette day=blendPalette(noon,warm,horizonWarm);
            float darkness=clamp(max(uNight,uEclipse*.97),0.0,1.0);
            Palette p=blendPalette(day,nightPalette(),darkness);

            float hy=dir.y;
            float band=pow(1.0-clamp(abs(hy)/.30,0.0,1.0),3.0);
            float opposite=clamp(-sunDot,0.0,1.0)*(1.0-uNight);
            vec3 zenith=mix(p.zenith,p.opposite,opposite*.40);
            vec3 upper=mix(p.horizon,zenith,pow(clamp(hy,0.0,1.0),.60));
            vec3 lower=mix(p.horizon,p.nadir,pow(clamp(-hy,0.0,1.0),.50));
            vec3 sky=mix(lower,upper,step(0.0,hy));
            sky=mix(sky,p.horizon,band);

            // Gradiente radial de luz solar e tonalidade local ao amanhecer/ocaso.
            float glowMask=1.0-smoothstep(0.0,.55,sunAngle);
            vec3 glowColor=mix(p.inner,p.outer,smoothstep(0.0,.55,sunAngle));
            float sunVisible=smoothstep(-.035,.025,sunDir.y);
            float glowPower=1.10*(1.0-uEclipse*.90)*sunVisible;
            sky+=glowColor*glowMask*glowMask*glowPower;

            // Dither ordenado apenas na cor atmosferica (nao nas estrelas/lua).
            // Interpolacao entre niveis discretos: sem ruido aleatorio ou flickering.
            float dither=bayer4(gl_FragCoord.xy)-.5;
            float levels=48.0;
            float gradientZone=clamp(.33+.50*band+.35*glowMask,0.0,1.0);
            sky=floor(max(sky,vec3(0.0))*levels+.5+
                dither*uDitherStrength*gradientZone)/levels;

            vec2 uv=skyUV(dir);
            vec4 sprite=texture(uStarMap,uv);
            float pStar=max(sprite.r,max(sprite.g,sprite.b))*sprite.a;
            float starId=hash11(floor(uv.x*2048.0)*.73+floor(uv.y*2048.0)*.31);
            float rate=.55+1.45*hash11(starId*31.7);
            float twinkle=smoothstep(-.34,.60,sin(uSkyTime*rate+starId*6.28318));
            vec3 starTint=mix(vec3(1.04,1.04,1.05),vec3(1.22,.52,.48),
                step(.83,hash11(starId*87.7)));
            float starVisibility=darkness*smoothstep(-.20,.05,dir.y);
            sky+=sprite.rgb*starTint*(.8+1.8*pStar)*starVisibility*twinkle;
            sky+=vec3(1.00,1.04,1.15)*pow(pStar,7.0)*.65*starVisibility*twinkle;
            sky+=vec3(1.20,1.34,1.59)*meteor(uv)*starVisibility;

            // Disco solar: aparece desde o nascer ate o poente.
            float solarDisc=smoothstep(.99970,.99986,sunDot)*sunVisible;
            float solarBrightness=(1.0-uEclipse*.91);
            sky=mix(sky,p.inner*1.62*solarBrightness,solarDisc);

            // Coroa radiante na borda da Lua, somente durante eclipse.
            float corona=exp(-pow((sunAngle-.030)*145.0,2.0));
            sky+=vec3(1.25,.77,.38)*corona*uEclipse*uEclipse*.92*sunVisible;

            // Lua real em PNG com transparencia; 10% maior e brilhante a noite.
            vec3 moonDir=normalize(uMoonDir);
            float moonDot=clamp(dot(dir,moonDir),-1.0,1.0);
            float moonAngle=sqrt(max(0.0,2.0*(1.0-moonDot)));
            float moonLight=max(uNight,uEclipse*.80);
            float moonVisible=smoothstep(-.045,.03,moonDir.y);
            float lunarHalo=pow(max(moonDot,0.0),100.0)*.15*moonLight*moonVisible;
            sky+=vec3(.57,.67,.96)*lunarHalo;
            // Eixo fixo perpendicular ao plano orbital: evita giro repentino no zenite.
            vec3 orbitalNormal=vec3(.41460825,0.0,.910);
            vec3 east=normalize(cross(orbitalNormal,moonDir));
            vec3 north=normalize(cross(moonDir,east));
            float moonSize=.052*mix(1.0,1.10,uNight);
            vec2 moonUv=vec2(dot(dir,east),dot(dir,north))/moonSize+.5;
            if(moonAngle<.047 && moonVisible>.001 &&
               all(greaterThanEqual(moonUv,vec2(0.0))) &&
               all(lessThanEqual(moonUv,vec2(1.0)))){
                vec4 moon=texture(uMoonTex,moonUv);
                // Lua palida sob ceu azul; prateada e luminosa na noite.
                vec3 dayMoon=mix(vec3(.78,.84,.94),moon.rgb*vec3(.96,1.00,1.13),.45);
                vec3 nightMoon=moon.rgb*vec3(1.04,1.12,1.27)*1.22;
                vec3 moonColor=mix(dayMoon,nightMoon,uNight);
                // Oclusao solar apenas quando ha eclipse REAL e coincidencia angular.
                float obscured=uEclipse*smoothstep(.9989,.99976,sunDot);
                moonColor=mix(moonColor,vec3(.002,.004,.009),obscured);
                float opacity=mix(.55,.98,uNight);
                opacity=mix(opacity,1.0,uEclipse);
                sky=mix(sky,moonColor,clamp(moon.a*opacity,0.0,1.0));
            }
            // Ruido espacial muito leve, ~10% do grain antigo, apenas no ceu.
            // Baixa frequencia temporal para evitar flicker e consumo adicional de CPU.
            float grain=fract(sin(dot(floor(gl_FragCoord.xy*.65),
                vec2(12.9898,78.233))+floor(uSkyTime*4.0)*.47)*43758.5453)-.5;
            sky+=vec3(grain*.012*uSkyGrain);
            outColor=vec4(max(sky,vec3(0.0)),1.0);
        }
        """;
}
