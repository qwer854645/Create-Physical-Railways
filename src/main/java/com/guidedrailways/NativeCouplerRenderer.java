package com.guidedrailways;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.railwayteam.railways.content.buffer.IDyedBuffer;
import dev.ryanhcode.sable.companion.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import java.util.*;

/** Native JSON elements are baked once per resource reload and painted variant, then animated by mechanical part. */
final class NativeCouplerRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private enum Part {BODY,MOUNT,PIN,LINK,JAW,RIGGING,SCREW}
    private static final Map<String,Mesh> MESHES=new HashMap<>();
    private record Element(BlockElement element,Part part){}
    private static final class Mesh {
        final BlockModel model;final List<Element> elements;final Map<String,Map<Part,List<BakedQuad>>> baked=new HashMap<>();final double shift;
        Mesh(BlockModel model,List<Element> elements,double shift){this.model=model;this.elements=elements;this.shift=shift;}
        Map<Part,List<BakedQuad>> quads(String paint){return baked.computeIfAbsent(paint,key->{
            var out=new EnumMap<Part,List<BakedQuad>>(Part.class);var atlas=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);var bakery=new FaceBakery();
            for(var element:elements)for(var face:element.element.faces.entrySet()){
                var texture=model.getMaterial(face.getValue().texture()).texture();
                if(texture.getPath().equals("block/buffer/small_buffer")&&!paint.isEmpty())texture=ResourceLocation.fromNamespaceAndPath("railways","block/buffer/small_buffer/small_buffer_"+paint);
                var quad=bakery.bakeQuad(element.element.from,element.element.to,face.getValue(),atlas.apply(texture),face.getKey(),BlockModelRotation.X0_Y0,element.element.rotation,element.element.shade);
                out.computeIfAbsent(element.part,p->new ArrayList<>()).add(quad);
            }return out;
        });}
    }
    static void register(IEventBus bus){
        bus.addListener((RegisterClientReloadListenersEvent e)->e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)NativeCouplerRenderer::reload));
        bus.addListener((ModelEvent.ModifyBakingResult e)->e.getModels().replaceAll((id,model)->id.id().getNamespace().equals("railways")?new Filter(model):model));
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->{
            e.registerBlockEntityRenderer(com.railwayteam.railways.registry.CRBlockEntities.DYEABLE.get(),c->new NativeCouplerRenderer<>());
            e.registerBlockEntityRenderer(com.railwayteam.railways.registry.CRBlockEntities.HEADSTOCK.get(),c->new NativeCouplerRenderer<>());
            e.registerBlockEntityRenderer(com.railwayteam.railways.registry.CRBlockEntities.COPYCAT_HEADSTOCK.get(),c->new NativeCouplerRenderer<>());
        });
    }
    private static String modelName(CouplerKind kind){return switch(kind){case LINK->"link_and_pin";case LINKLESS->"link_and_pin_linkless";case KNUCKLE->"knuckle_coupler";case KNUCKLE_SPLIT->"split_knuckle_coupler";case SCREWLINK->"screwlink_coupler";default->throw new IllegalArgumentException();};}
    private static String path(String mount,CouplerKind kind,boolean flip){return mount.equals("single_deco")?"single_deco/"+modelName(kind):"headstock/"+mount+"_"+modelName(kind)+(flip?"_upside_down":"");}
    private static void groups(JsonArray groups,Map<Integer,String> names){if(groups==null)return;for(var value:groups){if(!value.isJsonObject())continue;var group=value.getAsJsonObject();var children=group.getAsJsonArray("children");if(children==null)continue;for(var child:children)if(child.isJsonPrimitive()&&child.getAsJsonPrimitive().isNumber())names.put(child.getAsInt(),group.get("name").getAsString());else if(child.isJsonObject()){var nested=new JsonArray();nested.add(child);groups(nested,names);}}}
    private static void reload(ResourceManager resources){
        HardwareRenderData.ready=false;MESHES.clear();
        try{
            for(var kind:CouplerKind.values())if(kind!=CouplerKind.STANDARD)for(String mount:List.of("single_deco","wooden_headstock","copycat_headstock"))for(boolean flip:new boolean[]{false,true}){
                if(flip&&mount.equals("single_deco"))continue;String path=path(mount,kind,flip);
                try(var reader=resources.openAsReader(ResourceLocation.fromNamespaceAndPath("railways","models/block/buffer/"+path+".json"))){
                    var json=JsonParser.parseReader(reader).getAsJsonObject();var model=BlockModel.fromString(json.toString());var names=new HashMap<Integer,String>();groups(json.getAsJsonArray("groups"),names);var elements=new ArrayList<Element>();
                    for(int i=0;i<model.getElements().size();i++){
                        var element=model.getElements().get(i);boolean attachment=element.faces.values().stream().anyMatch(f->model.getMaterial(f.texture()).texture().getNamespace().equals("railways"));if(!attachment)continue;
                        String group=names.getOrDefault(i,"");Part part=Part.BODY;
                        if(kind==CouplerKind.LINK||kind==CouplerKind.LINKLESS){
                            if(element.to.x-element.from.x<1.1&&element.to.z-element.from.z<1.1&&element.to.y-element.from.y>=3)part=Part.PIN;
                            else if(group.equals("link")||group.equals("pin"))part=Part.LINK;
                        }else if(kind==CouplerKind.KNUCKLE||kind==CouplerKind.KNUCKLE_SPLIT){if(group.equals("knuckle"))part=Part.JAW;else if(element.from.z>=(mount.equals("single_deco")?12:9))part=Part.MOUNT;}
                        else if(kind==CouplerKind.SCREWLINK){if(group.equals("screw"))part=Part.SCREW;else if(group.equals("flat_links")||group.equals("shackle"))part=Part.RIGGING;}
                        elements.add(new Element(element,part));
                    }
                    if(elements.isEmpty())throw new IllegalStateException("No native coupler elements: "+path);
                    MESHES.put(path,new Mesh(model,elements,mount.equals("single_deco")?0:-3/16.0));
                }
            }
            HardwareRenderData.ready=true;
        }catch(Exception failure){MESHES.clear();org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).error("[CouplerHardware] Native model reload failed; keeping static models",failure);}
    }
    private static String mount(BlockState state){String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();return id.contains("copycat")?"copycat_headstock":id.contains("headstock")?"wooden_headstock":"single_deco";}
    private static boolean flip(BlockState state){for(var p:state.getProperties())if(p.getName().equals("upside_down"))return Boolean.TRUE.equals(state.getValue(p));return false;}
    private static final class Filter extends BakedModelWrapper<BakedModel> {
        Filter(BakedModel original){super(original);}
        @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state,ModelData data){
            var original=super.getModelData(level,pos,state,data);var levelNow=Minecraft.getInstance().level;boolean dynamic=levelNow!=null&&HardwareRenderData.ready&&RailwayHardware.coupler(state)&&SableCompanion.INSTANCE.getContaining(levelNow,pos)!=null;
            return original.derive().with(HardwareRenderData.DYNAMIC,dynamic).build();
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type){
            var quads=super.getQuads(state,side,random,data,type);
            if(!Boolean.TRUE.equals(data.get(HardwareRenderData.DYNAMIC)))return quads;
            return quads.stream().filter(q->!q.getSprite().contents().name().getNamespace().equals("railways")||!q.getSprite().contents().name().getPath().startsWith("block/buffer/")).toList();
        }
    }
    private static ClientSubLevelAccess body(CouplerEndpoint e){var b=SableCompanion.INSTANCE.getContaining(e.getLevel(),e.getBlockPos());return b instanceof ClientSubLevelAccess c?c:null;}
    private static Vector3d world(CouplerEndpoint e,float partial){var b=body(e);return b==null?null:b.renderPose(partial).transformPosition(new Vector3d(e.anchor).add(e.getBlockPos().getX(),e.getBlockPos().getY(),e.getBlockPos().getZ()),new Vector3d());}
    private static void part(PoseStack pose,VertexConsumer consumer,List<BakedQuad> quads,int light,int overlay){if(quads!=null)for(var quad:quads)consumer.putBulkData(pose.last(),quad,1,1,1,1,light,overlay);}
    private static void at(PoseStack pose,Vector3d point,Quaterniond rotation){pose.translate(point.x,point.y,point.z);pose.mulPose(new Quaternionf(rotation));pose.translate(-point.x,-point.y,-point.z);}
    @Override public void render(T owner,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var e=CouplerEndpoint.get(owner);if(e==null||!HardwareRenderData.ready||body(e)==null)return;var kind=RailwayHardware.kind(e);if(kind==null||kind==CouplerKind.STANDARD)return;
        String mounting=mount(e.getBlockState());var mesh=MESHES.get(path(mounting,kind,flip(e.getBlockState())));if(mesh==null)return;
        String paint=owner instanceof IDyedBuffer dyed&&dyed.getColor()!=null?dyed.getColor().getName():"";var quads=mesh.quads(paint);
        var facing=e.getBlockState().getValue(CouplerBlock.FACING);var facingRotation=new Quaterniond().rotationTo(new Vector3d(0,0,-1),new Vector3d(facing.getStepX(),0,facing.getStepZ()));
        double p=e.hardwareProgress(partial);var motion=CouplerMechanism.motion(kind,p);var peer=e.visualPeerPos!=null&&e.getLevel().hasChunkAt(e.visualPeerPos)?CouplerEndpoint.get(e.getLevel().getBlockEntity(e.visualPeerPos)):null;
        boolean valid=peer!=null&&peer.endpointId.equals(e.visualPeerId)&&e.endpointId.equals(peer.visualPeerId)&&body(peer)!=null;
        boolean active=valid&&e.endpointId.compareTo(peer.endpointId)<0;var target=valid?world(peer,partial):null;
        Vector3d toward=null;if(target!=null){toward=body(e).renderPose(partial).transformPositionInverse(target,new Vector3d()).sub(e.getBlockPos().getX(),e.getBlockPos().getY(),e.getBlockPos().getZ()).sub(.5,.5,.5).rotate(new Quaterniond(facingRotation).conjugate()).add(.5,.5,.5);}
        pose.pushPose();pose.translate(.5,.5,.5);pose.mulPose(new Quaternionf(facingRotation));pose.translate(-.5,-.5,-.5);
        var consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));part(pose,consumer,quads.get(Part.MOUNT),light,overlay);pose.pushPose();
        if((kind==CouplerKind.KNUCKLE||kind==CouplerKind.KNUCKLE_SPLIT)&&valid&&p>0&&toward!=null){
            var bearing=new Vector3d(.5,.5,.875+mesh.shift);var direction=new Vector3d(toward).sub(bearing);
            if(direction.lengthSquared()>.001){var turn=new Quaterniond().rotationTo(new Vector3d(0,0,-1),direction.normalize());double angle=turn.angle();if(angle>Math.toRadians(25))turn=new Quaterniond().slerp(turn,Math.toRadians(25)/angle);at(pose,bearing,turn);}
        }
        part(pose,consumer,quads.get(Part.BODY),light,overlay);
        if(kind==CouplerKind.KNUCKLE||kind==CouplerKind.KNUCKLE_SPLIT){
            pose.pushPose();at(pose,new Vector3d(11/16.0,.5,5/16.0+mesh.shift),new Quaterniond().rotateY(Math.toRadians(48)*motion.jawOpen()));part(pose,consumer,quads.get(Part.JAW),light,overlay);pose.popPose();
        }else if(kind==CouplerKind.LINK||kind==CouplerKind.LINKLESS){
            pose.pushPose();double lift=motion.pinLift();pose.translate(0,.18*Math.clamp(lift,0,1),0);part(pose,consumer,quads.get(Part.PIN),light,overlay);pose.popPose();
            var links=quads.get(Part.LINK);if(links==null&&active&&p>0)links=MESHES.get(path(mounting,CouplerKind.LINK,flip(e.getBlockState()))).quads(paint).get(Part.LINK);
            pose.pushPose();var pivot=new Vector3d(.5,.5,.5+mesh.shift);double insert=motion.linkInsertion();
            if(active&&toward!=null&&toward.distanceSquared(pivot)<16){
                var delta=new Vector3d(toward).sub(pivot);double length=delta.length();if(length>.001){at(pose,pivot,new Quaterniond().slerp(new Quaterniond().rotationTo(new Vector3d(0,0,-1),delta.normalize()),insert));}
                at(pose,pivot,new Quaterniond().rotateX(Math.toRadians(22.5)*insert));
            }else if(valid)at(pose,pivot,new Quaterniond().rotateX(-Math.PI/2*insert));
            part(pose,consumer,links,light,overlay);pose.popPose();
        }else if(kind==CouplerKind.SCREWLINK){
            pose.pushPose();var pivot=new Vector3d(.5,.5,.75+mesh.shift);double hang=Math.clamp(p/.35,0,1);
            if(active&&toward!=null&&toward.distanceSquared(pivot)<16){var delta=new Vector3d(toward).sub(pivot);if(delta.lengthSquared()>.001)at(pose,pivot,new Quaterniond().slerp(new Quaterniond().rotationTo(new Vector3d(0,-1,0),delta.normalize()),hang));}
            part(pose,consumer,quads.get(Part.RIGGING),light,overlay);
            pose.pushPose();if(active)at(pose,new Vector3d(.5,.22,.75+mesh.shift),new Quaterniond().rotateY(Math.PI*2*motion.screwTurns()));part(pose,consumer,quads.get(Part.SCREW),light,overlay);pose.popPose();pose.popPose();
        }
        pose.popPose();pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(T owner){return true;}
}
