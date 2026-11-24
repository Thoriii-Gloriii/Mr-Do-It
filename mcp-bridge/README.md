# Mr Do It phone bridge (stdio)

**This needs the free Mr Do It app on your Android phone: [download the APK](https://github.com/Thoriii-Gloriii/Mr-Do-It/releases/latest/download/mrdoit.apk) or see [Mr Do It on GitHub](https://github.com/Thoriii-Gloriii/Mr-Do-It).** Mr Do It lets AI agents read your screen, tap and type, and asks before anything risky. No root, no adb.

Mr Do It's phone tools (read the screen, tap, type, scroll, open apps, screenshots, notifications, the background browser…) are an MCP server inside the Mr Do It app on your Android phone. This bridge speaks MCP over stdio and passes every request on to Mr Do It, for any MCP client that starts servers as a command. It has no dependencies (Node 18+).

## From a computer (over Tailscale)

With the phone and the computer on the same tailnet, switch on **Settings → Phone tools in your own sessions → Phone tools on Tailscale** in Mr Do It. It shows the phone's address. Then:

```bash
claude mcp add -s user phone -e MRDOIT_URL=http://<phone's Tailscale address>:8765 -- npx -y mrdoit-phone-bridge
```

On Windows (not WSL), Claude Code starts npx through cmd: `... -- cmd /c npx -y mrdoit-phone-bridge`.

Or in any MCP client's config:

```json
{
  "mcpServers": {
    "phone": {
      "command": "npx",
      "args": ["-y", "mrdoit-phone-bridge"],
      "env": { "MRDOIT_URL": "http://<phone's Tailscale address>:8765" }
    }
  }
}
```

The first time the agent uses a phone tool, the phone asks you to pair this computer, on a card that names it and its Tailscale address. Approve it and the bridge keeps the key it gets in your settings folder (`~/.config/mrdoit-phone-bridge/keys.json`, or `%APPDATA%\mrdoit-phone-bridge\keys.json` on Windows), so later starts need no card. Remove the computer in Mr Do It's Settings and its key stops working: the next call asks to pair again.

## On the phone itself (Termux)

```bash
MRDOIT_TOKEN=<token from Mr Do It's Settings> npx -y mrdoit-phone-bridge
```

Most agents on the phone connect to `http://127.0.0.1:8765/mcp` directly instead: Mr Do It's Settings give the command for Claude Code, Codex, OpenCode, Antigravity and Grok.

## Settings

- `MRDOIT_URL`: Mr Do It's address, `http://127.0.0.1:8765` if not set (with or without `/mcp`).
- `MRDOIT_TOKEN`: a token to use instead of pairing (with or without `Bearer `).

When Mr Do It can't be reached, the bridge still lists the tools (from `tools.json`, a copy of Mr Do It's list) and each call says why it couldn't run; that's also what happens in the Dockerfile's container, which has no phone.

Mr Do It is an independent project, not made by or affiliated with Anthropic, OpenAI, Google or xAI.
