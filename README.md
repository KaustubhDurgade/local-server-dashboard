# Local Server Dashboard

Create, manage, and publicly expose a local Paper Minecraft server from a Fabric client mod.

This project is a V1 vertical slice of a Minecraft local-server dashboard:

- A **Fabric 1.21.11 client mod** adds a `Local Servers` button to the Minecraft title screen.
- A bundled **background manager jar** is extracted and launched by the mod.
- The manager creates and controls local **Paper 1.21.11** server folders.
- The manager downloads Paper, installs plugins from Modrinth, starts/stops servers, and keeps running after Minecraft closes.
- A **Paper bridge plugin** is automatically installed into created servers.
- A **relay jar** can run on a public machine/VPS so players can join the local server without being on the same Wi-Fi.

## Status

Working V1. Not production-hardened.

Verified locally on July 3, 2026:

- `./gradlew build` completed successfully.
- Manager self-check passed.
- Relay self-check passed.
- Manager created a Paper `1.21.11` server.
- Manager downloaded Paper build `132`.
- Manager installed `Chunky-Bukkit-1.4.40.jar` from Modrinth.
- Manager auto-installed `localservers-bridge.jar`.
- Paper booted and loaded `Chunky` and `LocalServersBridge`.
- The relay tunnel worked: a Minecraft status request through the relay public port returned `Paper 1.21.11` and the `Local Server Dashboard` MOTD.
- Smoke-test Java processes and ports were cleaned up after verification.

## Project Layout

```text
local-server-dashboard/
  fabric-mod/      Fabric client mod and Minecraft title-screen dashboard
  manager/         Background process that owns servers, downloads, plugins, and tunnels
  paper-plugin/    Paper bridge plugin installed into created servers
  relay/           Public TCP relay for forwarding player traffic back to the local server
```

## Architecture

```text
Minecraft Fabric client
  -> Local Servers dashboard UI
  -> extracts/starts manager.jar
  -> calls manager over http://127.0.0.1:45631

manager.jar
  -> creates Paper server folders
  -> downloads Paper from PaperMC Fill v3
  -> installs Modrinth Paper plugins
  -> starts/stops Paper as a separate Java process
  -> opens an outbound tunnel to relay.jar

relay.jar on a public machine
  -> listens for players on public-port
  -> listens for manager control/data sockets
  -> forwards TCP bytes between players and the local Paper server

Paper server
  -> runs localservers-bridge.jar
  -> runs user-installed plugins
```

The Fabric mod is only the UI/bootstrapper. The manager is the long-running local app. This is intentional: once Minecraft closes, the Fabric mod is gone, but the manager can keep the server process alive.

## Requirements

- Java 21
- Minecraft Java Edition `1.21.11`
- Fabric Loader `0.19.3+`
- Fabric API for `1.21.11`
- A public machine/VPS if you want people outside your network to join

## Build

```sh
./gradlew build
```

Built jars:

```text
fabric-mod/build/libs/fabric-mod-0.1.0.jar
manager/build/libs/manager-0.1.0.jar
paper-plugin/build/libs/paper-plugin-0.1.0.jar
relay/build/libs/relay-0.1.0.jar
```

The Fabric mod jar contains the manager jar. The manager jar contains the Paper bridge plugin.

## Install The Fabric Mod

1. Build the project.
2. Put `fabric-mod/build/libs/fabric-mod-0.1.0.jar` into your Fabric instance `mods/` folder.
3. Launch Minecraft `1.21.11`.
4. Click `Local Servers` on the title screen.

On first use, the mod extracts:

```text
<minecraft-instance>/local-server-dashboard/manager.jar
```

The manager stores created servers under:

```text
<minecraft-instance>/local-server-dashboard/servers/
```

## Dashboard Fields

- `Server`: local server id/folder name
- `RAM`: max heap in MB, passed as `-Xmx`
- `CPU`: Java active processor count, passed as `-XX:ActiveProcessorCount`
- `Port`: local Paper server port
- `Modrinth slug`: plugin slug to install, for example `chunky`
- `Relay host`: public relay hostname/IP
- `Public`: relay port players join
- `Control`: relay control port used by the manager
- `Data`: relay data port used by the manager
- `Relay token`: shared token required by the relay control socket

## Run A Relay

Run `relay.jar` on a public machine/VPS:

```sh
java -jar relay/build/libs/relay-0.1.0.jar \
  --public-port 25577 \
  --control-port 45640 \
  --data-port 45641 \
  --token "change-this-token"
```

Open these ports on the public machine:

