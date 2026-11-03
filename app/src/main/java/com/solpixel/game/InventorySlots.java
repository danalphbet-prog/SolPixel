package com.solpixel.game;

/** 10 slots de mochila e 2 slots de mao; sem duplicar itens nas trocas. */
final class InventorySlots {
    static final int EMPTY=0, SWORD=1, TORCH=2;
    static final int BAG_COUNT=10, LEFT_HAND=10, RIGHT_HAND=11, TOTAL=12;
    private final int[] slots=new int[TOTAL];

    InventorySlots(){
        // Comeca com a espada equipada, como no Solas anterior.
        slots[LEFT_HAND]=SWORD;
        slots[1]=TORCH; // Item visual; a ferramenta 3D pode ser adicionada depois.
    }

    synchronized int itemAt(int slot){
        return slot>=0 && slot<TOTAL ? slots[slot] : EMPTY;
    }
    synchronized boolean isSwordInHand(int hand){
        return (hand==0 || hand==1) && slots[LEFT_HAND+hand]==SWORD;
    }
    synchronized boolean swap(int from,int to){
        if(from<0 || to<0 || from>=TOTAL || to>=TOTAL || from==to)return false;
        int item=slots[from];slots[from]=slots[to];slots[to]=item;
        return true;
    }
    synchronized String save(){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<TOTAL;i++){
            if(i>0)b.append(',');
            b.append(slots[i]);
        }
        return b.toString();
    }
    synchronized void restore(String saved){
        if(saved==null || saved.length()>100)return;
        String[] fields=saved.split(",",-1);
        if(fields.length!=TOTAL)return;
        int[] parsed=new int[TOTAL];
        int swordCount=0,torchCount=0;
        try{
            for(int i=0;i<TOTAL;i++){
                parsed[i]=Integer.parseInt(fields[i].trim());
                if(parsed[i]<EMPTY || parsed[i]>TORCH)return;
                if(parsed[i]==SWORD)swordCount++;
                if(parsed[i]==TORCH)torchCount++;
            }
        }catch(NumberFormatException ignored){return;}
        // Evita estados salvos corrompidos: nenhuma espada some ou duplica.
        if(swordCount!=1 || torchCount!=1)return;
        System.arraycopy(parsed,0,slots,0,TOTAL);
    }
}
