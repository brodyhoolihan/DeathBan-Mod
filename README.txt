DynamicDeathBan (Fabric, Minecraft 26.3) - mod version of the DynamicDeathBan plugin.

BUILD WITHOUT INSTALLING ANYTHING (GitHub):
1. Create a free GitHub account and a new repository.
2. Upload ALL files from this folder (including the hidden .github folder).
3. Open the Actions tab -> wait for "Build" to finish -> open the run.
4. Download the "DynamicDeathBan" artifact (a zip containing the mod jar).

BUILD LOCALLY: install JDK 25 + Gradle 9.8.2, then run:  gradle build
The jar appears in build/libs/

INSTALL:
- Server: put the jar + Fabric API in the server's mods folder.
- Players (optional but recommended): put the same jar + Fabric API in their mods
  folder to get the live countdown on the death screen. Players without it are kicked
  right away with the ban message, exactly like the plugin did.

Edit config/dynamicdeathban.json and run /ddb reload.
Commands (op): /ddb reload | check <player> | pardon <player> | set <player> <duration>
