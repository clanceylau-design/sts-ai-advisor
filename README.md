# STS AI Advisor

An AI-powered advisor mod for Slay the Spire that provides real-time battle suggestions using LLM APIs.

## Features

- **Battle Scene**: Real-time card play recommendations with reasoning
- **Reward Scene**: Card selection suggestions based on deck archetype
- **3-Agent Architecture**: Analysis + Skill + Advisor collaboration
- **Local Knowledge Base**: Markdown-based tactics with LLM extraction
- **Multi-LLM Support**: Claude, GPT-4, and OpenAI-compatible APIs

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

### Supported APIs

- **OpenAI**: GPT-4o, GPT-4-turbo
- **Anthropic**: Claude 3.5 Sonnet, Claude 3 Opus
- **OpenAI-Compatible**: DashScope (Qwen), DeepSeek, etc.

## Hotkeys

- **F4**: Toggle the advice panel
- **F3**: Manually request advice

## Architecture

```
com.stsaiadvisor/
├── agent/          # 3-Agent system (Analysis, Skill, Advisor)
├── capture/        # Game state capture (Battle, Reward)
├── event/          # Event listeners
├── knowledge/      # Skill manager & knowledge base
├── llm/            # LLM API clients & prompt builders
├── model/          # Data models
├── ui/             # UI rendering
└── util/           # Utilities
```

## Building

```bash
git clone https://github.com/clanceylau-design/sts-ai-advisor.git
cd sts-ai-advisor
./gradlew build
```

## License

MIT License