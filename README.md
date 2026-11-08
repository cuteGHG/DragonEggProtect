# DragonEggProtect

Paper plugin for Minecraft **26.3** (Java 25+).

## Features
- Dropped dragon eggs hover in place and emit portal particles.
- Protected dropped eggs cannot take damage, burn, merge, or despawn; their position is continuously stabilized so they cannot fall into the void.
- Dragon egg blocks are protected from player breaking and explosions.
- If a player logs out while carrying a dragon egg, the egg is placed at their location (or safely dropped there if the block is occupied).
- The recovery location defaults to the usual End exit-portal platform at **(0, 64, 0)** and is saved in the plugin config.

## Build
Requires JDK 25 and Maven:

```sh
mvn package
```

The plugin jar is created in `target/DragonEggProtect-1.0.0.jar`.

## Install
Put the jar into the server's `plugins` directory and restart Paper 26.3.
