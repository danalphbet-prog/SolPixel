package com.solpixel.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class GameRenderer implements GLSurfaceView.Renderer {
    private final Context context;
    private final GameActivity game;
    private int meshP,skyP,brightP,blurP,finalP,dustP,groundTexture;
    private int emptyVao;
    private Target scene,bloomA,bloomB;
    private MeshGpu ground,objects,shadows;
    private TreeModel broadleaf,pines;
    private World.Decoration activeDecoration;
    private int treeP,skyTexture;
    private final ExecutorService worldWorker=Executors.newSingleThreadExecutor();
    private volatile BuildResult completedWorld;
    private volatile boolean buildingWorld;
    private final float[] collisionPoint=new float[2];
    private static final class BuildResult {
        final float x,z;final World.Mesh ground;final World.Decoration decoration;
        BuildResult(float x,float z,World.Mesh m,World.Decoration d){
            this.x=x;this.z=z;this.ground=m;this.decoration=d;
        }
    }
    private int dustVao,dustVbo,dustCount;
    private final float[] proj=new float[16],view=new float[16],vp=new float[16];
    private final float[] forward=new float[3],right=new float[3],up=new float[3];
    private static final float[] SUN={.53f,.70f,-.47f};
    private int screenW=1,screenH=1,sceneW=1,sceneH=1;
    private float baseX=99999,baseZ=99999;
    private volatile float stickX=0,stickY=0;
    private volatile boolean jumpPressed=false,resetRequested=true;
    private float lookDX=0,lookDY=0;
    private float x=0,z=0,yaw=.45f,pitch=.10f,airHeight=0,vy=0,time=0,step=0;
    private long lastFrame;
    GameRenderer(GameActivity game){this.context=game;this.game=game;}

    void move(float vx,float vy){stickX=vx;stickY=vy;}
    void jump(){jumpPressed=true;}
    void reset(){resetRequested=true;}
    synchronized void look(float dx,float dy){lookDX+=dx;lookDY+=dy;}
    private synchronized float[] consumeLook(){
        float[] out={lookDX,lookDY};lookDX=lookDY=0;return out;
    }
    @Override public void onSurfaceCreated(GL10 unused,EGLConfig config){
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        GLES30.glClearColor(.57f,.69f,.75f,1);
        meshP=program(Shaders.MESH_V,Shaders.MESH_F);
        skyP=program(Shaders.FULL_V,Shaders.SKY_F);
        brightP=program(Shaders.FULL_V,Shaders.BRIGHT_F);
        blurP=program(Shaders.FULL_V,Shaders.BLUR_F);
        finalP=program(Shaders.FULL_V,Shaders.FINAL_F);
        dustP=program(Shaders.DUST_V,Shaders.DUST_F);
        treeP=program(Shaders.TREE_V,Shaders.TREE_F);
        int[] tmp=new int[1];GLES30.glGenVertexArrays(1,tmp,0);emptyVao=tmp[0];
        groundTexture=loadTexture();
        skyTexture=loadSkyTexture();
        try {
            broadleaf=new TreeModel(context,"arvore-16.glb");
            pines=new TreeModel(context,"arvore-2.glb");
        } catch(Exception ex){Log.e("SolPixel","Falha carregando arvores GLB",ex);}
        makeDust();
        baseX=99999f;baseZ=99999f;
        lastFrame=System.nanoTime();
    }
    @Override public void onSurfaceChanged(GL10 unused,int width,int height){
        screenW=Math.max(1,width);screenH=Math.max(1,height);
        // 1280px de largura maxima para garantir FPS em aparelhos intermediarios.
        float scale=Math.min(1f,1280f/Math.max(screenW,screenH));
        sceneW=Math.max(16,Math.round(screenW*scale));
        sceneH=Math.max(16,Math.round(screenH*scale));
        scene=Target.make(sceneW,sceneH,true);
        bloomA=Target.make(Math.max(8,sceneW/4),Math.max(8,sceneH/4),false);
        bloomB=Target.make(Math.max(8,sceneW/4),Math.max(8,sceneH/4),false);
        Matrix.perspectiveM(proj,0,67f,sceneW/(float)sceneH,.12f,420f);
    }
    @Override public void onDrawFrame(GL10 unused){
        if(scene==null)return;
        long now=System.nanoTime();
        float dt=Math.min(.075f,Math.max(.001f,(now-lastFrame)*1e-9f));lastFrame=now;
        update(dt);
        // Preparacao procedural fora da GL thread: nao engasga o joystick.
        if(ground==null){
            baseX=Math.round(x/64f)*64f;baseZ=Math.round(z/64f)*64f;
            installWorld(new BuildResult(baseX,baseZ,World.ground(baseX,baseZ),World.decorate(baseX,baseZ)));
        } else {
            BuildResult ready=completedWorld;
            if(ready!=null){
                completedWorld=null;
                installWorld(ready);
                buildingWorld=false;
            }
            if((Math.abs(x-baseX)>67f||Math.abs(z-baseZ)>67f)&&!buildingWorld){
                final float cx=Math.round(x/64f)*64f,cz=Math.round(z/64f)*64f;
                buildingWorld=true;
                worldWorker.execute(()->{
                    try {completedWorld=new BuildResult(cx,cz,World.ground(cx,cz),World.decorate(cx,cz));}
                    catch(Throwable err){Log.e("SolPixel","Erro construindo terreno",err);buildingWorld=false;}
                });
            }
        }
        float eyeY=World.height(x,z)+1.72f+airHeight;
        if(game.state==1 && airHeight<=.005f){
            eyeY+=(float)Math.sin(step*10.5f)*.025f;
        }
        eyeY=Math.max(eyeY,World.height(x,z)+1.6f);
        float cosP=(float)Math.cos(pitch),sinP=(float)Math.sin(pitch),
              cosY=(float)Math.cos(yaw),sinY=(float)Math.sin(yaw);
        forward[0]=sinY*cosP;forward[1]=sinP;forward[2]=-cosY*cosP;
        right[0]=cosY;right[1]=0;right[2]=sinY;
        up[0]=-sinY*sinP;up[1]=cosP;up[2]=cosY*sinP;
        Matrix.setLookAtM(view,0,x,eyeY,z,x+forward[0],eyeY+forward[1],z+forward[2],0,1,0);
        Matrix.multiplyMM(vp,0,proj,0,view,0);
        drawScene(eyeY);
        bloom();
        composite();
    }
    private void update(float dt){
        time+=dt;
        if(resetRequested){
            x=0;z=0;pitch=.08f;yaw=.48f;airHeight=0;vy=0;step=0;
            jumpPressed=false;stickX=stickY=0;resetRequested=false;
            consumeLook();
        }
        float[] drag=consumeLook();
        if(game.state!=1)return;
        yaw+=drag[0]*.0040f;
        pitch=Math.max(-1.30f,Math.min(1.30f,pitch-drag[1]*.0034f));
        float mx=stickX,my=stickY;
        float length=(float)Math.hypot(mx,my);
        if(length>.06f){
            float speed=length>.92f?7.3f:5.45f;
            float c=(float)Math.cos(yaw),s=(float)Math.sin(yaw);
            float targetX=x+(c*mx+s*my)*speed*dt;
            float targetZ=z+(s*mx-c*my)*speed*dt;
            collisionPoint[0]=targetX;collisionPoint[1]=targetZ;
            // Apenas troncos das 6 arvores mais proximas; movimento desliza nas bordas.
            World.collide(collisionPoint,.48f,activeDecoration);
            float oldX=x,oldZ=z;
            x=collisionPoint[0];z=collisionPoint[1];
            step+=(float)Math.hypot(x-oldX,z-oldZ);
        }
        if(jumpPressed){
            if(airHeight<=.001f){vy=6.6f;airHeight=.003f;}
            jumpPressed=false;
        }
        if(airHeight>0||vy>0){
            vy-=16.5f*dt;
            airHeight+=vy*dt;
            if(airHeight<0){airHeight=0;vy=0;}
        }
    }
    private void drawScene(float eyeY){
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,scene.fbo);
        GLES30.glViewport(0,0,sceneW,sceneH);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT|GLES30.GL_DEPTH_BUFFER_BIT);
        // Fundo e sol como direcoes 3D reais (acompanham a camera).
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);
        GLES30.glUseProgram(skyP);
        v3(skyP,"uForward",forward);v3(skyP,"uRight",right);v3(skyP,"uUp",up);v3(skyP,"uSun",SUN);
        f1(skyP,"uAspect",sceneW/(float)sceneH);
        f1(skyP,"uFov",(float)Math.tan(Math.toRadians(67)/2));
        texture(3,skyTexture);i1(skyP,"uSkybox",3);
        f1(skyP,"uSkyAvailable",skyTexture==0?0f:1f);
        fullTriangle();
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(true);
        GLES30.glUseProgram(meshP);
        GLES30.glUniformMatrix4fv(loc(meshP,"uMvp"),1,false,vp,0);
        GLES30.glUniform3f(loc(meshP,"uEye"),x,eyeY,z);
        v3(meshP,"uSun",SUN);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,groundTexture);
        i1(meshP,"uGround",0);
        ground.draw();
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glDepthMask(false);
        shadows.draw();
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        objects.draw();
        // Duas especies GLB, geometria compartilhada; instancing evita centenas de draws.
        if(activeDecoration!=null && broadleaf!=null && pines!=null){
            GLES30.glUseProgram(treeP);
            GLES30.glUniformMatrix4fv(loc(treeP,"uMvp"),1,false,vp,0);
            GLES30.glUniform3f(loc(treeP,"uEye"),x,eyeY,z);
            v3(treeP,"uSun",SUN);
            broadleaf.draw(treeP,activeDecoration.broadleafInstances);
            pines.draw(treeP,activeDecoration.pineInstances);
        }
        // Poeira iluminada flutuante, sprites de ponto com textura radial.
        GLES30.glUseProgram(dustP);
        GLES30.glUniformMatrix4fv(loc(dustP,"uMvp"),1,false,vp,0);
        GLES30.glUniform3f(loc(dustP,"uEye"),x,eyeY,z);
        f1(dustP,"uTime",time);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE);
        GLES30.glDepthMask(false);
        GLES30.glBindVertexArray(dustVao);
        GLES30.glDrawArrays(GLES30.GL_POINTS,0,dustCount);
        GLES30.glBindVertexArray(0);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
    }
    private void bloom(){
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        // Bright pass: extrai somente pixels claros.
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,bloomA.fbo);
        GLES30.glViewport(0,0,bloomA.w,bloomA.h);
        GLES30.glUseProgram(brightP);
        texture(0,scene.color);i1(brightP,"uScene",0);
        GLES30.glUniform2f(loc(brightP,"uTexel"),1f/sceneW,1f/sceneH);
        fullTriangle();
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,bloomB.fbo);
        GLES30.glUseProgram(blurP);
        texture(0,bloomA.color);i1(blurP,"uInput",0);
        GLES30.glUniform2f(loc(blurP,"uDirection"),1f/bloomA.w,0);
        fullTriangle();
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,bloomA.fbo);
        texture(0,bloomB.color);
        GLES30.glUniform2f(loc(blurP,"uDirection"),0,1f/bloomA.h);
        fullTriangle();
    }
    private void composite(){
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
        GLES30.glViewport(0,0,screenW,screenH);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glUseProgram(finalP);
        texture(0,scene.color);texture(1,scene.depth);texture(2,bloomA.color);
        i1(finalP,"uScene",0);i1(finalP,"uDepth",1);i1(finalP,"uBloom",2);
        GLES30.glUniform2f(loc(finalP,"uTexel"),1f/sceneW,1f/sceneH);
        float aspect=sceneW/(float)sceneH;
        float dx=SUN[0]*right[0]+SUN[1]*right[1]+SUN[2]*right[2];
        float dy=SUN[0]*up[0]+SUN[1]*up[1]+SUN[2]*up[2];
        float dz=SUN[0]*forward[0]+SUN[1]*forward[1]+SUN[2]*forward[2];
        float fov=(float)Math.tan(Math.toRadians(67)/2);
        float ux=dz>.05f?.5f+.5f*dx/(dz*fov*aspect):-5f;
        float uy=dz>.05f?.5f+.5f*dy/(dz*fov):-5f;
        GLES30.glUniform2f(loc(finalP,"uSunUv"),ux,uy);
        f1(finalP,"uSunVisible",dz>.05f && ux>-.2f&&ux<1.2f&&uy>-.2f&&uy<1.2f?1:0);
        fullTriangle();
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }
    private void installWorld(BuildResult result){
        baseX=result.x;baseZ=result.z;
        if(ground!=null)ground.destroy();
        if(objects!=null)objects.destroy();
        if(shadows!=null)shadows.destroy();
        ground=new MeshGpu(result.ground);
        objects=new MeshGpu(result.decoration.objects);
        shadows=new MeshGpu(result.decoration.shadows);
        activeDecoration=result.decoration;
    }
    private int loadSkyTexture(){
        try(InputStream input=context.getAssets().open("skybox_horizon.png")){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
            Bitmap bitmap=BitmapFactory.decodeStream(input,null,options);
            if(bitmap==null)return 0;
            int[] id=new int[1];GLES30.glGenTextures(1,id,0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,id[0]);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_REPEAT);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR_MIPMAP_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,bitmap,0);
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);bitmap.recycle();return id[0];
        }catch(Exception ex){Log.w("SolPixel","Skybox indisponivel: "+ex);return 0;}
    }
    private int loadTexture(){
        int[] t=new int[1];GLES30.glGenTextures(1,t,0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,t[0]);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_MIRRORED_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_MIRRORED_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST_MIPMAP_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
        try(InputStream input=context.getAssets().open("ground.jpg")){
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inScaled=false;
            Bitmap b=BitmapFactory.decodeStream(input,null,opts);
            if(b==null)throw new IllegalStateException("Imagem do solo nao carregou");
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,b,0);
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
            b.recycle();
        }catch(Exception ex){throw new RuntimeException("Falha na textura do terreno",ex);}
        return t[0];
    }
    private void makeDust(){
        Random r=new Random(62519);
        dustCount=185;
        FloatBuffer seeds=ByteBuffer.allocateDirect(dustCount*3*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for(int i=0;i<dustCount;i++){
            seeds.put(r.nextFloat()*2-1).put(r.nextFloat()).put(r.nextFloat()*2-1);
        }
        seeds.position(0);
        int[] a=new int[1],b=new int[1];
        GLES30.glGenVertexArrays(1,a,0);GLES30.glGenBuffers(1,b,0);
        dustVao=a[0];dustVbo=b[0];
        GLES30.glBindVertexArray(dustVao);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,dustVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,dustCount*12,seeds,GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,12,0);
        GLES30.glBindVertexArray(0);
    }
    private void fullTriangle(){GLES30.glBindVertexArray(emptyVao);GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);GLES30.glBindVertexArray(0);}
    private static int loc(int p,String s){return GLES30.glGetUniformLocation(p,s);}
    private static void i1(int p,String s,int v){GLES30.glUniform1i(loc(p,s),v);}
    private static void f1(int p,String s,float v){GLES30.glUniform1f(loc(p,s),v);}
    private static void v3(int p,String s,float[] v){GLES30.glUniform3fv(loc(p,s),1,v,0);}
    private static void texture(int slot,int id){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+slot);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,id);}
    private static int compile(int type,String src){
        int shader=GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader,src);GLES30.glCompileShader(shader);
        int[] result=new int[1];GLES30.glGetShaderiv(shader,GLES30.GL_COMPILE_STATUS,result,0);
        if(result[0]==0){String err=GLES30.glGetShaderInfoLog(shader);Log.e("SolPixel","GLSL: "+err);throw new IllegalStateException("Shader GLSL: "+err);}
        return shader;
    }
    private static int program(String vertex,String fragment){
        int v=compile(GLES30.GL_VERTEX_SHADER,vertex),f=compile(GLES30.GL_FRAGMENT_SHADER,fragment);
        int p=GLES30.glCreateProgram();GLES30.glAttachShader(p,v);GLES30.glAttachShader(p,f);
        GLES30.glLinkProgram(p);
        int[] status=new int[1];GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,status,0);
        GLES30.glDeleteShader(v);GLES30.glDeleteShader(f);
        if(status[0]==0)throw new IllegalStateException("Link GLSL: "+GLES30.glGetProgramInfoLog(p));
        return p;
    }
    static final class MeshGpu {
        int vao,vbo,ibo,count;
        MeshGpu(World.Mesh mesh){
            count=mesh.indices.length;
            int[] a=new int[1],b=new int[2];
            GLES30.glGenVertexArrays(1,a,0);GLES30.glGenBuffers(2,b,0);
            vao=a[0];vbo=b[0];ibo=b[1];
            FloatBuffer f=ByteBuffer.allocateDirect(mesh.vertices.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            f.put(mesh.vertices).position(0);
            ShortBuffer ix=ByteBuffer.allocateDirect(count*2).order(ByteOrder.nativeOrder()).asShortBuffer();
            ix.put(mesh.indices).position(0);
            GLES30.glBindVertexArray(vao);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,mesh.vertices.length*4,f,GLES30.GL_STATIC_DRAW);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,ibo);
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,count*2,ix,GLES30.GL_STATIC_DRAW);
            int stride=9*4;
            for(int i=0;i<4;i++)GLES30.glEnableVertexAttribArray(i);
            GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,stride,0);
            GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,stride,12);
            GLES30.glVertexAttribPointer(2,2,GLES30.GL_FLOAT,false,stride,24);
            GLES30.glVertexAttribPointer(3,1,GLES30.GL_FLOAT,false,stride,32);
            GLES30.glBindVertexArray(0);
        }
        void draw(){if(count<=0)return;GLES30.glBindVertexArray(vao);GLES30.glDrawElements(GLES30.GL_TRIANGLES,count,GLES30.GL_UNSIGNED_SHORT,0);GLES30.glBindVertexArray(0);}
        void destroy(){int[] a={vao},b={vbo,ibo};GLES30.glDeleteVertexArrays(1,a,0);GLES30.glDeleteBuffers(2,b,0);}
    }
    static final class Target {
        int fbo,color,depth,w,h;
        static Target make(int w,int h,boolean hasDepth){
            Target t=new Target();t.w=w;t.h=h;
            int[] id=new int[1];
            GLES30.glGenFramebuffers(1,id,0);t.fbo=id[0];
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,t.fbo);
            GLES30.glGenTextures(1,id,0);t.color=id[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,t.color);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGBA8,w,h,0,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,null);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_COLOR_ATTACHMENT0,GLES30.GL_TEXTURE_2D,t.color,0);
            if(hasDepth){
                GLES30.glGenTextures(1,id,0);t.depth=id[0];
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,t.depth);
                GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_DEPTH_COMPONENT24,w,h,0,
                    GLES30.GL_DEPTH_COMPONENT,GLES30.GL_UNSIGNED_INT,null);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_DEPTH_ATTACHMENT,GLES30.GL_TEXTURE_2D,t.depth,0);
            }
            int s=GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
            if(s!=GLES30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Framebuffer incompleto: "+Integer.toHexString(s));
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
            return t;
        }
    }
}
