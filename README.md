# Smol Agent

Client-only Fabric mod for Minecraft 26.3. Type `/smolpp` to open the menu:

- **Rename** (left): pairs of *name part → replacement*. Every occurrence in any
  player's name, in the tab list, overhead nametags and chat, is replaced.
  Matching is case-insensitive; the replacement follows the case of the match
  (`agent`→`smol`, `Agent`→`Smol`, `AGENT`→`SMOL`).
- **Translucent** (right): name parts. Any player whose server nickname (the
  name the server puts in the tab list) contains one of them is drawn
  translucent. The Minecraft account name only counts for players without a
  nickname.
  The slider sets the opacity, 1–100 %.

Changes apply live; closing the menu (Done or Esc) saves to
`config/smol-agent.json`. First start defaults: `Agent → Smol`, translucent
`agent`, `smol` at 30 %.

## Build / test

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home
./gradlew build               # jar in build/libs/, unit tests
./gradlew runClientGameTest   # drives /smolpp in a real client, ~25 s
```

## License

MIT — see `LICENSE`.
