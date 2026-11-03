package com.solpixel.game;

import android.opengl.GLES30;
import android.opengl.Matrix;
import android.util.Log;

/** Two independent shadow cascades: fixed high-definition near map and distant coverage.
 * Raising distance never enlarges the near-map texel footprint.
 */
final class ForestShadows {
    static final float NEAR_RADIUS=180f;
    static final float NEAR_TRANSITION=130f;
    static final int NEAR_SIZE=1024;
    private static final int FAR_SIZE_PREFERRED=2048;
    private static final int FAR_SIZE_FALLBACK=1024;

    private static final class Target {
        final int texture, framebuffer, size;
        Target(int texture,int framebuffer,int size){this.texture=texture;this.framebuffer=framebuffer;this.size=size;}
    }
    private final int program;
    private final Target near, far;
    final float[] lightVP=new float[16],farLightVP=new float[16];
    private final float[] lightView=new float[16],lightProj=new float[16];
    private float lastNearX=Float.MAX_VALUE,lastNearZ=Float.MAX_VALUE,lastNearY=Float.MAX_VALUE;
    private float lastFarX=Float.MAX_VALUE,lastFarZ=Float.MAX_VALUE,lastFarY=Float.MAX_VALUE;
    private float nearSunX=Float.NaN,nearSunY=Float.NaN,nearSunZ=Float.NaN;
    private float farSunX=Float.NaN,farSunY=Float.NaN,farSunZ=Float.NaN;
    private int nearFrames=100,farFrames=100;
    private float lastFarRange=-1f;
    private World.Decoration nearDecoration,farDecoration;
    private FarWorld farSource;
    private boolean nearValid=false,farValid=false;

    ForestShadows(int depthProgram){
        program=depthProgram;
        near=allocate(NEAR_SIZE);
        Target next;
        try { next=allocate(FAR_SIZE_PREFERRED); }
        catch(RuntimeException ex){
            Log.w("Solas","Sombra distante 2048 indisponivel, tentando 1024",ex);
            try{next=allocate(FAR_SIZE_FALLBACK);}
            catch(RuntimeException fallback){
                Log.w("Solas","Sombra distante indisponivel; mantendo sombras proximas",fallback);
                next=null;
            }
        }
        far=next;
    }

