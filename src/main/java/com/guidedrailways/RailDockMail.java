package com.guidedrailways;

/** Directional package permissions, shared by native insertion and direct logistics handoffs. */
final class RailDockMail {
    static boolean allows(boolean parcel,boolean reciprocalLock,boolean stationReady,boolean receivingWorld,boolean matchesStation){
        if(!parcel)return true;
        return reciprocalLock&&stationReady&&(receivingWorld==matchesStation);
    }
}
