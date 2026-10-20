package com.guidedrailways;
import com.google.gson.*;
import java.util.*;
/** Stable saved indices; the first five native indices retain the 0.1.57 mapping. */
public final class BogeyVariant {
    public static final BogeyVariant DEFAULT=new BogeyVariant();
    private static final List<BogeyVariant> ALL=new ArrayList<>();
    public static final BogeyVariant FREIGHT,PASSENGER,MODERN,BLOMBERG,Y25;
    public final String id,size;public final RailGauge gauge;public final double renderY,tread,ride,half,animationRatio;public final int mountTop;
    final List<double[]> boxes;private final int index;
    private BogeyVariant(){index=0;id="";size="";gauge=RailGauge.STANDARD;renderY=BogieGeometry.RENDER_Y;tread=BogieGeometry.TREAD_RADIUS;ride=BogieGeometry.RIDE_HEIGHT;half=1;mountTop=2;animationRatio=1;boxes=List.of();}
    private BogeyVariant(JsonObject data){
        index=data.get("index").getAsInt();id=data.get("id").getAsString();size=data.get("size").getAsString();gauge=Objects.requireNonNull(RailGauge.forTrack(data.get("gauge").getAsString()));
        renderY=data.get("renderY").getAsDouble();tread=data.get("tread").getAsDouble();ride=data.get("ride").getAsDouble();half=data.get("half").getAsDouble();mountTop=data.get("mountTop").getAsInt();animationRatio=data.get("animationRatio").getAsDouble();
        var list=new ArrayList<double[]>();for(var value:data.getAsJsonArray("boxes")){double[] b=new double[6];for(int i=0;i<6;i++)b[i]=value.getAsJsonArray().get(i).getAsDouble();list.add(b);}boxes=List.copyOf(list);
    }
    static{
        ALL.add(DEFAULT);
        try(var stream=BogeyVariant.class.getResourceAsStream("/assets/guidedrailways/geometry/all_bogeys.json")){
            for(var row:JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(stream),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){
                var v=new BogeyVariant(row.getAsJsonObject());if(v.index!=ALL.size())throw new IllegalStateException("Non-contiguous bogey catalog");ALL.add(v);
            }
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
        FREIGHT=ALL.get(1);PASSENGER=ALL.get(2);MODERN=ALL.get(3);BLOMBERG=ALL.get(4);Y25=ALL.get(5);
    }
    public int ordinal(){return index;}
    public static BogeyVariant[] values(){return ALL.toArray(BogeyVariant[]::new);}
    public static BogeyVariant read(int index){return index>=0&&index<ALL.size()?ALL.get(index):DEFAULT;}
    public static BogeyVariant find(String id){var small=find(id,"create:small");if(small!=null)return small;for(var v:ALL)if(v.id.equals(id))return v;return null;}
    public static BogeyVariant find(String id,String size){for(var v:ALL)if(v.id.equals(id)&&v.size.equals(size))return v;return null;}
    public static BogeyVariant defaultFor(RailGauge gauge){return Objects.requireNonNull(find(switch(gauge){case NARROW->"railways:narrow_default";case WIDE->"railways:wide_default";case MONORAIL->"railways:monobogey";default->"create:standard";},"create:small"));}
    public double ride(RailGauge gauge){return this==DEFAULT?BogieGeometry.ride(gauge):ride;}
    public double plateOffset(RailGauge gauge){return this==DEFAULT?BogieGeometry.plateOffset(gauge):mountTop-.5-ride;}
    public double pivotHeight(int version,RailGauge gauge){return this==DEFAULT?BogieGeometry.pivotHeight(version,gauge):plateOffset(gauge)+BogieGeometry.MOUNT_UNDERSIDE-.5;}
    @Override public String toString(){return this==DEFAULT?"DEFAULT":id+"/"+size;}
}