    private static Target allocate(int size){
        int[] ids=new int[1];
        GLES30.glGenTextures(1,ids,0);int tex=ids[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,tex);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_DEPTH_COMPONENT24,
                 size,size,0,GLES30.GL_DEPTH_COMPONENT,GLES30.GL_UNSIGNED_INT,null);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glGenFramebuffers(1,ids,0);int fbo=ids[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,fbo);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_DEPTH_ATTACHMENT,
                 GLES30.GL_TEXTURE_2D,tex,0);
        GLES30.glDrawBuffers(1,new int[]{GLES30.GL_NONE},0);
        GLES30.glReadBuffer(GLES30.GL_NONE);
        int status=GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
        int err=GLES30.glGetError();
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
        if(status!=GLES30.GL_FRAMEBUFFER_COMPLETE||err!=GLES30.GL_NO_ERROR){
            GLES30.glDeleteFramebuffers(1,new int[]{fbo},0);
            GLES30.glDeleteTextures(1,new int[]{tex},0);
            throw new IllegalStateException("Shadow target "+size+" falhou: "+Integer.toHexString(status)+" / "+Integer.toHexString(err));
        }
        return new Target(tex,fbo,size);
    }

    int texture(){return near.texture;}
    int farTexture(){return far!=null?far.texture:0;}
    int farSize(){return far!=null?far.size:NEAR_SIZE;}
    boolean ready(){return nearValid;}
    boolean farReady(){return farValid;}
    float nearDistance(){return NEAR_TRANSITION;}

    private void project(float cx,float y,float cz,float[] sun,float radius,float[] target){
        float cameraDistance=Math.max(240f,radius*2.0f);
        float sunLen=Math.max(.0001f,(float)Math.sqrt(sun[0]*sun[0]+sun[1]*sun[1]+sun[2]*sun[2]));
        float nx=sun[0]/sunLen,ny=sun[1]/sunLen,nz=sun[2]/sunLen;
        float centerY=y+20f;
        float upY=Math.abs(ny)>.96f?0f:1f;
        float upZ=Math.abs(ny)>.96f?1f:0f;
        Matrix.setLookAtM(lightView,0,cx+nx*cameraDistance,centerY+ny*cameraDistance,
                          cz+nz*cameraDistance,cx,centerY,cz,0f,upY,upZ);
        Matrix.orthoM(lightProj,0,-radius,radius,-radius,radius,8f,cameraDistance*2.9f+radius);
        Matrix.multiplyMM(target,0,lightProj,0,lightView,0);
    }

    private void begin(Target target,float[] matrix){
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,target.framebuffer);
        GLES30.glViewport(0,0,target.size,target.size);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glColorMask(false,false,false,false);
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL);
        GLES30.glPolygonOffset(1.9f,3.1f);
        GLES30.glUseProgram(program);
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program,"uLightVP"),1,false,matrix,0);
    }
    private void end(){
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL);
        GLES30.glColorMask(true,true,true,true);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
    }
    private static boolean sunMoved(float[] sun,float sx,float sy,float sz,float tolerance){
        return Float.isNaN(sx)||Math.abs(sun[0]-sx)>tolerance||
               Math.abs(sun[1]-sy)>tolerance||Math.abs(sun[2]-sz)>tolerance;
    }
    void update(float x,float y,float z,float[] sun,World.Decoration decoration,
                TreeModel ordinary,TreeModel pine,TreeLodRenderer lod,FarWorld farWorld,
                float requestedRange,boolean enabled){
        if(!enabled){nearValid=false;farValid=false;return;}
        if(decoration==null||ordinary==null||pine==null){nearValid=false;farValid=false;return;}
        float range=Math.max(10f,Math.min(1000f,requestedRange));
        ++nearFrames;++farFrames;
        boolean makeNear=!nearValid || decoration!=nearDecoration || nearFrames>=16 ||
              Math.abs(x-lastNearX)>3f||Math.abs(z-lastNearZ)>3f||Math.abs(y-lastNearY)>2f||
              sunMoved(sun,nearSunX,nearSunY,nearSunZ,.035f);
        if(makeNear){
            nearFrames=0;
            project(Math.round(x/2f)*2f,y,Math.round(z/2f)*2f,sun,NEAR_RADIUS,lightVP);
            begin(near,lightVP);
            try {
                ordinary.drawShadowCasters(program,decoration.broadleafInstances,x,z,NEAR_RADIUS,192);
                pine.drawShadowCasters(program,decoration.pineInstances,x,z,NEAR_RADIUS,192);
                nearValid=true;
                nearDecoration=decoration;
                lastNearX=x;lastNearY=y;lastNearZ=z;
                nearSunX=sun[0];nearSunY=sun[1];nearSunZ=sun[2];
            } finally {end();}
        }
        if(range<=NEAR_TRANSITION||far==null){farValid=false;return;}
        boolean makeFar=!farValid || decoration!=farDecoration || farWorld!=farSource ||
              Math.abs(lastFarRange-range)>.5f || farFrames>=40 ||
              Math.abs(x-lastFarX)>14f||Math.abs(z-lastFarZ)>14f||Math.abs(y-lastFarY)>7f||
              sunMoved(sun,farSunX,farSunY,farSunZ,.065f);
        if(makeFar){
            farFrames=0;
            project(Math.round(x/10f)*10f,y,Math.round(z/10f)*10f,sun,range,farLightVP);
            begin(far,farLightVP);
            try {
                TreeModel distantNormal=lod==null?ordinary:lod.shadowModel(0);
                TreeModel distantPine=lod==null?pine:lod.shadowModel(1);
                distantNormal.drawShadowCasters(program,decoration.broadleafInstances,x,z,range,450);
                distantPine.drawShadowCasters(program,decoration.pineInstances,x,z,range,450);
                if(farWorld!=null){
                    distantNormal.drawShadowCasters(program,farWorld.normalTrees,x,z,range,240);
                    distantPine.drawShadowCasters(program,farWorld.giantTrees,x,z,range,240);
                }
                farValid=true;
                farDecoration=decoration;farSource=farWorld;
                lastFarX=x;lastFarY=y;lastFarZ=z;lastFarRange=range;
                farSunX=sun[0];farSunY=sun[1];farSunZ=sun[2];
            } finally {end();}
        }
    }
}
