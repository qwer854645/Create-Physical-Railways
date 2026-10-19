package com.guidedrailways;
import java.util.*;
final class RailDockGate {
    static boolean ready(UUID targetStation,UUID boundStation,UUID peerBody,Set<UUID> train,boolean reciprocal,boolean worldLocked,boolean carLocked){
        return targetStation!=null&&targetStation.equals(boundStation)&&peerBody!=null&&train.contains(peerBody)&&reciprocal&&worldLocked&&carLocked;
    }
}
