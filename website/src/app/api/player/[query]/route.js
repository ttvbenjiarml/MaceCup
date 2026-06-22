import { NextResponse } from 'next/server';

export async function GET(request, { params }) {
  const query = String(params.query || '').trim();
  if (!/^[A-Za-z0-9_]{2,16}$|^[0-9a-fA-F-]{36}$/.test(query)) {
    return NextResponse.json({ error: 'Invalid player query' }, { status: 400 });
  }

  const proxyUrl = new URL(process.env.PROXY_API_URL || 'http://127.0.0.1:24454');
  proxyUrl.pathname = `/api/player/${encodeURIComponent(query)}`;
  try {
    const res = await fetch(proxyUrl, { next: { revalidate: 10 } });
    if (!res.ok) {
      if (res.status === 404) {
        return NextResponse.json({ error: 'Player not found' }, { status: 404 });
      }
      throw new Error('Proxy responded with error');
    }
    const data = await res.json();
    return NextResponse.json(data);
  } catch (err) {
    return NextResponse.json({ error: 'Player data unavailable' }, { status: 503 });
  }
}
