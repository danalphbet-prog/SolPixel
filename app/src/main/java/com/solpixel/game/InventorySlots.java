package com.solpixel.game;

/** Ten backpack slots plus two hands. One immutable sword, one torch, stackable logs. */
final class InventorySlots {
    static final int EMPTY=0, SWORD=1, TORCH=2, LOG=3;
    static final int BAG_COUNT=10, LEFT_HAND=10, RIGHT_HAND=11, TOTAL=12, STACK_MAX=64;
    private final int[] types=new int[TOTAL];
    private final int[] counts=new int[TOTAL];
    InventorySlots(){types[LEFT_HAND]=SWORD;counts[LEFT_HAND]=1;types[1]=TORCH;counts[1]=1;}
    synchronized int itemAt(int slot){return slot>=0 && slot<TOTAL?types[slot]:EMPTY;}
    synchronized int countAt(int slot){return slot>=0 && slot<TOTAL?counts[slot]:0;}
    synchronized boolean isSwordInHand(int hand){return validHand(hand)&&types[LEFT_HAND+hand]==SWORD;}
    synchronized boolean isEmptyHand(int hand){return validHand(hand)&&types[LEFT_HAND+hand]==EMPTY;}
    synchronized boolean isLogInHand(int hand){return validHand(hand)&&types[LEFT_HAND+hand]==LOG;}
    private static boolean validHand(int hand){return hand==0||hand==1;}
    synchronized boolean swap(int from,int to){
        if(from<0||from>=TOTAL||to<0||to>=TOTAL||from==to)return false;
        int t=types[from];types[from]=types[to];types[to]=t;
        int c=counts[from];counts[from]=counts[to];counts[to]=c;
        return true;
    }
    /** Add logs to existing backpack stacks, then free backpack slots. Return accepted count. */
    synchronized int addLogs(int requested){
        int remain=Math.max(0,requested);
        for(int i=0;i<BAG_COUNT&&remain>0;i++)if(types[i]==LOG){
            int n=Math.min(remain,STACK_MAX-counts[i]);counts[i]+=n;remain-=n;
        }
        for(int i=0;i<BAG_COUNT&&remain>0;i++)if(types[i]==EMPTY){
            int n=Math.min(remain,STACK_MAX);types[i]=LOG;counts[i]=n;remain-=n;
        }
        return Math.max(0,requested)-remain;
    }
    synchronized int consumeHandLogs(int hand,int quantity){
        if(!isLogInHand(hand))return 0;
        int at=LEFT_HAND+hand,removed=Math.min(counts[at],Math.max(0,quantity));
        counts[at]-=removed;
        if(counts[at]<=0){counts[at]=0;types[at]=EMPTY;}
        return removed;
    }
    synchronized String save(){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<TOTAL;i++){if(i>0)b.append(',');b.append(types[i]).append(':').append(counts[i]);}
        return b.toString();
    }
    synchronized void restore(String saved){
        if(saved==null||saved.length()>240)return;
        String[] tokens=saved.split(",",-1);if(tokens.length!=TOTAL)return;
        int[] t=new int[TOTAL],c=new int[TOTAL];int swords=0,torches=0;
        try{
            for(int i=0;i<TOTAL;i++){
                String[] pair=tokens[i].split(":",-1);
                if(pair.length==1){t[i]=Integer.parseInt(pair[0].trim());c[i]=t[i]==EMPTY?0:1;}
                else if(pair.length==2){t[i]=Integer.parseInt(pair[0].trim());c[i]=Integer.parseInt(pair[1].trim());}
                else return;
                if(t[i]<EMPTY||t[i]>LOG||c[i]!=(t[i]==EMPTY?0:Math.max(1,Math.min(STACK_MAX,c[i]))))return;
                if(t[i]!=LOG && c[i]>1)return;
                if(t[i]==SWORD)swords++;
                if(t[i]==TORCH)torches++;
            }
        }catch(NumberFormatException ignored){return;}
        if(swords!=1||torches!=1)return;
        System.arraycopy(t,0,types,0,TOTAL);System.arraycopy(c,0,counts,0,TOTAL);
    }
}
