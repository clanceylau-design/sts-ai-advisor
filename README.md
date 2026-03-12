# STS AI Advisor Mod

An AI-powered advisor mod for Slay the Spire that provides real-time battle suggestions using Claude or GPT-4.

## Features

- Real-time battle analysis and card play recommendations
- Support for both Claude (Anthropic) and GPT-4 (OpenAI) APIs
- Priority-based card suggestions with reasoning
- In-game UI panel with hotkey support

## Requirements

- Slay the Spire (Steam version)
- ModTheSpire 3.30.0+
- BaseMod 5.61.0+
- StSLib 1.4.0+

## Installation

1. Install ModTheSpire, BaseMod, and StSLib via Steam Workshop
2. Download the latest release of AI Advisor
3. Place the JAR file in your `Slay the Spire/mods/` folder
4. Launch the game via ModTheSpire

## Configuration

Edit `mods/sts-ai-advisor/config.json` to configure:

```json
{
  "apiKey": "your-api-key-here",
  "model": "claude-3-5-sonnet-20241022",
  "apiProvider": "anthropic",
  "enableAutoAdvice": true,
  "showReasoning": true,
  "requestTimeout": 30
}
```

### API Providers

- **Anthropic (Claude)**: Set `apiProvider` to `"anthropic"` and use your Anthropic API key
- **OpenAI (GPT-4)**: Set `apiProvider` to `"openai"` and use your OpenAI API key

### Models

For Anthropic:
- `claude-3-5-sonnet-20241022` (recommended)
- `claude-3-opus-20240229`

For OpenAI:
- `gpt-4o` (recommended)
- `gpt-4-turbo`

## Hotkeys

- **F4**: Toggle the advice panel
- **F3**: Manually request advice

## Building from Source

```bash
# Clone the repository
git clone https://github.com/your-repo/sts-ai-advisor.git
cd sts-ai-advisor

# Copy game JARs to libs/ folder
# - desktop-1.0.jar from your Slay the Spire installation
# - ModTheSpire.jar
# - BaseMod.jar
# - StSLib.jar

# Build
./gradlew build

# The output JAR will be in build/libs/
```

## Development Setup

### Prerequisites
- JDK 8 or JDK 11
- IntelliJ IDEA (recommended) or any Java IDE

### IntelliJ Setup
1. Open the project in IntelliJ
2. Wait for Gradle sync
3. Copy required JARs to `libs/` folder
4. Run `gradlew build` to verify setup

## Architecture

```
com.stsaiadvisor/
├── STSAIAdvisorMod.java     # Mod entry point
├── config/                   # Configuration management
├── model/                    # Data models
├── capture/                  # Game state capture
├── llm/                      # LLM API clients
├── event/                    # Event listeners
├── ui/                       # UI rendering
└── util/                     # Utilities
```

## API Keys

You need to obtain an API key from either:
- [Anthropic Console](https://console.anthropic.com/) for Claude
- [OpenAI Platform](https://platform.openai.com/) for GPT-4

**Important**: Keep your API key secure. Never share it or commit it to version control.

## License

MIT License

## Credits

- Built with [BaseMod](https://github.com/daviscook477/BaseMod)
- Powered by [ModTheSpire](https://github.com/kiooeht/ModTheSpire)