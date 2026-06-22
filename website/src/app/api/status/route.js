import { NextResponse } from 'next/server';

export async function GET() {
  const proxyUrl = new URL(process.env.PROXY_API_URL || 'http://127.0.0.1:24454');
  proxyUrl.pathname = '/api/status';
  try {
    const res = await fetch(proxyUrl, { next: { revalidate: 15 } });
    if (!res.ok) throw new Error('Proxy responded with error');
    const data = await res.json();
    return NextResponse.json(data);
  } catch (err) {
    return NextResponse.json({ onlinePlayers: 0, servers: {}, error: 'Status unavailable' }, { status: 503 });
  }
}
