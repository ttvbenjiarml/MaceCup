import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';
import { spawn } from 'node:child_process';
import { createWriteStream, existsSync, readdirSync } from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const JAVA = findJava();
const SERVERS = {
  'lobby-practice': {
    type: 'paper',
    dir: path.join(ROOT, 'lobby-practice'),
    jar: 'paper-1.21.11.jar',
    defaultXms: '512M',
    defaultXmx: '1024M',
    stopCommand: 'stop',
    tailLog: path.join(ROOT, 'lobby-practice', 'logs', 'mcp-console.log'),
  },
  'event-1': {
    type: 'paper',
    dir: path.join(ROOT, 'event-1'),
    jar: 'paper-1.21.11.jar',
    defaultXms: '512M',
    defaultXmx: '1024M',
    stopCommand: 'stop',
    tailLog: path.join(ROOT, 'event-1', 'logs', 'mcp-console.log'),
  },
  'velocity-na': {
    type: 'velocity',
    dir: path.join(ROOT, 'velocity-na'),
    jar: 'velocity.jar',
    defaultXms: '256M',
    defaultXmx: '768M',
    stopCommand: 'shutdown',
    tailLog: path.join(ROOT, 'velocity-na', 'logs', 'mcp-console.log'),
  },
  'velocity-eu': {
    type: 'velocity',
    dir: path.join(ROOT, 'velocity-eu'),
    jar: 'velocity.jar',
    defaultXms: '256M',
    defaultXmx: '768M',
    stopCommand: 'shutdown',
    tailLog: path.join(ROOT, 'velocity-eu', 'logs', 'mcp-console.log'),
  },
};

const children = new Map();
const memoryPattern = /^(?:[1-9]\d{0,2})(?:M|G)$/i;

const serverNameSchema = z.enum(Object.keys(SERVERS));
const relativePathSchema = z.string().min(1).max(240).default('.');

const mcp = new McpServer({
  name: 'macecup-control',
  version: '1.0.0',
});

mcp.registerTool(
  'list_servers',
  {
    title: 'List MaceCup Servers',
    description: 'List known local MaceCup server folders and MCP-managed process state.',
    inputSchema: {},
  },
  async () => text(toolState())
);

mcp.registerTool(
  'sync_built_jars',
  {
    title: 'Sync Built Plugin Jars',
    description: 'Copy freshly built MaceCupCore and MaceCupProxyBridge jars into every local server plugins folder.',
    inputSchema: {},
  },
  async () => {
    const copies = [
      ['source/MaceCupCore/build/libs/MaceCupCore.jar', 'lobby-practice/plugins/MaceCupCore.jar'],
      ['source/MaceCupCore/build/libs/MaceCupCore.jar', 'event-1/plugins/MaceCupCore.jar'],
      ['source/MaceCupProxyBridge/build/libs/MaceCupProxyBridge.jar', 'velocity-na/plugins/MaceCupProxyBridge.jar'],
      ['source/MaceCupProxyBridge/build/libs/MaceCupProxyBridge.jar', 'velocity-eu/plugins/MaceCupProxyBridge.jar'],
    ];
    const results = [];
    for (const [from, to] of copies) {
      const source = path.join(ROOT, from);
      const target = path.join(ROOT, to);
      await fs.mkdir(path.dirname(target), { recursive: true });
      await fs.copyFile(source, target);
      const stat = await fs.stat(target);
      results.push(`${to} (${stat.size} bytes)`);
    }
    return text(`Synced built jars:\n${results.map((line) => `- ${line}`).join('\n')}`);
  }
);

