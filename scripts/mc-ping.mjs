import net from 'node:net';
const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
function varint(n){ const out=[]; n >>>= 0; do { let b=n & 0x7f; n >>>= 7; if(n) b|=0x80; out.push(b); } while(n); return Buffer.from(out); }
function str(s){ const b=Buffer.from(s,'utf8'); return Buffer.concat([varint(b.length), b]); }
function packet(id, body=Buffer.alloc(0)){ const inner=Buffer.concat([varint(id), body]); return Buffer.concat([varint(inner.length), inner]); }
function readVarInt(buf, off=0){ let num=0, shift=0, pos=off; while(true){ const b=buf[pos++]; num |= (b & 0x7f) << shift; if((b & 0x80) === 0) return [num,pos]; shift += 7; if(shift > 35) throw new Error('varint too big'); } }
const socket = net.createConnection({host, port});
socket.setTimeout(10000);
let chunks=[];
socket.on('connect', () => {
  const handshakeBody = Buffer.concat([varint(0), str(host), Buffer.from([(port >> 8) & 255, port & 255]), varint(1)]);
  socket.write(packet(0, handshakeBody));
  socket.write(packet(0));
});
socket.on('data', d => { chunks.push(d); const buf=Buffer.concat(chunks); try { const [,p1]=readVarInt(buf,0); const [id,p2]=readVarInt(buf,p1); if(id !== 0) return; const [len,p3]=readVarInt(buf,p2); if(buf.length < p3 + len) return; const json=buf.subarray(p3,p3+len).toString('utf8'); console.log(json); socket.end(); } catch {} });
socket.on('timeout', () => { console.error('timeout'); socket.destroy(); process.exit(1); });
socket.on('error', e => { console.error(e.message); process.exit(1); });
socket.on('close', () => process.exit(0));
