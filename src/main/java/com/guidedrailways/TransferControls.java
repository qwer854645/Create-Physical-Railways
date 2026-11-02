package com.guidedrailways;

import net.minecraft.nbt.*;

/** Park at a portal exit without dropping the physical schedule item or restarting old station waits. */
final class TransferControls {
  private TransferControls() {}
  static void park(CompoundTag data) {
    var chunks = data.getCompound("plot").getCompound("chunks");
    for (String key : chunks.getAllKeys())
      for (var value : chunks.getCompound(key).getList("block_entities", Tag.TAG_COMPOUND)) {
        var be = (CompoundTag) value;
        if (!be.getString("id").equals("guidedrailways:driver_console")) continue;
        be.putInt("Notch", -4); be.putBoolean("Emergency", true);
        if (!be.contains("PhysicalSchedule", Tag.TAG_COMPOUND)) continue;
        var schedule = be.getCompound("PhysicalSchedule");
        schedule.putBoolean("Running", false); schedule.putBoolean("Waiting", false);
        schedule.putInt("Status", ScheduleController.PAUSED);
        schedule.remove("Station"); schedule.remove("Conditions");
      }
  }
}