mcp.registerTool(
  'start_server',
  {
    title: 'Start Local Server',
    description: 'Start one local Paper or Velocity server jar from its folder and capture console output.',
    inputSchema: {
      server: serverNameSchema,
      xms: z.string().regex(memoryPattern).optional(),
      xmx: z.string().regex(memoryPattern).optional(),
    },
  },
  async ({ server, xms, xmx }) => {
    const cfg = getServer(server);
    const existing = children.get(server);
    if (existing && !existing.killed && existing.exitCode === null) {
      return text(`${server} is already MCP-managed with pid ${existing.pid}.`);
    }
    const jarPath = path.join(cfg.dir, cfg.jar);
    if (!existsSync(jarPath)) {
      throw new Error(`Missing server jar: ${jarPath}`);
    }
    await fs.mkdir(path.dirname(cfg.tailLog), { recursive: true });
    await fs.appendFile(cfg.tailLog, `\n[MCP] starting ${server} at ${new Date().toISOString()}\n`);
    const log = createWriteStream(cfg.tailLog, { flags: 'a' });
    const args = [`-Xms${xms ?? cfg.defaultXms}`, `-Xmx${xmx ?? cfg.defaultXmx}`, '-jar', cfg.jar];
    if (cfg.type === 'paper') args.push('nogui');
    const child = spawn(JAVA, args, {
      cwd: cfg.dir,
      windowsHide: true,
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    child.stdout.pipe(log, { end: false });
    child.stderr.pipe(log, { end: false });
    child.on('exit', (code, signal) => {
      log.write(`\n[MCP] ${server} exited code=${code ?? 'null'} signal=${signal ?? 'null'} at ${new Date().toISOString()}\n`);
      log.end();
      children.delete(server);
    });
    children.set(server, child);
    return text(`Started ${server} with pid ${child.pid}. Console log: ${relativeFromRoot(cfg.tailLog)}`);
  }
);

mcp.registerTool(
  'send_console_command',
  {
    title: 'Send Console Command',
    description: 'Send one command to a local server that was started by this MCP process.',
    inputSchema: {
      server: serverNameSchema,
      command: z.string().min(1).max(240),
    },
  },
  async ({ server, command }) => {
    const child = runningChild(server);
    assertConsoleCommand(command);
    child.stdin.write(`${command}\n`);
    return text(`Sent to ${server}: ${command}`);
  }
);

mcp.registerTool(
  'stop_server',
  {
    title: 'Stop Local Server',
    description: 'Gracefully stop one local server started by this MCP process.',
    inputSchema: {
      server: serverNameSchema,
    },
  },
  async ({ server }) => {
    const cfg = getServer(server);
    const child = runningChild(server);
    child.stdin.write(`${cfg.stopCommand}\n`);
    return text(`Sent ${cfg.stopCommand} to ${server}.`);
  }
);

mcp.registerTool(
  'read_console',
  {
    title: 'Read Console Log',
    description: 'Read the tail of the MCP-captured console log for a local server.',
    inputSchema: {
      server: serverNameSchema,
      lines: z.number().int().min(1).max(500).default(80),
    },
  },
  async ({ server, lines }) => {
    const cfg = getServer(server);
    return text(await tailFile(cfg.tailLog, lines));
  }
);

mcp.registerTool(
  'list_files',
  {
    title: 'List Server Files',
    description: 'List files inside one known local server folder.',
    inputSchema: {
      server: serverNameSchema,
      path: relativePathSchema,
    },
  },
  async ({ server, path: relPath }) => {
    const target = resolveInside(getServer(server).dir, relPath);
    const entries = await fs.readdir(target, { withFileTypes: true });
    const lines = entries
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((entry) => `${entry.isDirectory() ? 'dir ' : 'file'} ${entry.name}`);
    return text(lines.join('\n') || '(empty)');
  }
);

mcp.registerTool(
  'read_file',
  {
    title: 'Read Server File',
    description: 'Read a UTF-8 text file inside one known local server folder.',
    inputSchema: {
      server: serverNameSchema,
      path: z.string().min(1).max(240),
      maxBytes: z.number().int().min(1).max(200000).default(50000),
    },
  },
  async ({ server, path: relPath, maxBytes }) => {
    const target = resolveInside(getServer(server).dir, relPath);
    const data = await fs.readFile(target);
    return text(data.subarray(0, maxBytes).toString('utf8'));
  }
);

mcp.registerTool(
  'write_file',
  {
    title: 'Write Server File',
    description: 'Write a UTF-8 text file inside one known local server folder.',
    inputSchema: {
      server: serverNameSchema,
      path: z.string().min(1).max(240),
      content: z.string().max(200000),
    },
  },
  async ({ server, path: relPath, content }) => {
    const target = resolveInside(getServer(server).dir, relPath);
    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.writeFile(target, content, 'utf8');
    return text(`Wrote ${Buffer.byteLength(content, 'utf8')} bytes to ${server}/${relPath}.`);
  }
);

mcp.registerTool(
  'copy_file',
  {
    title: 'Copy Server File',
    description: 'Copy one file between known local server folders.',
    inputSchema: {
      fromServer: serverNameSchema,
      fromPath: z.string().min(1).max(240),
      toServer: serverNameSchema,
      toPath: z.string().min(1).max(240),
    },
  },
  async ({ fromServer, fromPath, toServer, toPath }) => {
    const source = resolveInside(getServer(fromServer).dir, fromPath);
    const target = resolveInside(getServer(toServer).dir, toPath);
    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.copyFile(source, target);
    return text(`Copied ${fromServer}/${fromPath} to ${toServer}/${toPath}.`);
  }
);

function findJava() {
  const localTools = path.join(ROOT, '.tools');
  if (existsSync(localTools)) {
    const candidates = readdirSync(localTools)
      .filter((name) => name.startsWith('jdk-21'))
      .map((name) => path.join(localTools, name, 'bin', process.platform === 'win32' ? 'java.exe' : 'java'));
    for (const candidate of candidates) {
      if (existsSync(candidate)) return candidate;
    }
  }
  return 'java';
}

function getServer(name) {
  const cfg = SERVERS[name];
  if (!cfg) throw new Error(`Unknown server: ${name}`);
  return cfg;
}

function runningChild(server) {
  const child = children.get(server);
  if (!child || child.killed || child.exitCode !== null) {
    throw new Error(`${server} is not running under this MCP process.`);
  }
  return child;
}

function assertConsoleCommand(command) {
  if (/[\r\n\u0000]/.test(command)) {
    throw new Error('Console command must be a single line.');
  }
}

function resolveInside(baseDir, relPath) {
  const base = path.resolve(baseDir);
  const target = path.resolve(base, relPath);
  const baseCmp = process.platform === 'win32' ? base.toLowerCase() : base;
  const targetCmp = process.platform === 'win32' ? target.toLowerCase() : target;
  if (targetCmp !== baseCmp && !targetCmp.startsWith(baseCmp + path.sep)) {
    throw new Error(`Path escapes server folder: ${relPath}`);
  }
  return target;
}

async function tailFile(file, lines) {
  if (!existsSync(file)) return '(console log does not exist yet)';
  const stat = await fs.stat(file);
  const maxBytes = 256 * 1024;
  const start = Math.max(0, stat.size - maxBytes);
  const handle = await fs.open(file, 'r');
  try {
    const buffer = Buffer.alloc(stat.size - start);
    await handle.read(buffer, 0, buffer.length, start);
    return buffer.toString('utf8').split(/\r?\n/).slice(-lines).join('\n');
  } finally {
    await handle.close();
  }
}

function toolState() {
  return Object.entries(SERVERS).map(([name, cfg]) => {
    const child = children.get(name);
    const state = child && !child.killed && child.exitCode === null ? `running pid=${child.pid}` : 'stopped';
    return `- ${name}: ${cfg.type}, ${state}, jar=${relativeFromRoot(path.join(cfg.dir, cfg.jar))}`;
  }).join('\n');
}

function relativeFromRoot(target) {
  return path.relative(ROOT, target).replaceAll(path.sep, '/');
}

function text(value) {
  return {
    content: [
      {
        type: 'text',
        text: String(value),
      },
    ],
  };
}

await mcp.connect(new StdioServerTransport());
