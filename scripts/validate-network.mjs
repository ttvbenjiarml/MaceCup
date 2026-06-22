import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const required = ['README_SETUP.md','TROUBLESHOOTING.md','shared/resource-pack/MaceCupResourcePack.zip','shared/datapack/MaceCupDatapack.zip','shared/mysql/schema.sql','shared/redis/redis-example.conf','velocity-na/velocity.toml','velocity-eu/velocity.toml','lobby-practice/server.properties','event-1/server.properties','source/MaceCupCore/build.gradle','source/MaceCupProxyBridge/build.gradle'];
let ok = true;
for (const rel of required) { const exists = fs.existsSync(path.join(root, rel)); console.log((exists ? 'OK   ' : 'MISS ') + rel); if (!exists) ok = false; }
const pack = fs.readFileSync(path.join(root, 'shared/resource-pack/MaceCupResourcePack.zip'));
const sha1 = crypto.createHash('sha1').update(pack).digest('hex');
console.log('Resource pack SHA1:', sha1);
if (sha1 !== fs.readFileSync(path.join(root, 'shared/resource-pack/SHA1.txt'), 'utf8').trim()) ok = false;
JSON.parse(fs.readFileSync(path.join(root, 'source/MaceCupResourcePack/pack.mcmeta'), 'utf8'));
JSON.parse(fs.readFileSync(path.join(root, 'source/MaceCupDatapack/pack.mcmeta'), 'utf8'));
const advancementsDir = path.join(root, 'source/MaceCupDatapack/data/macecup/advancement');
if (fs.existsSync(advancementsDir)) {
  for (const f of fs.readdirSync(advancementsDir)) JSON.parse(fs.readFileSync(path.join(advancementsDir, f), 'utf8'));
}
process.exit(ok ? 0 : 1);
