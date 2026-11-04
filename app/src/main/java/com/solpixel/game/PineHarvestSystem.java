package com.solpixel.game;

import android.content.Context;
import android.opengl.GLES30;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/** Six sword hits cut real procedural pines. Logs remain in-world until picked up. */
final class PineHarvestSystem {
    private static final int MAX_LOOSE_LOGS=256;
    private final HashMap<Long,TreeState> states=new HashMap<>();
    private final ArrayList<TreeState> falling=new ArrayList<>();
    private final ArrayList<LooseLog> logs=new ArrayList<>();
    private final TreeModel nearLog,farLog;
    private CampfireSystem campfires;
    private World.Decoration decoration;
    private float[] originalPines;
    private float gameTime;
    private TreeState lastHit;

    private static final class TreeState {
        final float x,y,z,scale,cos,sin,shade,tilt,radius;
        int damage;
        boolean felled;
        float hitTime,felledAt=-1f;
        TreeState(World.Tree tree,float tilt){
            x=tree.x;y=tree.y;z=tree.z;scale=tree.scale;
            cos=tree.cos;sin=tree.sin;shade=tree.shade;radius=tree.radius;
            this.tilt=tilt;
        }
        float[] instance(){return new float[]{x,y,z,scale,cos,sin,shade,tilt};}
    }
    private static final class LooseLog {
        float x,y,z,baseY,vSpeed,scale,angle,age;
        LooseLog(float x,float z,float scale,float angle,float height){
            this.x=x;this.z=z;this.scale=scale;this.angle=angle;
            baseY=World.height(x,z)+scale*.94f;
            y=baseY+height;
        }
        void step(float dt){
            age+=dt;
            if(y>baseY+.006f || Math.abs(vSpeed)>.08f){
                vSpeed-=19.0f*dt;
                y+=vSpeed*dt;
                if(y<baseY){y=baseY;vSpeed=Math.abs(vSpeed)>.7f?-vSpeed*.23f:0f;}
            }
        }
        void add(float[] out,int offset){
            out[offset]=x;out[offset+1]=y;out[offset+2]=z;out[offset+3]=scale;
            out[offset+4]=(float)Math.cos(angle);out[offset+5]=(float)Math.sin(angle);
            out[offset+6]=.3f;out[offset+7]=1.5707963f; // cylindrical log lies on its side
        }
    }
    PineHarvestSystem(Context context) throws Exception {
        nearLog=new TreeModel(context,"tronco_6_lados.glb");
        farLog=new TreeModel(context,"tronco_4_lados.glb");
        nearLog.setNoLeaves();farLog.setNoLeaves();
        try{campfires=new CampfireSystem(context);}
        catch(Exception error){
            android.util.Log.e("Solas","Fogueira GL nao disponivel (corte continua ativo)",error);
            campfires=null;
        }
    }
    private static long key(float x,float z){
        return ((long)Math.round(x*1000f)<<32) ^ (Math.round(z*1000f)&0xffffffffL);
    }
    boolean isFelled(float x,float z){
        TreeState state=states.get(key(x,z));
        return state!=null && state.felled;
    }
    void attach(World.Decoration next){
        if(next==decoration)return;
        decoration=next;
        originalPines=next==null?null:next.pineInstances;
        rebuildPines();
    }
    private void rebuildPines(){
        if(decoration==null || originalPines==null)return;
        int size=0;
        for(int i=0;i+7<originalPines.length;i+=8)
            if(!isFelled(originalPines[i],originalPines[i+2]))size+=8;
        if(size==originalPines.length){decoration.pineInstances=originalPines;return;}
        float[] kept=new float[size];int off=0;
        for(int i=0;i+7<originalPines.length;i+=8){
            if(isFelled(originalPines[i],originalPines[i+2]))continue;
            System.arraycopy(originalPines,i,kept,off,8);off+=8;
        }
        decoration.pineInstances=kept;
    }
    void tick(float time,float delta,float playerX,float playerZ){
        if(campfires!=null)campfires.tick(time,delta,playerX,playerZ);
        gameTime=time;
        float dt=Math.min(.045f,Math.max(0f,delta));
        for(LooseLog log:logs)log.step(dt);
        for(Iterator<TreeState> it=falling.iterator();it.hasNext();){
            if(time-it.next().felledAt>2.2f)it.remove();
        }
    }
    int hit(float px,float pz,float fx,float fz,boolean glowing){
        if(campfires!=null && campfires.attack(px,pz,fx,fz,glowing))return 0;
        if(decoration==null || decoration.trees==null)return 0;
        float norm=(float)Math.hypot(fx,fz);
        if(norm<.1f)return 0;fx/=norm;fz/=norm;
        World.Tree target=null;
        float best=Float.POSITIVE_INFINITY;
        for(World.Tree candidate:decoration.trees){
            if(candidate.kind!=World.PINE || isFelled(candidate.x,candidate.z))continue;
            float dx=candidate.x-px,dz=candidate.z-pz;
            float distance=(float)Math.hypot(dx,dz);
            if(distance>10.5f || distance<.10f)continue;
            float ahead=dx*fx+dz*fz;
            if(ahead<-Math.min(1.0f,candidate.radius*.55f) || ahead>10.5f)continue;
            float lateral=Math.abs(dx*fz-dz*fx);
            // Arco frontal mais tolerante: mira central OU tronco a frente.
            float width=Math.max(1.65f,Math.min(3.7f,1.2f+distance*.33f+candidate.radius*.48f));
            if(lateral>width)continue;
            float score=lateral*.70f+distance*.23f;
            if(score<best){best=score;target=candidate;}
        }
        if(target==null)return 0;
        long id=key(target.x,target.z);
        TreeState s=states.get(id);
        if(s==null){
            float tilt=0;
            if(originalPines!=null)for(int i=0;i+7<originalPines.length;i+=8)
                if(key(originalPines[i],originalPines[i+2])==id){tilt=originalPines[i+7];break;}
            s=new TreeState(target,tilt);states.put(id,s);
        }
        s.damage++;s.hitTime=gameTime;lastHit=s;
        if(s.damage>=6){
            s.felled=true;s.felledAt=gameTime;
            falling.add(s);
            spawnTreeLogs(s);
            rebuildPines();
        }
        return s.damage;
    }
    private void spawnTreeLogs(TreeState s){
        // Four to six physical pieces, diameter matches World.Tree.radius.
        int n=Math.max(4,Math.min(6,Math.round(3+s.scale*2.2f)));
        for(int i=0;i<n&&logs.size()<MAX_LOOSE_LOGS;i++){
            float a=6.2831853f*i/n+(float)(s.x+s.z)*.04f;
            float dist=1.25f+(i%3)*.40f;
            float x=s.x+(float)Math.cos(a)*dist,z=s.z+(float)Math.sin(a)*dist;
            logs.add(new LooseLog(x,z,Math.max(.25f,s.radius),a,1.5f+i*.42f));
        }
    }
    float[] pulse(){
        if(lastHit==null || lastHit.felled)return new float[]{0,0,0,0};
        float t=(gameTime-lastHit.hitTime)/.26f;
        if(t<0||t>=1f)return new float[]{0,0,0,0};
        return new float[]{lastHit.x,lastHit.z,(float)Math.sin(Math.PI*t),1f};
    }
    int pickUp(float px,float pz,float fx,float fz,InventorySlots inventory,boolean all){
        if(campfires!=null){
            int taken=campfires.pickup(px,pz,fx,fz,inventory,all);
            if(taken>0)return taken;
        }
        if(logs.isEmpty())return 0;
        float d=(float)Math.hypot(fx,fz);
        if(d<.05f)return 0;fx/=d;fz/=d;
        LooseLog best=null;float score=Float.POSITIVE_INFINITY;
        for(LooseLog log:logs){
            float dx=log.x-px,dz=log.z-pz;
            float dist=(float)Math.hypot(dx,dz);
            if(dist>4.7f)continue;
            float ahead=dx*fx+dz*fz;
            float lateral=Math.abs(dx*fz-dz*fx);
            if(ahead<-.5f || lateral>Math.max(1.7f,dist*.50f))continue;
            float cost=dist*.24f+lateral;
            if(cost<score){best=log;score=cost;}
        }
        if(best==null)return 0;
        int obtained=0;
        if(!all){
            if(inventory.addLogs(1)==1){logs.remove(best);obtained=1;}
            return obtained;
        }
        // Double-tap-and-hold: all logs of the same drop pile close to the targeted piece.
        float cx=best.x,cz=best.z;
        for(Iterator<LooseLog> it=logs.iterator();it.hasNext();){
            LooseLog log=it.next();
            float dx=log.x-cx,dz=log.z-cz;
            float playerDist=(float)Math.hypot(log.x-px,log.z-pz);
            if(dx*dx+dz*dz>5f*5f || playerDist>6.2f)continue;
            if(inventory.addLogs(1)!=1)break;it.remove();obtained++;
        }
        return obtained;
    }
    private float[] groundPointAtCrosshair(float px,float pz,float fx,float fz,float pitch){
        // Centro da tela: raycast leve contra o terreno a ate 7.5 m.
        float horizontal=(float)Math.cos(pitch),vertical=(float)Math.sin(pitch);
        float eye=World.height(px,pz)+1.72f;
        for(float step=1.2f;step<=7.5f;step+=.22f){
            float xx=px+fx*horizontal*step,zz=pz+fz*horizontal*step;
            if(eye+vertical*step<=World.height(xx,zz)+.12f)
                return new float[]{xx,zz};
        }
        return new float[]{px+fx*3.1f,pz+fz*3.1f};
    }
    int dropFromHand(InventorySlots inventory,int hand,boolean all,float px,float pz,float fx,float fz,float aimPitch){
        int have=inventory.countAt(InventorySlots.LEFT_HAND+hand);
        if(!inventory.isLogInHand(hand) || have<1)return 0;
        // Segurar SOLTAR com ao menos 2 troncos monta base da fogueira (2 pecas).
        if(all && have>=2 && campfires!=null){
            float[] target=groundPointAtCrosshair(px,pz,fx,fz,aimPitch);
            if(campfires.createAt(target[0],target[1],(float)Math.atan2(fx,-fz)))
                return inventory.consumeHandLogs(hand,2);
        }
        int n=Math.min(Math.min(all?have:1,MAX_LOOSE_LOGS-logs.size()),InventorySlots.STACK_MAX);
        if(n<=0)return 0;
        n=inventory.consumeHandLogs(hand,n);
        float norm=(float)Math.hypot(fx,fz);
        if(norm<.01f){fx=0;fz=-1;}else{fx/=norm;fz/=norm;}
        for(int i=0;i<n;i++){
            float angle=(float)(i*2.399963f);
            float radius=.35f+Math.min(3,i/6)*.21f;
            float x=px+fx*2.1f+(float)Math.cos(angle)*radius;
            float z=pz+fz*2.1f+(float)Math.sin(angle)*radius;
            logs.add(new LooseLog(x,z,.58f,angle,.3f+i*.02f));
        }
        return n;
    }
    /** First-person held log uses the high-detail six-sided GLB. */
    void drawHeld(int program,float px,float py,float pz,float[] forward,float[] right,float[] up,float yaw){
        float cx=px+forward[0]*1.45f+right[0]*.47f-up[0]*.43f;
        float cy=py+forward[1]*1.45f+right[1]*.47f-up[1]*.43f;
        float cz=pz+forward[2]*1.45f+right[2]*.47f-up[2]*.43f;
        float[] instance={cx,cy,cz,.34f,(float)Math.cos(yaw),(float)Math.sin(yaw),.25f,.23f};
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uLogMaterial"),1f);
        nearLog.draw(program,instance);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uLogMaterial"),0f);
    }

