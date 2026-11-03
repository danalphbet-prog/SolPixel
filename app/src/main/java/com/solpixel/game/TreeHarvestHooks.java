package com.solpixel.game;

/**
 * Gancho de golpes para a futura derrubada de arvores.
 * Nao modifica o mundo, nao destrói arvores nem gera troncos nesta versao.
 * O futuro sistema de colisao/tronco pode registrar um Listener no render thread.
 */
final class TreeHarvestHooks {
    interface Listener {
        void onSwordSwing(float playerX,float playerZ,float directionX,float directionZ);
    }
    private static volatile Listener listener;
    private TreeHarvestHooks(){}
    static void register(Listener callback){listener=callback;}
    static void swordSwing(float x,float z,float dx,float dz){
        Listener callback=listener;
        if(callback!=null)callback.onSwordSwing(x,z,dx,dz);
    }
}
