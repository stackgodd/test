# Auction House Timer + Papering helper (Fabric, client-side) - Minecraft 26.1 / 26.2 / 26.3

This folder is SOURCE CODE. You need to turn it into a .jar once, then put the .jar in your `mods` folder
(next to Fabric API). Two ways:

## Way A - no installs (GitHub builds it for you)
1. Make a free account at github.com, click **New repository**, name it anything, **Create**.
2. On the new repo page click **uploading an existing file**.
3. Open this folder, select EVERYTHING inside it (including the hidden `.github` folder - on Mac press
   Cmd+Shift+. to show it; on Windows turn on View > Hidden items) and drag it all into the page. Click **Commit changes**.
4. Open the **Actions** tab > the **Build mod jars** run. After ~5 minutes scroll down to **Artifacts**.
5. Download the one for your Minecraft version, unzip it, and put the `.jar` in your `mods` folder.

## Way B - build on your PC
Install Java 25 (https://adoptium.net, Temurin 25), then run `build-windows.bat` (or `./build.sh` on Mac/Linux),
pick your Minecraft version. The jar appears in the `mods-output` folder.

## In the game
Needs Fabric Loader 0.20.1+ and Fabric API for your Minecraft version. Nothing is needed on the server.

### Auction
- `/timer 30s` (also `2m`, `1m30s`) sets the length. **K** starts, **J** stops.
- The held item (or the dropped item / item frame you look at) is copied when you press K.
- `/ahs` = settings, `/ahs move` = drag the panels.

### Papering
- `/pap on` turns it on. Every payment to you ("X paid you $50k") becomes a bet in a queue and the chat line
  gets a `[bet #3]` tag. The panel shows the next bet, what a win pays, the queue, session profit, and the last results.
- Flick the droppers, then press **Y** (client wins) or **N** (client loses; a tie is a loss).
  The mod prints the result in your chat, logs it, and on a win opens the chat with `/pay name amount` already typed
  (you press Enter yourself - the mod never sends anything).
- `/pap win|lose|skip|undo|clear|reset|add <player> <amount>|settings`.
- The payment pattern and pay command are editable in `/ahs` > Papering. The defaults are guesses - paste me a real
  DonutSMP payment line and I'll fix them.
