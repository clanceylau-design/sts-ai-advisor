# STS AI Advisor

An AI-powered advisor mod for Slay the Spire that provides real-time battle suggestions using LLM APIs.

## Features

- **Battle Scene**: Real-time card play recommendations with reasoning
- **Reward Scene**: Card selection suggestions based on deck archetype
- **3-Agent Architecture**: Analysis + Skill + Advisor collaboration
- **Local Knowledge Base**: Markdown-based tactics with LLM extraction
- **Multi-LLM Support**: Claude, GPT-4, and OpenAI-compatible APIs
- **Electron Overlay**: Independent floating panel with always-on-top support

## Requirements

- Slay the Spire (Steam version)
- ModTheSpire 3.30.0+
- BaseMod 5.61.0+
- StSLib 1.4.0+
- Node.js 18+ (for Electron Overlay)

## Installation

1. Install ModTheSpire, BaseMod, and StSLib via Steam Workshop
2. Download the latest release of AI Advisor
3. Place the JAR file in your `Slay the Spire/mods/` folder
4. Launch the game via ModTheSpire

## Configuration

### Game Configuration

Edit `mods/sts-ai-advisor/config.json`:

```json
{
  "apiKey": "your-api-key-here",
  "baseUrl": "https://api.openai.com/v1",
  "model": "gpt-4o",
  "apiProvider": "openai",
  "enableAutoAdvice": true,
  "requestTimeout": 30
}
```

### Local Development Configuration

For development, create a `local.properties` file in the project root:

```bash
# Copy the example file
cp local.properties.example local.properties
```

Edit `local.properties` with your paths:

```properties
# Game installation directory (for JAR deployment)
game.dir=D:\\SteamLibrary\\steamapps\\common\\SlayTheSpire

# Project development directory (for Overlay auto-start)
project.dir=C:\\Users\\YourName\\sts-ai-advisor

# Java 8 Home (optional, for Gradle compilation)
# java.home=C:\\Users\\YourName\\.jdks\\corretto-1.8.0_482
```

> **Note**: `local.properties` is gitignored to prevent committing machine-specific paths.

### Supported APIs

- **OpenAI**: GPT-4o, GPT-4-turbo
- **Anthropic**: Claude 3.5 Sonnet, Claude 3 Opus
- **OpenAI-Compatible**: DashScope (Qwen), DeepSeek, etc.

## Hotkeys

- **F4**: Toggle the overlay panel / Restart overlay if closed
- **F3**: Manually request advice

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                 Slay the Spire (Game Process)               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  STS AI Advisor Mod                                  │   │
│  │  - State capture, LLM calls                          │   │
│  │  - HTTP Client → localhost:17532                     │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP (localhost:17532)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Electron Overlay (Independent Process)          │
│  - Transparent always-on-top window                         │
│  - Receives and renders recommendations                     │
│  - Draggable positioning                                    │
└─────────────────────────────────────────────────────────────┘
```

## Building

```bash
git clone https://github.com/clanceylau-design/sts-ai-advisor.git
cd sts-ai-advisor

# Configure local paths
cp local.properties.example local.properties
# Edit local.properties with your paths

# Build
./gradlew build

# Build and deploy to game directory
./gradlew deploy
```

### Running the Overlay

```bash
cd overlay
npm install
npm start
```

For packaged distribution:
```bash
npm run build
# Output: dist/STS AI Advisor Overlay.exe
```

## Project Structure

```
sts-ai-advisor/
├── src/main/java/com/stsaiadvisor/
│   ├── agent/          # 3-Agent system (Analysis, Skill, Advisor)
│   ├── capture/        # Game state capture (Battle, Reward)
│   ├── config/         # Configuration management
│   ├── event/          # Event listeners
│   ├── knowledge/      # Skill manager & knowledge base
│   ├── llm/            # LLM API clients & prompt builders
│   ├── model/          # Data models
│   ├── overlay/        # Overlay HTTP client
│   └── util/           # Utilities
├── overlay/            # Electron Overlay application
│   ├── main.js         # Main process
│   ├── preload.js      # Security bridge
│   └── src/            # Renderer (HTML, CSS, JS)
├── skills/             # Markdown skill files
│   ├── battle/         # Battle tactics
│   └── reward/         # Card selection strategies
├── local.properties.example  # Local config template
└── README.md
```

## License

MIT License