package com.guidedrailways;
import java.util.*;
public final class PassengerManifestCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        var head=UUID.randomUUID();var trailer=UUID.randomUUID();var platform=UUID.randomUUID();
        var alice=UUID.randomUUID();var bob=UUID.randomUUID();
        var train=new PassengerManifest(Set.of(head,trailer),true);
        train.seated(alice,head);train.seated(bob,trailer);train.seated(alice,head);
        train.seated(UUID.randomUUID(),platform);
        check(train.count()==2,"count the whole consist once, exclude another train and platform seats");
        check(train.satisfies(2,false)&&!train.satisfies(1,false),"exact native passenger target");
        check(train.satisfies(1,true)&&!train.satisfies(3,true),"native at least target");
        var detached=new PassengerManifest(Set.of(head),true);
        detached.seated(alice,head);detached.seated(bob,trailer);
        check(detached.count()==1,"uncoupled carriage passengers no longer belong to the train");
        var empty=new PassengerManifest(Set.of(head),true);
        check(empty.satisfies(0,false)&&empty.satisfies(0,true),"empty loaded train satisfies zero target");
        check(!empty.satisfies(1,true),"dismount and disconnect are observed without stale cached counts");
        var unloaded=new PassengerManifest(Set.of(head,trailer),false);
        unloaded.seated(alice,head);
        check(!unloaded.satisfies(0,true)&&!unloaded.satisfies(1,false),"partial load cannot satisfy a passenger condition");
        var reloaded=new PassengerManifest(Set.of(head,trailer),true);
        check(!reloaded.satisfies(2,false),"saved previous count is not a restored passenger manifest");
        System.out.println("PassengerManifestCheck: "+checks+" assertions passed");
    }
}
