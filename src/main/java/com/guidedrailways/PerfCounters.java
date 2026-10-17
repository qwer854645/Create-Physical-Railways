package com.guidedrailways;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.WeakHashMap;
/** Opt-in wall-clock measurements, not a total server tick or frame-rate benchmark. */
final class PerfCounters {
    private static final class Stats {long next,steps,total,max,queries,checks,builds,checkTime,maxCheck;}
    private static final Map<ServerLevel,Stats> STATS=new WeakHashMap<>();
    static void step(ServerLevel level,long nanos){var s=STATS.computeIfAbsent(level,l->new Stats());s.steps++;s.total+=nanos;s.max=Math.max(s.max,nanos);}
    static void query(ServerLevel level){if(TrainConfig.PERFORMANCE_LOGGING.get())STATS.computeIfAbsent(level,l->new Stats()).queries++;}
    static void index(ServerLevel level,long nanos,boolean rebuilt){if(TrainConfig.PERFORMANCE_LOGGING.get()){var s=STATS.computeIfAbsent(level,l->new Stats());s.checks++;if(rebuilt)s.builds++;s.checkTime+=nanos;s.maxCheck=Math.max(s.maxCheck,nanos);}}
    static void report(ServerLevel level,int bogies){
        if(!TrainConfig.PERFORMANCE_LOGGING.get()){STATS.remove(level);return;}
        var s=STATS.computeIfAbsent(level,l->new Stats());if(level.getGameTime()<s.next)return;s.next=level.getGameTime()+20;
        if(s.steps>0&&bogies>0)org.slf4j.LoggerFactory.getLogger(GuidedRailways.ID).info("[GuidedPerf] bogies={} steps={} meanMs={} maxMs={} pathQueries={} indexChecks={} indexCheckTotalMs={} indexCheckMaxMs={} indexRebuilds={} scope=java_guidance_only",bogies,s.steps,s.total/(double)s.steps/1e6,s.max/1e6,s.queries,s.checks,s.checkTime/1e6,s.maxCheck/1e6,s.builds);
        s.steps=s.total=s.max=s.queries=s.checks=s.builds=s.checkTime=s.maxCheck=0;
    }
    static void unload(ServerLevel level){STATS.remove(level);}
}
