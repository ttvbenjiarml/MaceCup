# MaceCup MCP Control Server

Local MCP sidecar for controlling this development network from an MCP-capable client.

This is not an AMP startup script and not a Minecraft plugin. AMP should still run only:

- Paper: `paper-1.21.11.jar`
- Velocity: `velocity.jar`

Install dependencies:

```powershell
cd tools/macecup-mcp
npm install
```

Run manually:

```powershell
npm start
```

MCP tools exposed:

- `list_servers`
- `sync_built_jars`
- `start_server`
- `stop_server`
- `send_console_command`
- `read_console`
- `list_files`
- `read_file`
- `write_file`
- `copy_file`

The tools are restricted to these local server folders:

- `lobby-practice`
- `event-1`
- `velocity-na`
- `velocity-eu`

Only servers started by this MCP process can receive console commands or graceful stop commands.
