# meteor-cyber-addon

Meteor Client addon with Cyber combat and utility modules.

## Features

### Modules (Cyber category)
- `trigger-bot`: Instantly attacks when your crosshair is on a valid target.
- `item-id`: Shows the hovered item's registry ID in tooltips.
- `auto-tool+`: Automatically switches to the most effective mining tool.
- `sprint+`: Automatically sprints with vanilla-like conditions.

### Commands
- `.cpos` (`.copypos`): Copy your current coordinates to clipboard.
- `.sping` (`.ping`): Show current server ping in ms.

## Requirements
- Java 21+
- Minecraft `1.21.11`
- Fabric Loader `>=0.18.2`
- Meteor Client

## Build
```bash
./gradlew build
```

Built jar:
- `build/libs/meteor-cyber-1.0.0.jar` (current version)

## Install
1. Build the jar.
2. Put the jar in your Minecraft `mods` folder.
3. Start the game with Fabric + Meteor Client.

## Disclaimer
Compatibility with anti-cheat environments has been tuned, but behavior and detection outcomes are not guaranteed. Use at your own risk.

## License
GPL-3.0
