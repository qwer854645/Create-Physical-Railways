package com.guidedrailways;
import java.util.*;
final class BogieContactPolicyCheck {
    private static UUID id(int n){return new UUID(0,n);}
    private static BogieContactPolicy.Body body(int n,int carrier,double x,boolean detached){return new BogieContactPolicy.Body(id(n),id(carrier),x,0,0,detached);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void run(){
        var a=body(1,11,0,false);var b=body(2,12,3,false);var bodies=List.of(a,b);
        check(BogieContactPolicy.exclusions(bodies,List.of()).isEmpty(),"unlinked vehicles must collide");
        var link=new BogieContactPolicy.Link(id(11),id(12));var pair=BogieContactPolicy.Pair.of(id(1),id(2));
        check(BogieContactPolicy.exclusions(bodies,List.of(link)).equals(Set.of(pair)),"linked bogies must not bind on curves");
        check(BogieContactPolicy.exclusions(List.of(b,a),List.of(link)).equals(Set.of(pair)),"stable pair order");
        check(BogieContactPolicy.exclusions(List.of(a,body(2,11,3,false)),List.of()).equals(Set.of(pair)),"two bogies under one carrier");
        var chain=List.of(new BogieContactPolicy.Link(id(11),id(90)),new BogieContactPolicy.Link(id(90),id(12)));
        check(BogieContactPolicy.exclusions(bodies,chain).equals(Set.of(pair)),"bare intermediate carriers must connect the group");
        check(BogieContactPolicy.exclusions(bodies,List.of(chain.getFirst())).isEmpty(),"unlink restores collision");
        check(BogieContactPolicy.exclusions(List.of(a,body(2,12,3,true)),List.of(link)).isEmpty(),"derailed bogies regain contacts");
        check(BogieContactPolicy.exclusions(List.of(a,body(2,12,9,false)),List.of(link)).isEmpty(),"distant bogies need no native handle");
        check(BogieContactPolicy.exclusions(List.of(a),List.of(link)).isEmpty(),"removed bogies clear the pair");
        check(BogieContactPolicy.exclusions(List.of(a,b,body(3,13,2,false)),List.of(link)).equals(Set.of(pair)),"nearby unrelated trains still collide");
        System.out.println("BogieContactPolicyCheck: 10 assertions passed");
    }
}
