# KibbleCommands

**KibbleCommands** is a high-performance Paper/Spigot plugin for Minecraft 1.21+ that allows server administrators to define custom command aliases, set permissions, manage cooldowns, execute commands as console or player, and interact via a built-in GUI menu.

---

## Features

- **Custom Command Aliases**: Easily map short commands (e.g. `/gmc`, `/day`) to full commands with custom arguments and placeholders.
- **Dynamic Placeholders**: Supports `{player}`, `{sender}`, `{uuid}`, `{world}`, `{args}`, `{arg:1}`, `{arg:2}`, etc.
- **Flexible Execution**: Run commands as the sender or force execution as `console`.
- **Cooldown Support**: Set custom per-player cooldowns for each alias.
- **Admin GUI**: Built-in interactive inventory GUI (`/kc gui`) for viewing and managing registered aliases.
- **Permission Bridges**: Fully compatible with LuckPerms, Vault, and standard Bukkit permission nodes.

---

## Requirements

- **Server**: Paper / Spigot / Purpur 1.21+ (Java 21 required)
- **Dependencies**: Soft dependencies include `LuckPerms`, `Vault`, and `Essentials`.

---

## Commands & Permissions

| Command | Usage | Description | Default Permission |
|---------|-------|-------------|-------------------|
| `/kibblecommands` | `/kc <help\|list\|reload\|gui\|add\|remove>` | Main management command | `kibblecommands.admin` |
| `/kc gui` | `/kc gui` | Opens the Admin GUI menu | `kibblecommands.gui` |
| `/kc reload` | `/kc reload` | Reloads `config.yml` | `kibblecommands.reload` |

---

## Building from Source

To compile the plugin yourself:

1. Clone the repository:
   ```bash
   git clone https://github.com/minedkibbles21/kibblecommands.git
   cd kibblecommands
   ```

2. Build with Maven:
   ```bash
   mvn clean package
   ```

3. The compiled plugin JAR will be located in the `target/` directory:
   ```
   target/KibbleCommands-1.0.0.jar
   ```

---

## Configuration Example (`config.yml`)

```yaml
message-prefix: "&8[&6KibbleCommands&8]&r "
require-use-permission: false
notify-on-alias-use: false

aliases:
  gmc:
    command: "minecraft:gamemode creative {player}"
    description: "Switch yourself to Creative mode"
    permission: "kibblecommands.alias.gmc"
    player-only: true
    pass-args: false
    execute-as: "console"
```

---

## License

**Copyright (c) 2026 MinedKibbles21**

This project is licensed under the **[Creative Commons Attribution-NonCommercial-NoDerivatives 4.0 International (CC BY-NC-ND 4.0)](LICENSE)** license.

Under this license:
- ❌ **No Derivatives**: You may **not** distribute or publish modified versions of this software.
- ❌ **Non-Commercial**: You may **not** use or sell this software for commercial gain.
- ✅ **Personal Use**: You may view the source code and compile it for personal use.
