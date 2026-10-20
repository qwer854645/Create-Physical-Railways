package com.guidedrailways;

import java.util.*;
import net.minecraft.server.level.ServerLevel;

/**
 * Longitudinal demand and consist power allocation; never changes positions or velocity directly.
 */
final class VehicleTraction {
  private VehicleTraction() {}

  static void allocate(ServerLevel level) {
    for (var component : TrainConsists.get(level).groups()) {
      var group = component.units();
      double mass = component.mass();
      var rpm = new double[group.size()];
      for (int i = 0; i < rpm.length; i++) {
        var u = group.get(i);
        rpm[i] = u.detached || u.mount.braking ? 0 : u.mount.getSpeed();
      }
      var shares = TractionBudget.shares(mass, rpm);
      boolean powered = java.util.Arrays.stream(shares).anyMatch(m -> m > 0);
      for (int i = 0; i < shares.length; i++) {
        var u = group.get(i);
        u.tractionMass = shares[i];
        u.trainPowered = powered;
      }
    }
  }

  static double force(VehiclePhysics.Unit u, double speed, double dt) {
    double force;
    if (u.command != null) {
      var control = u.command;
      double ceiling = control.sign() * control.ceiling();
      boolean hold =
          control.hold()
              || control.owner().isRemoved()
              || u.mount.braking
              || control.navigation() != null && control.navigation().routeRetry;
      if (control.mode() == TrainCommand.Mode.MANUAL) {
        ceiling =
            Math.copySign(
                control.owner().manualCeiling(control.notch(), Math.abs(ceiling)), ceiling);
        force =
            DriverModel.manualForce(
                speed,
                ceiling,
                u.mount.getSpeed(),
                u.mass,
                control.notch(),
                control.emergency(),
                hold,
                TrainConfig.FORCE_PER_RPM.get(),
                TrainConfig.ACCELERATION.get(),
                TrainConfig.BRAKING.get(),
                TrainConfig.COASTING.get(),
                dt,
                u.tractionMass,
                u.trainPowered);
      } else
        force =
            DriverModel.force(
                speed,
                ceiling,
                u.mount.getSpeed(),
                u.mass,
                control.notch(),
                control.emergency(),
                hold,
                TrainConfig.FORCE_PER_RPM.get(),
                TrainConfig.ACCELERATION.get(),
                TrainConfig.BRAKING.get(),
                TrainConfig.COASTING.get(),
                dt,
                u.tractionMass,
                u.trainPowered);
    } else if (u.driver != null) {
      // A console without an authoritative command must hold rather than drive.
      force = DriveModel.coastForce(speed, u.mass, TrainConfig.BRAKING.get(), dt);
    } else {
      force =
          DriveModel.force(
              speed,
              u.mount.getSpeed(),
              u.mass,
              u.mount.braking,
              TrainConfig.speedPerRpm(),
              TrainConfig.FORCE_PER_RPM.get(),
              TrainConfig.ACCELERATION.get(),
              TrainConfig.BRAKING.get(),
              u.tractionMass);
      if (!u.mount.braking && !u.trainPowered && Math.abs(u.mount.getSpeed()) < .001)
        force += DriveModel.coastForce(speed, u.mass, TrainConfig.COASTING.get(), dt);
    }
    return force;
  }

  static double requestedSpeed(VehiclePhysics.Unit u) {
    var c = u.command;
    if (c != null)
      return !c.hold() && !c.emergency() && c.notch() > 0
          ? c.sign()
              * (c.mode() == TrainCommand.Mode.MANUAL
                  ? c.owner().manualCeiling(c.notch(), c.ceiling())
                  : c.ceiling())
          : 0;
    return u.driver == null ? u.mount.getSpeed() : 0;
  }
}
