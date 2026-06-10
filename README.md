# Smol Agent

A client-only Fabric mod for Minecraft 26.1.2 that cosmetically rewrites
`Agent` → `Smol` and `agent` → `smol` in the tab list, in overhead nametags,
and in chat messages.

Purely cosmetic. Only your own client sees the rewritten names — the server,
other players, and gameplay are unaffected.

## Install

Drop `smol-agent-1.0.0.jar` into your Fabric 26.1.2 client's `mods/` folder
alongside Fabric API.

## Build

```sh
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home \
  ./gradlew build
```

Output jar is in `build/libs/`.

## License

MIT — see `LICENSE`.