- `25577/tcp`: player join port
- `45640/tcp`: manager control socket
- `45641/tcp`: manager data sockets

In the Minecraft dashboard:

- set `Relay host` to the public machine hostname/IP
- set the three relay ports to match the relay command
- set `Relay token` to the same token
- click `Start Tunnel`

Players join:

```text
<relay-host>:25577
```

## Manager API

Default manager address:

```text
http://127.0.0.1:45631
```

Endpoints:

```text
GET  /health
GET  /version
POST /shutdown
GET  /servers
POST /servers/create
POST /servers/{id}/start
POST /servers/{id}/stop
GET  /servers/{id}/logs
POST /servers/{id}/modrinth/install
GET  /servers/{id}/tunnel
POST /servers/{id}/tunnel/start
POST /servers/{id}/tunnel/stop
GET  /modrinth/search?q=<query>
```

Example:

```sh
curl -X POST http://127.0.0.1:45631/servers/create \
  -H 'Content-Type: application/json' \
  -d '{"id":"main","minecraftVersion":"1.21.11","ramMb":4096,"cpuCores":2,"serverPort":25565}'
```

## Manual Verification

Build and self-check:

```sh
./gradlew build
java -ea -jar manager/build/libs/manager-0.1.0.jar --self-check
java -ea -jar relay/build/libs/relay-0.1.0.jar --self-check
```

Start the manager on a test port:

```sh
java -jar manager/build/libs/manager-0.1.0.jar --root /tmp/localservers-test --port 45632
```

Start a relay locally:

```sh
java -jar relay/build/libs/relay-0.1.0.jar \
  --public-port 25578 \
  --control-port 45643 \
  --data-port 45644 \
  --token test
```

Create and start a server:

```sh
curl -X POST http://127.0.0.1:45632/servers/create \
  -H 'Content-Type: application/json' \
  -d '{"id":"smoke","minecraftVersion":"1.21.11","ramMb":1024,"cpuCores":1,"serverPort":25567}'

curl -X POST http://127.0.0.1:45632/servers/smoke/modrinth/install \
  -H 'Content-Type: application/json' \
  -d '{"project":"chunky","loader":"paper"}'

curl -X POST http://127.0.0.1:45632/servers/smoke/start \
  -H 'Content-Type: application/json' \
  -d '{}'
```

Start the tunnel:

```sh
curl -X POST http://127.0.0.1:45632/servers/smoke/tunnel/start \
  -H 'Content-Type: application/json' \
  -d '{"relayHost":"127.0.0.1","controlPort":45643,"dataPort":45644,"publicPort":25578,"localPort":25567,"relayToken":"test"}'
```

Check status:

```sh
curl http://127.0.0.1:45632/servers/smoke/tunnel
```

## Version Pins

- Minecraft: `1.21.11`
- Java: `21`
- Fabric Loom: `1.14.8`
- Fabric Loader: `0.19.3`
- Fabric API: `0.141.4+1.21.11`
- Yarn: `1.21.11+build.6`
- Paper API: `1.21.11-R0.1-SNAPSHOT`
- Paper runtime smoke-tested build: `1.21.11-132`

## Current Limitations

- Fabric-only client mod.
- Paper-only server creation.
- Modrinth-only plugin install.
- CurseForge is not implemented.
- No native launch-on-login service yet.
- Relay traffic is raw TCP forwarding. It is token-gated for the manager control socket, but player traffic is not encrypted by the relay.
- No relay account system.
- No automatic relay hosting. You run `relay.jar` yourself.
- No modpack dependency resolver yet.
- No GUI log viewer beyond returning/tailing logs through the manager API.

## Security Notes

- Use a random relay token.
- Do not expose the manager API publicly. It is intended for `127.0.0.1` only.
- Run the relay on a machine you control.
- Treat the relay public port like a Minecraft server port: anyone who can reach it can attempt to join.

## Roadmap

- Real server list UI instead of a single editable server id.
- Plugin/mod search results UI.
- Dependency-aware Modrinth install.
- CurseForge support.
- System tray app.
- Launch-on-login service install.
- Better log viewer.
- Relay auth/session management.
- Relay deployment guide for common VPS providers.
- Multi-loader support: Fabric server, NeoForge, Quilt, and plugin/mod loader selection.

## References

- [Fabric documentation](https://docs.fabricmc.net/)
- [Paper documentation](https://docs.papermc.io/)
- [PaperMC Downloads Service](https://docs.papermc.io/misc/downloads-service/)
- [Modrinth API](https://docs.modrinth.com/api/)
- [e4mc](https://github.com/vgskye/e4mc-minecraft-architectury)
