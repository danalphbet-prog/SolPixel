package com.solpixel.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.util.Log;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/** Animated campfire from two physical logs: GL ES 3.0 light + 13-frame atlas + embers. */
final class CampfireSystem {
    private static final int MAX_FIRES=32, MAX_LIGHTS=4, MAX_SPARKS=256;
    private static final float TWO_PI=(float)(2.0*Math.PI);
    private final ArrayList<Fire> fires=new ArrayList<>();
    private final ArrayList<Spark> sparks=new ArrayList<>();
    private final Random random=new Random(0x51A5C00FL);
    private float time=0f, cameraX=0f,cameraZ=0f;
    private int fireProgram, sparkProgram, quadVao,quadVbo,particleVao,particleVbo,fireTexture;

    private static final class Fire {
        float x,y,z,angle,scale=.58f,ignitedAt=0f;
        int pieces=2,hitCount=0;
        boolean burning=false;
        Fire(float x,float z,float angle){
            this.x=x;this.z=z;this.angle=angle;this.y=World.height(x,z);
        }
    }
    private static final class Spark {
        float x,y,z,vx,vy,vz,age,life,size;
        Spark(float x,float y,float z,float vx,float vy,float vz,float life,float size){
            this.x=x;this.y=y;this.z=z;this.vx=vx;this.vy=vy;this.vz=vz;this.life=life;this.size=size;
        }
    }
    CampfireSystem(Context context) throws Exception {
        fireProgram=link(FIRE_VERTEX,FIRE_FRAGMENT);
        sparkProgram=link(SPARK_VERTEX,SPARK_FRAGMENT);
        try(InputStream in=context.getAssets().open("fire_13frames.png")){
            Bitmap bitmap=BitmapFactory.decodeStream(in);
            if(bitmap==null || bitmap.getWidth()!=832 || bitmap.getHeight()!=32)
                throw new IllegalArgumentException("Sprite fogueira deve ter 832x32 (13 frames)");
            int[] t=new int[1];GLES30.glGenTextures(1,t,0);fireTexture=t[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,fireTexture);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle();
        }
        int[] a=new int[1],b=new int[1];
        GLES30.glGenVertexArrays(1,a,0);quadVao=a[0];
        GLES30.glGenBuffers(1,b,0);quadVbo=b[0];
        FloatBuffer quad=floatBuffer(new float[]{-.5f,0f,.5f,0f,-.5f,1f,.5f,1f});
        GLES30.glBindVertexArray(quadVao);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,quadVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,8*4,quad,GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0,2,GLES30.GL_FLOAT,false,8,0);
        GLES30.glGenVertexArrays(1,a,0);particleVao=a[0];
        GLES30.glGenBuffers(1,b,0);particleVbo=b[0];
        GLES30.glBindVertexArray(particleVao);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,particleVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,MAX_SPARKS*5*4,null,GLES30.GL_DYNAMIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,20,0);
        GLES30.glVertexAttribPointer(1,2,GLES30.GL_FLOAT,false,20,12);
        GLES30.glBindVertexArray(0);
    }
    private static FloatBuffer floatBuffer(float[] values){
        FloatBuffer f=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        return f.put(values).position(0);
    }
    private static int compile(int type,String source){
        int shader=GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader,source);GLES30.glCompileShader(shader);
        int[] ok=new int[1];GLES30.glGetShaderiv(shader,GLES30.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0){String log=GLES30.glGetShaderInfoLog(shader);GLES30.glDeleteShader(shader);throw new IllegalStateException(log);}
        return shader;
    }
    private static int link(String vertex,String fragment){
        int v=compile(GLES30.GL_VERTEX_SHADER,vertex),f=compile(GLES30.GL_FRAGMENT_SHADER,fragment);
        int p=GLES30.glCreateProgram();GLES30.glAttachShader(p,v);GLES30.glAttachShader(p,f);
        GLES30.glLinkProgram(p);GLES30.glDeleteShader(v);GLES30.glDeleteShader(f);
        int[] ok=new int[1];GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,ok,0);
        if(ok[0]==0){String log=GLES30.glGetProgramInfoLog(p);GLES30.glDeleteProgram(p);throw new IllegalStateException(log);}
        return p;
    }
    private static int uniform(int program,String name){return GLES30.glGetUniformLocation(program,name);}

    boolean create(float px,float pz,float fx,float fz){
        if(fires.size()>=MAX_FIRES)return false;
        float length=(float)Math.hypot(fx,fz);
        if(length<.01f){fx=0f;fz=-1f;}else{fx/=length;fz/=length;}
        float x=px+fx*2.5f,z=pz+fz*2.5f;
        return createAt(x,z,(float)Math.atan2(fx,-fz));
    }
    boolean createAt(float x,float z,float angle){
        if(fires.size()>=MAX_FIRES)return false;
        for(Fire existing:fires){
            float dx=x-existing.x,dz=z-existing.z;
            if(dx*dx+dz*dz<1.6f*1.6f)return false;
        }
        fires.add(new Fire(x,z,angle));
        return true;
    }
    void tick(float now,float delta,float playerX,float playerZ){
        time=now;cameraX=playerX;cameraZ=playerZ;
        float dt=Math.max(0f,Math.min(.05f,delta));
        for(Iterator<Spark> it=sparks.iterator();it.hasNext();){
            Spark s=it.next();s.age+=dt;
            if(s.age>=s.life){it.remove();continue;}
            s.x+=s.vx*dt;s.y+=s.vy*dt;s.z+=s.vz*dt;
            s.vx*=.988f;s.vz*=.988f;s.vy-=3.4f*dt;
        }
    }
    /** First attack with glowing sword ignites. Twelve later attacks extinguish. */
    boolean attack(float px,float pz,float fx,float fz,boolean glowing){
        float len=(float)Math.hypot(fx,fz);if(len<.01f)return false;fx/=len;fz/=len;
        Fire chosen=null;float best=Float.POSITIVE_INFINITY;
        for(Fire f:fires){
            if(f.pieces<2)continue;
            float dx=f.x-px,dz=f.z-pz,d=(float)Math.hypot(dx,dz);
            if(d>.1f && d<5.2f && dx*fx+dz*fz>-.25f && Math.abs(dx*fz-dz*fx)<1.55f){
                if(d<best){chosen=f;best=d;}
            }
        }
        if(chosen==null)return false;
        if(!chosen.burning){
            if(!glowing)return false;
            chosen.burning=true;chosen.hitCount=0;chosen.ignitedAt=time;
            emitEmbers(chosen,18);
            return true;
        }
        chosen.hitCount++;
        emitEmbers(chosen,22);
        if(chosen.hitCount>=12){chosen.burning=false;chosen.hitCount=0;}
        return true;
    }
    private void emitEmbers(Fire f,int amount){
        for(int i=0;i<amount && sparks.size()<MAX_SPARKS;i++){
            float theta=random.nextFloat()*TWO_PI;
            float r=random.nextFloat()*.43f;
            float speed=.4f+random.nextFloat()*2.8f;
            sparks.add(new Spark(f.x+(float)Math.cos(theta)*r,
                f.y+.9f+random.nextFloat()*.8f,f.z+(float)Math.sin(theta)*r,
                (float)Math.cos(theta)*speed,1.8f+random.nextFloat()*3.4f,
                (float)Math.sin(theta)*speed,.55f+random.nextFloat()*.95f,
                4f+random.nextFloat()*5f));
        }
    }
    /** Unlit logs may be taken one by one or both at once. */
    int pickup(float px,float pz,float fx,float fz,InventorySlots inventory,boolean all){
        float len=(float)Math.hypot(fx,fz);if(len<.01f)return 0;fx/=len;fz/=len;
        Fire chosen=null;float score=Float.POSITIVE_INFINITY;
        for(Fire f:fires){
            if(f.burning||f.pieces==0)continue;
            float dx=f.x-px,dz=f.z-pz;
            float distance=(float)Math.hypot(dx,dz);
            if(distance>4.7f||dx*fx+dz*fz<-.45f||Math.abs(dx*fz-dz*fx)>1.55f)continue;
            if(distance<score){score=distance;chosen=f;}
        }
        if(chosen==null)return 0;
        int added=inventory.addLogs(all?chosen.pieces:1);
        chosen.pieces-=added;
        if(chosen.pieces<=0)fires.remove(chosen);
        return added;
    }
    float audibleAt(float x,float z){
        float nearest=Float.POSITIVE_INFINITY;
        for(Fire f:fires)if(f.burning){
            float d=(float)Math.hypot(x-f.x,z-f.z);if(d<nearest)nearest=d;
        }
        return nearest>=27f?0f:Math.max(0f,Math.min(1f,(27f-nearest)/23f));
    }
    void bindLights(int program){
        float[] data=new float[MAX_LIGHTS*4];
        int count=0;
        // Only nearest four lit campfires participate in each draw pass.
        float[] distances={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        for(Fire fire:fires){
            if(!fire.burning)continue;
            float d=(fire.x-cameraX)*(fire.x-cameraX)+(fire.z-cameraZ)*(fire.z-cameraZ);
            if(d>45f*45f)continue;
            int slot=-1;for(int i=0;i<MAX_LIGHTS;i++)if(d<distances[i]){slot=i;break;}
            if(slot<0)continue;
            for(int j=MAX_LIGHTS-1;j>slot;j--){distances[j]=distances[j-1];System.arraycopy(data,(j-1)*4,data,j*4,4);}
            distances[slot]=d;data[slot*4]=fire.x;
            data[slot*4+1]=fire.y+1f;data[slot*4+2]=fire.z;
            data[slot*4+3]=.94f+.06f*(float)Math.sin(time*15f+fire.x*.7f);
            if(count<MAX_LIGHTS)count++;
        }
        GLES30.glUniform1i(uniform(program,"uFireCount"),count);
        if(count>0)GLES30.glUniform4fv(uniform(program,"uFirePositions[0]"),count,data,0);
        GLES30.glUniform1f(uniform(program,"uLogMaterial"),0f);
    }
    /** Log bodies from supplied 6-sided and 4-sided models. Sideways, slightly overlapping. */
    void drawLogs(int treeProgram,TreeModel near,TreeModel far,float eyeX,float eyeZ){
        int n=0,m=0;
        for(Fire fire:fires){float d=(float)Math.hypot(fire.x-eyeX,fire.z-eyeZ);if(d<14f)n+=fire.pieces;else if(d<80f)m+=fire.pieces;}
        float[] close=new float[n*8],distant=new float[m*8];int ci=0,di=0;
        for(Fire fire:fires){
            float d=(float)Math.hypot(fire.x-eyeX,fire.z-eyeZ);if(d>=80f)continue;
            for(int k=0;k<fire.pieces;k++){
                float off=(k==0?-.62f:.62f)*fire.scale;
                float dx=(float)Math.cos(fire.angle)*off;
                float dz=(float)Math.sin(fire.angle)*off;
                float[] dst=d<14f?close:distant;int at=d<14f?ci:di;
                dst[at]=fire.x+dx;dst[at+1]=World.height(fire.x+dx,fire.z+dz)+fire.scale*.90f+(k==1?fire.scale*.28f:0f);
                dst[at+2]=fire.z+dz;dst[at+3]=fire.scale;
                dst[at+4]=(float)Math.cos(fire.angle);dst[at+5]=(float)Math.sin(fire.angle);
                dst[at+6]=.25f;dst[at+7]=1.5707963f;
                if(d<14f)ci+=8;else di+=8;
            }
        }
        GLES30.glUniform1f(uniform(treeProgram,"uLogMaterial"),1f);
        if(ci>0)near.draw(treeProgram,close);
        if(di>0)far.draw(treeProgram,distant);
        GLES30.glUniform1f(uniform(treeProgram,"uLogMaterial"),0f);
    }
    void drawEffects(float[] vp,float[] cameraRight){
        if(fires.isEmpty()&&sparks.isEmpty())return;
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glDepthMask(false);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glUseProgram(fireProgram);
        GLES30.glUniformMatrix4fv(uniform(fireProgram,"uMvp"),1,false,vp,0);
        GLES30.glUniform3fv(uniform(fireProgram,"uRight"),1,cameraRight,0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,fireTexture);
        GLES30.glUniform1i(uniform(fireProgram,"uAtlas"),0);
        GLES30.glBindVertexArray(quadVao);
        for(Fire f:fires){
            if(!f.burning)continue;
            float d=(float)Math.hypot(f.x-cameraX,f.z-cameraZ);if(d>110f)continue;
            float age=Math.max(0f,time-f.ignitedAt);
            float grow=Math.min(1f,age/.88f);grow=grow*grow*(3f-2f*grow);
            float size=(.12f+.88f*grow)*f.scale*3f*1.2f;
            GLES30.glUniform3f(uniform(fireProgram,"uCenter"),f.x,f.y+f.scale*.48f,f.z);
            GLES30.glUniform2f(uniform(fireProgram,"uSize"),size*1.85f,size);
            GLES30.glUniform1f(uniform(fireProgram,"uFrame"),(float)((int)(age*11.0f)%13));
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4);
        }
        GLES30.glBindVertexArray(0);
        if(!sparks.isEmpty()){
            float[] values=new float[sparks.size()*5];int i=0;
            for(Spark s:sparks){values[i++]=s.x;values[i++]=s.y;values[i++]=s.z;
                values[i++]=1f-s.age/s.life;values[i++]=s.size;}
            GLES30.glUseProgram(sparkProgram);
            GLES30.glUniformMatrix4fv(uniform(sparkProgram,"uMvp"),1,false,vp,0);
            GLES30.glBindVertexArray(particleVao);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,particleVbo);
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER,0,values.length*4,floatBuffer(values));
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE);
            GLES30.glDrawArrays(GLES30.GL_POINTS,0,sparks.size());
            GLES30.glBindVertexArray(0);
        }
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
    }
    private static final String FIRE_VERTEX="""
        #version 300 es
        layout(location=0) in vec2 aQuad;
        uniform mat4 uMvp;
        uniform vec3 uCenter,uRight;
        uniform vec2 uSize;
        uniform float uFrame;
        out vec2 vUv;
        void main(){
            vec3 p=uCenter+uRight*aQuad.x*uSize.x+vec3(0.0,aQuad.y*uSize.y,0.0);
            gl_Position=uMvp*vec4(p,1.0);
            vUv=vec2((uFrame+aQuad.x+.5)/13.0,1.0-aQuad.y);
        }
        """;
    private static final String FIRE_FRAGMENT="""
        #version 300 es
        precision mediump float;
        in vec2 vUv;
        uniform sampler2D uAtlas;
        out vec4 outColor;
        void main(){
            vec4 fire=texture(uAtlas,vUv);
            if(fire.a<.08)discard;
            outColor=vec4(fire.rgb,fire.a*.96);
        }
        """;
    private static final String SPARK_VERTEX="""
        #version 300 es
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec2 aLifeSize;
        uniform mat4 uMvp;
        out float vLife;
        void main(){
            gl_Position=uMvp*vec4(aPos,1.0);
            gl_PointSize=clamp(aLifeSize.y*min(1.0,10.0/max(gl_Position.w,1.0)),2.0,9.0);
            vLife=aLifeSize.x;
        }
        """;
    private static final String SPARK_FRAGMENT="""
        #version 300 es
        precision mediump float;
        in float vLife;
        out vec4 outColor;
        void main(){
            float radius=length(gl_PointCoord-.5)*2.0;
            float a=(1.0-smoothstep(.15,1.0,radius))*vLife;
            if(a<.01)discard;
            outColor=vec4(1.0,.41+.45*vLife,.06,a);
        }
        """;
}
