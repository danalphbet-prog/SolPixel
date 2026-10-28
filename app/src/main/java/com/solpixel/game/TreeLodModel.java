package com.solpixel.game;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

/** SolPixel v0.6: proxy 3D low-poly com textura procedural, sem billboards verdes. */
final class TreeLodModel {
    private static final int STRIDE=32;
    private final boolean pine;
    private final Part crown,trunk;
    private final int crownTex,trunkTex,instanceBuffer;
    private float[] lastSource,visible;
    private float lastX=Float.MAX_VALUE,lastZ=Float.MAX_VALUE,lastDirX=5,lastDirZ=5,lastDist=-1;
    private static class Part { int vao,vbo,ibo,count; }
    private static class Builder {
        float[] verts=new float[4000];short[] inds=new short[3000];int vc=0,ic=0;
        void add(float x,float y,float z,float nx,float ny,float nz,float u,float v){
            if(vc*8+8>verts.length)verts=Arrays.copyOf(verts,verts.length*2);
            int o=vc*8;
            verts[o]=x;verts[o+1]=y;verts[o+2]=z;verts[o+3]=nx;verts[o+4]=ny;verts[o+5]=nz;verts[o+6]=u;verts[o+7]=v;
            vc++;
        }
        void triangle(float[] a,float[] b,float[] c,float u,float v){
            float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
            float vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            float mag=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(mag<.00001f)return;
            nx/=mag;ny/=mag;nz/=mag;
            if(ic+3>inds.length)inds=Arrays.copyOf(inds,inds.length*2);
            int idx=vc;
            add(a[0],a[1],a[2],nx,ny,nz,u,v);
            add(b[0],b[1],b[2],nx,ny,nz,u+.19f,v+.42f);
            add(c[0],c[1],c[2],nx,ny,nz,u+.43f,v+.14f);
            inds[ic++]=(short)idx;inds[ic++]=(short)(idx+1);inds[ic++]=(short)(idx+2);
        }
    }
    TreeLodModel(boolean pine){
        this.pine=pine;
        Builder a=new Builder(),b=new Builder();
        if(pine){
            // Cinco andares assimetricos de folhagem, silhueta do GLB original.
            tapered(b,0,-2.6f,1.30f,50.4f,.61f,8,2.1f);
            for(int j=0;j<5;j++){
                float base=17.5f+j*6.45f;
                float r=5.18f-j*.73f;
                tapered(a,0,base,r,base+12.5f,.14f,8,j*.37f);
            }
        }else{
            tapered(b,0,-2.6f,.39f,8.9f,.30f,7,1.8f);
            ellipsoid(a,-.72f,6.25f,.31f,1.50f,2.3f,1.48f,8,1.1f);
            ellipsoid(a, .62f,6.63f,-.54f,1.72f,2.15f,1.62f,8,2.0f);
            ellipsoid(a,-.18f,8.30f,.71f,1.81f,2.0f,1.65f,8,2.9f);
            ellipsoid(a, .55f,8.42f,.05f,1.72f,2.08f,1.78f,8,3.4f);
            ellipsoid(a, -.30f,9.37f,-.28f,1.45f,1.35f,1.4f,8,4.2f);
        }
        int[] buffers=new int[1];GLES30.glGenBuffers(1,buffers,0);instanceBuffer=buffers[0];
        crown=makePart(a);trunk=makePart(b);
        crownTex=makeTexture(pine?26:43,pine);
        trunkTex=makeTexture(94,false);
    }
    private static float[] p(float x,float y,float z){return new float[]{x,y,z};}
    private static void tapered(Builder b,float cx,float y0,float radius,float y1,float tip,int sides,float seed){
        for(int i=0;i<sides;i++){
            float a=(float)(Math.PI*2*i/sides),aa=(float)(Math.PI*2*(i+1)/sides);
            float r0=radius*(.93f+.075f*(float)Math.sin(i*2.93f+seed));
            float r1=radius*(.93f+.075f*(float)Math.sin((i+1)*2.93f+seed));
            float[] lo=p(cx+(float)Math.cos(a)*r0,y0,(float)Math.sin(a)*r0);
            float[] hi=p(cx+(float)Math.cos(aa)*r1,y0,(float)Math.sin(aa)*r1);
            float[] top=p(cx+(float)Math.cos(a)*tip,y1,(float)Math.sin(a)*tip);
            float[] top2=p(cx+(float)Math.cos(aa)*tip,y1,(float)Math.sin(aa)*tip);
            b.triangle(lo,hi,top2,i*.13f,seed*.11f);
            b.triangle(lo,top2,top,(i+.3f)*.13f,seed*.13f);
        }
    }
    private static void ellipsoid(Builder b,float cx,float cy,float cz,float rx,float ry,float rz,int sides,float seed){
        float[] bottom=p(cx,cy-ry,cz),top=p(cx,cy+ry,cz);
        for(int i=0;i<sides;i++){
            float a=(float)(Math.PI*2*i/sides),aa=(float)(Math.PI*2*(i+1)/sides);
            float rr=1f+.095f*(float)Math.sin(i*3.31+seed);
            float r2=1f+.095f*(float)Math.sin((i+1)*3.31+seed);
            float[] lo=p(cx+rx*rr*(float)Math.cos(a),cy-ry*.26f,cz+rz*rr*(float)Math.sin(a));
            float[] lo2=p(cx+rx*r2*(float)Math.cos(aa),cy-ry*.26f,cz+rz*r2*(float)Math.sin(aa));
            float[] mid=p(cx+rx*.96f*rr*(float)Math.cos(a),cy+ry*.30f,cz+rz*.96f*rr*(float)Math.sin(a));
            float[] mid2=p(cx+rx*.96f*r2*(float)Math.cos(aa),cy+ry*.30f,cz+rz*.96f*r2*(float)Math.sin(aa));
            b.triangle(bottom,lo2,lo,i*.12f,seed*.14f);
            b.triangle(lo,lo2,mid2,i*.12f,seed*.14f);
            b.triangle(lo,mid2,mid,i*.12f,seed*.14f);
            b.triangle(mid,mid2,top,i*.12f,seed*.14f);
        }
    }
    private static Part makePart(Builder b){
        Part m=new Part();m.count=b.ic;
        FloatBuffer verts=ByteBuffer.allocateDirect(b.vc*STRIDE).order(ByteOrder.nativeOrder()).asFloatBuffer();
        verts.put(b.verts,0,b.vc*8).position(0);
        ShortBuffer ix=ByteBuffer.allocateDirect(b.ic*2).order(ByteOrder.nativeOrder()).asShortBuffer();
        ix.put(b.inds,0,b.ic).position(0);
        int[] va=new int[1],buffers=new int[2];GLES30.glGenVertexArrays(1,va,0);GLES30.glGenBuffers(2,buffers,0);
        m.vao=va[0];m.vbo=buffers[0];m.ibo=buffers[1];
        GLES30.glBindVertexArray(m.vao);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,m.vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,b.vc*STRIDE,verts,GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,m.ibo);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,b.ic*2,ix,GLES30.GL_STATIC_DRAW);
        for(int i=0;i<3;i++)GLES30.glEnableVertexAttribArray(i);
        GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,STRIDE,0);
        GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,STRIDE,12);
        GLES30.glVertexAttribPointer(2,2,GLES30.GL_FLOAT,false,STRIDE,24);
        GLES30.glBindVertexArray(0);
        return m;
    }
    private static int makeTexture(int seed,boolean leafPine){
        Bitmap texture=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){
            int h=(x/3+73)*(y/3+19)*1103515245+seed*15731;
            h^=(h>>>15);h*=0x45d9f3b;h^=(h>>>16);
            int n=h&255;
            boolean bark=seed==94;
            int r=bark?67+n/16:leafPine?37+n/20:51+n/17;
            int g=bark?43+n/19:leafPine?63+n/9:86+n/8;
            int bl=bark?30+n/22:leafPine?44+n/17:40+n/20;
            if((h&15)==0){r=(int)(r*.74);g=(int)(g*.74);bl=(int)(bl*.74);}
            texture.setPixel(x,y,0xff000000|(r<<16)|(g<<8)|bl);
        }
        int[] t=new int[1];GLES30.glGenTextures(1,t,0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,t[0]);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST_MIPMAP_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,texture,0);
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
        texture.recycle();return t[0];
    }
    static float switchDistance(float px,float pz){
        int h=Float.floatToIntBits(px*1.71f)^Float.floatToIntBits(pz*2.27f);
        h^=h>>>16;h*=0x7feb352d;h^=h>>>15;
        return 58f+(h&255)/255f*20f;
    }
    void draw(int program,float[] source,float eyeX,float eyeZ,float fwX,float fwZ,float maxDistance){
        if(source==null||source.length==0)return;
        float horizontal=(float)Math.hypot(fwX,fwZ);
        if(horizontal>.01f){fwX/=horizontal;fwZ/=horizontal;}
        boolean recalc=lastSource!=source||visible==null||Math.abs(eyeX-lastX)>5f||
            Math.abs(eyeZ-lastZ)>5f||fwX*lastDirX+fwZ*lastDirZ<.978f||lastDist!=maxDistance;
        if(recalc){
            float[] arr=new float[source.length];int n=0;
            for(int i=0;i<source.length;i+=8){
                float px=source[i],pz=source[i+2];
                float vx=px-eyeX,vz=pz-eyeZ;
                float d2=vx*vx+vz*vz;
                if(d2>maxDistance*maxDistance)continue;
                float dist=(float)Math.sqrt(d2);
                if(dist<=switchDistance(px,pz))continue;
                if(vx*fwX+vz*fwZ<-dist*.34f)continue;
                int h=Float.floatToIntBits(px*3.11f)^Float.floatToIntBits(pz*2.61f);
                h^=h>>>16;h*=0x7feb352d;h^=h>>>15;
                float rand=(h&0x7fffffff)/2147483648f;
                float keep=dist<165f?1f:dist<370f?.80f:.63f;
                if(rand>keep)continue;
                System.arraycopy(source,i,arr,n,8);n+=8;
            }
            visible=Arrays.copyOf(arr,n);lastSource=source;
            lastX=eyeX;lastZ=eyeZ;lastDirX=fwX;lastDirZ=fwZ;lastDist=maxDistance;
        }
        if(visible.length==0)return;
        // So reenviar dados quando a selecao realmente muda.
        if(recalc){
            FloatBuffer instances=ByteBuffer.allocateDirect(visible.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            instances.put(visible).position(0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,instanceBuffer);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,visible.length*4,instances,GLES30.GL_DYNAMIC_DRAW);
        }
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"uTex"),1);
        drawPart(program,crown,crownTex,true,visible.length/8);
        drawPart(program,trunk,trunkTex,false,visible.length/8);
    }
    private void drawPart(int program,Part p,int texture,boolean foliage,int count){
        GLES30.glBindVertexArray(p.vao);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,instanceBuffer);
        GLES30.glEnableVertexAttribArray(3);GLES30.glEnableVertexAttribArray(4);
        GLES30.glVertexAttribPointer(3,4,GLES30.GL_FLOAT,false,32,0);
        GLES30.glVertexAttribPointer(4,4,GLES30.GL_FLOAT,false,32,16);
        GLES30.glVertexAttribDivisor(3,1);GLES30.glVertexAttribDivisor(4,1);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uLeaf"),foliage?1f:0f);
        GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES,p.count,GLES30.GL_UNSIGNED_SHORT,0,count);
        GLES30.glBindVertexArray(0);
    }
}
