# Smol Agent

A client-only Fabric mod for Minecraft 26.1.2 with two cosmetic effects for
players whose name contains `Agent` or `agent`:

- their displayed name is rewritten (`Agent` → `Smol`, `agent` → `smol`) in
  the tab list, in overhead nametags, and in chat messages
- they are rendered at 30% opacity — skin, cape, armor, glints, and nametag

## Install

Drop `smol-agent-1.1.0.jar` into your Fabric 26.1.2 client's `mods/` folder
alongside Fabric API `0.149.0+26.1.2` or newer. Requires Java 25.

## Build

```sh
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home \
  ./gradlew build
```

Output jar is in `build/libs/`.

## License

MIT — see `LICENSE`.
