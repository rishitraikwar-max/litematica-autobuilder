# Litematica AutoBuilder

Client-side Fabric mod for Minecraft 26.2 that autonomously builds a loaded
Litematica schematic using a from-scratch pathfinder and vanilla-legal
movement emulation (no Baritone / no third-party pathfinder dependency).

## Project status

This is a work in progress. The following subsystems are implemented:

- `spatial/` - voxel collision, hazard classification, line-of-sight
- `navigation/` - custom 3D A* pathfinder, path smoothing, movement input
- `rotation/` - smooth camera rotation controller
- `litematica/` - schematic reading, rotation/mirror transform handling
- `placement/` - standing-position solver, block orientation, packet dispatch

**Not yet implemented** (compilation will fail until these exist):

- `inventory/` - hotbar/container management
- `builder/` - the finite state machine coordinating everything
- `recovery/` - stuck detection and error recovery
- `render/` - in-world debug rendering
- `mixin/` - `KeyboardInputMixin`, `ClientPlayerEntityMixin`
- `AutoBuilderMod.java`, `AutoBuilderClient.java` - mod entrypoints

`fabric.mod.json` already references the entrypoint and mixin classes above,
so a build attempt will fail until they're added - this is expected, not a
structural problem.

## Repository structure

```
litematica-autobuilder/
├── .github/workflows/build.yml   # CI: builds the jar on every push
├── src/main/java/com/example/autobuilder/
│   ├── config/
│   ├── litematica/
│   ├── navigation/
│   ├── placement/
│   ├── rotation/
│   └── spatial/
├── src/main/resources/
│   ├── fabric.mod.json
│   └── autobuilder.mixins.json
├── build.gradle
├── gradle.properties
└── settings.gradle
```

## Building

This repo does not check in a Gradle wrapper jar, so CI (and local builds)
use the `gradle/actions/setup-gradle` GitHub Action to install Gradle
directly. Locally: install Gradle + JDK 25, then run `gradle build` from
the repo root. The jar lands in `build/libs/`.

## Dependency versions

`build.gradle` targets Minecraft 26.2, Fabric Loader 0.19.3, Fabric API
0.152.0+26.2, MaLiLib 26.2-0.29.3, and Litematica 26.2-0.28.4. These
coordinates and the `masa.dy.fi` maven URL should be double-checked against
current upstream releases if a build fails to resolve dependencies.
