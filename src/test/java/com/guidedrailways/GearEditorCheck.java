package com.guidedrailways;
public final class GearEditorCheck {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        for(int height:new int[]{117,141,201})for(int gear=0;gear<8;gear++){
            double top=65,bottom=top+height,y=bottom-gear*height/7.0;
            check(GearEditorModel.selection(y,top,bottom)==gear,"every visible detent maps to its own gear at different widget sizes");
            check(GearEditorModel.selection(y+height/35.0,top,bottom)==gear,"small pointer movement stays in the selected detent");
        }
        check(GearEditorModel.selection(-100,65,182)==7&&GearEditorModel.selection(999,65,182)==0,"dragging outside the track clamps to P8/P1");
        for(int maximum:new int[]{1,128,256,1000,2560})for(int speed=1;speed<=maximum;speed++){
            check(GearEditorModel.speed(GearEditorModel.fraction(speed,maximum),maximum)==speed,"slider and numeric input round-trip exactly at tenth-speed precision");
        }
        int[] profile={20,40,80,100,128,160,200,256};
        for(int selected=0;selected<8;selected++)for(int desired=-1;desired<=2561;desired++){
            var result=profile.clone();result[selected]=GearEditorModel.betweenNeighbours(desired,selected,profile);
            check(SpeedSetting.validGears(SpeedSetting.MAX,result),"dragging any speed preserves order without modifying any other gear");
            for(int i=0;i<8;i++)if(i!=selected)check(result[i]==profile[i],"editing one lever detent cannot change another gear");
        }
        check(GearEditorModel.speed(-1,256)==1&&GearEditorModel.speed(2,256)==256,"speed drags outside the slider remain bounded");
        System.out.println("GearEditorCheck: "+checks+" assertions passed");
    }
}