    void bindFireLights(int program){if(campfires!=null)campfires.bindLights(program);
        else GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"uFireCount"),0);
    }
    float fireSoundLevel(float px,float pz){return campfires==null?0f:campfires.audibleAt(px,pz);}
    void drawFireEffects(float[] viewProjection,float[] cameraRight){
        if(campfires!=null)campfires.drawEffects(viewProjection,cameraRight);
    }

    /** Draws original leaf canopy dropping down, and logs LOD6 near / LOD4 far. */
    void draw(int program,TreeModel originalPines,float eyeX,float eyeZ){
        if(originalPines!=null){
            for(TreeState s:falling){
                float t=Math.max(0f,Math.min(1f,(gameTime-s.felledAt)/2.05f));
                float fallingDistance=(float)(t*t*(3.0-2.0*t))*(38f*s.scale);
                float fade=Math.max(0f,Math.min(1f,(t-.35f)/.65f));
                GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uHarvestFall"),fallingDistance);
                GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uHarvestFade"),fade);
                originalPines.drawCanopy(program,s.instance());
            }
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uHarvestFall"),0f);
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uHarvestFade"),0f);
        }
        int nearCount=0,farCount=0;
        for(LooseLog log:logs){
            float dx=log.x-eyeX,dz=log.z-eyeZ;
            if(dx*dx+dz*dz<14f*14f)nearCount++;else if(dx*dx+dz*dz<80f*80f)farCount++;
        }
        float[] near=new float[nearCount*8],far=new float[farCount*8];int a=0,b=0;
        for(LooseLog log:logs){
            float dx=log.x-eyeX,dz=log.z-eyeZ,ds=dx*dx+dz*dz;
            if(ds<14f*14f){log.add(near,a);a+=8;}
            else if(ds<80f*80f){log.add(far,b);b+=8;}
        }
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uLogMaterial"),1f);
        if(a>0)nearLog.draw(program,near);
        if(b>0)farLog.draw(program,far);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uLogMaterial"),0f);
        if(campfires!=null)campfires.drawLogs(program,nearLog,farLog,eyeX,eyeZ);
    }
}
