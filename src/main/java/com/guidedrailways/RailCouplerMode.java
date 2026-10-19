package com.guidedrailways;
final class RailCouplerMode {
    static int choose(boolean linked,boolean sameTrain,boolean stopped,boolean ready,boolean couple,boolean decouple){
        if(!stopped||!ready)return 0;
        if(linked)return decouple?2:0;
        return !sameTrain&&couple?1:0;
    }
}
