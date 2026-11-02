package com.guidedrailways;

import net.minecraft.commands.arguments.UuidArgument;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator transfer and recovery tools, alongside automatic vanilla portal entry. */
final class TransferCommands {
  private TransferCommands() {}
  static void register(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("physicalrailways").requires(source -> source.hasPermission(2))
        .then(Commands.literal("transfer")
            .then(Commands.literal("recover").then(Commands.argument("operation", UuidArgument.uuid()).executes(context -> {
              try {
                int count = TransferRecovery.retry(context.getSource().getServer(), UuidArgument.getUuid(context, "operation"));
                context.getSource().sendSuccess(() -> Component.translatable("message.guidedrailways.transfer.recovered", count), true);
                return count;
              } catch (RuntimeException failure) { context.getSource().sendFailure(UserText.error(failure)); return 0; }
            })))
            .then(Commands.literal("list").executes(context -> {
              var source = context.getSource();
              var container = SubLevelContainer.getContainer(source.getLevel());
              int count = 0;
              if (container != null) for (var body : container.getAllSubLevels()) {
                if (body.isRemoved()) continue;
                var pose = body.logicalPose().position();
                source.sendSuccess(() -> Component.translatable("message.guidedrailways.transfer.body",
                    body.getUniqueId().toString(), UserText.userInput(body.getName()),
                    Math.round(pose.x), Math.round(pose.y), Math.round(pose.z)), false);
                count++;
              }
              return count;
            }))
            .then(Commands.argument("body", UuidArgument.uuid())
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                    .then(Commands.argument("position", Vec3Argument.vec3(false)).executes(context -> {
                      try {
                        var result = PhysicalTransfer.transfer(context.getSource().getLevel(),
                            UuidArgument.getUuid(context, "body"), DimensionArgument.getDimension(context, "dimension"),
                            Vec3Argument.getVec3(context, "position"));
                        context.getSource().sendSuccess(() -> Component.translatable(
                            "message.guidedrailways.transfer.done", result.bodies(), result.operation().toString()), true);
                        return result.bodies();
                      } catch (RuntimeException failure) {
                        context.getSource().sendFailure(UserText.error(failure));
                        return 0;
                      }
                    }))))));
  }
}
