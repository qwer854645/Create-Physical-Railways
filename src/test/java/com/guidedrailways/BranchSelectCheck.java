package com.guidedrailways;

import org.joml.Vector3d;

/** CPU-only Create-steer scoring regression. */
public final class BranchSelectCheck {
    static int checks;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}

    public static void main(String[] args){
        // Facing +Z: Create normal = exit × up points −X (right). Left entry is +X, right entry is −X.
        var exit=new Vector3d(0,0,1);
        var up=new Vector3d(0,1,0);
        var straight=new Vector3d(0,0,1);
        var left=new Vector3d(1,0,0);
        var right=new Vector3d(-1,0,0);
        var entries=new Vector3d[]{straight,left,right};

        check(BranchSelect.fromHeading(exit,up,new Vector3d(0,0,1))==BranchSelect.Steer.NONE,"ahead prefers NONE");
        check(BranchSelect.fromHeading(exit,up,new Vector3d(1,0,.2))==BranchSelect.Steer.LEFT,"yaw left prefers LEFT");
        check(BranchSelect.fromHeading(exit,up,new Vector3d(-1,0,.2))==BranchSelect.Steer.RIGHT,"yaw right prefers RIGHT");

        check(BranchSelect.steer(BranchSelect.Steer.NONE,exit,up,entries)==0,"NONE picks straight continuation");
        check(BranchSelect.steer(BranchSelect.Steer.LEFT,exit,up,entries)==1,"LEFT picks left branch");
        check(BranchSelect.steer(BranchSelect.Steer.RIGHT,exit,up,entries)==2,"RIGHT picks right branch");
        check(BranchSelect.steer(BranchSelect.Steer.NONE,exit,up,new Vector3d[0])==-1,"empty candidates yield -1");

        check(BranchSelect.reverseTravel(-5,0,false),"unpowered reverse coasting uses actual velocity");
        check(BranchSelect.reverseTravel(-5,256,false),"forward throttle cannot override backward travel");
        check(!BranchSelect.reverseTravel(5,-256,true),"reverse throttle cannot override forward travel");
        check(BranchSelect.reverseTravel(0,-64,false),"reverse start uses throttle at rest");
        check(!BranchSelect.reverseTravel(0,64,true),"forward start uses throttle at rest");
        check(BranchSelect.reverseTravel(-3,0,false),"unpowered towed bogie follows travel");
        check(BranchSelect.reverseTravel(.01,0,true),"near-rest noise preserves reverse direction");
        check(!BranchSelect.reverseTravel(-.01,0,false),"near-rest noise preserves forward direction");
        check(BranchSelect.reverseTravel(0,0,true),"unpowered standstill preserves last direction");
        for(double yaw:new double[]{0,.7,2.1}){
            var forward=new Vector3d(Math.sin(yaw),0,Math.cos(yaw));
            check(BranchSelect.exitsIncreasing(forward,forward,false),"forward exits increasing parameter");
            check(!BranchSelect.exitsIncreasing(forward,forward,true),"reverse exits decreasing parameter");
            check(!BranchSelect.exitsIncreasing(new Vector3d(forward).negate(),forward,false),"reversed edge parameter handles forward travel");
            check(BranchSelect.exitsIncreasing(new Vector3d(forward).negate(),forward,true),"reversed edge parameter handles reverse travel");
            for(boolean reverse:new boolean[]{false,true}){
                var travel=new Vector3d(forward).mul(reverse?-1:1);
                var sideways=new Vector3d(travel).cross(up).normalize();
                var choices=new Vector3d[]{new Vector3d(travel),new Vector3d(travel).add(sideways).normalize()};
                check(BranchSelect.steer(BranchSelect.fromHeading(travel,up,travel),travel,up,choices)==0,"straight continuation is symmetric in rotated and reversed travel");
            }
        }
        System.out.println("BranchSelectCheck: "+checks+" assertions passed");
    }
}
