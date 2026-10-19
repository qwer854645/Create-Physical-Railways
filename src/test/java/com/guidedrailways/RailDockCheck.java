package com.guidedrailways;
import java.util.*;
import java.nio.file.*;
import com.google.gson.JsonParser;
public final class RailDockCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        var first=UUID.randomUUID();var second=UUID.randomUUID();var lease=new RailDockLease();
        check(!lease.active(0),"freshly loaded connectors have no automatic lock");
        check(lease.claim(first,10)&&lease.active(49)&&!lease.active(50),"automatic lock has a bounded heartbeat lifetime");
        check(!lease.claim(second,20),"another train cannot steal an active connector");
        check(lease.claim(first,30)&&lease.active(69),"same train refreshes its docking heartbeat");
        check(!lease.release(second,40)&&lease.active(40),"another controller cannot release this train's docking lock");
        check(lease.release(first,40)&&!lease.active(40),"departure releases the automatic request immediately");
        check(!lease.claim(first,59)&&lease.claim(second,60),"release allows native undocking to settle before reuse");
        check(!lease.claim(first,99)&&lease.claim(first,100),"expired heartbeat releases a connector after controller removal");
        check(!lease.release(second,101)&&lease.active(101),"late release from a previous owner cannot unlock a new arrival");
        lease.clear(102);check(!lease.active(102)&&!lease.canClaim(first,103),"rebinding clears the active request and observes release cooldown");
        var station=UUID.randomUUID();var car=UUID.randomUUID();var trailer=UUID.randomUUID();var stranger=UUID.randomUUID();
        for(boolean reciprocal:new boolean[]{false,true})for(boolean a:new boolean[]{false,true})for(boolean b:new boolean[]{false,true})
            check(RailDockGate.ready(station,station,car,Set.of(car,trailer),reciprocal,a,b)==(reciprocal&&a&&b),"both native endpoints must be mutually paired and fully locked");
        check(RailDockGate.ready(station,station,trailer,Set.of(car,trailer),true,true,true),"a dock on a coupled trailer serves the whole consist");
        check(!RailDockGate.ready(station,station,stranger,Set.of(car,trailer),true,true,true),"a nearby different train cannot exchange this train's mail");
        check(!RailDockGate.ready(station,UUID.randomUUID(),car,Set.of(car),true,true,true),"a dock linked to another station cannot transfer packages");
        check(!RailDockGate.ready(station,station,null,Set.of(car),true,true,true),"world-to-world docking is not a train endpoint");
        var states=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/guidedrailways/blockstates/rail_docking_connector.json"))).getAsJsonObject().getAsJsonObject("variants");
        check(states.size()==24,"all native facing, power and extension states have a model");
        for(var entry:states.entrySet())check(entry.getValue().getAsJsonObject().get("model").getAsString().startsWith("simulated:block/docking_connector/"),"reuse native models for every state");
        var recipe=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/guidedrailways/recipe/rail_docking_connector.json"))).getAsJsonObject();
        check(recipe.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject().get("modid").getAsString().equals("simulated"),"optional recipe must not resolve a missing native connector");
        System.out.println("RailDockCheck: "+checks+" assertions passed");
    }
}
