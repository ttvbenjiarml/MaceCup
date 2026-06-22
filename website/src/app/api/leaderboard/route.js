import { NextResponse } from 'next/server';

const ALLOWED_CATEGORIES = new Set(['wins', 'kills', 'rating', 'slam', 'cashcup']);

export async function GET(request) {
  const { searchParams } = new URL(request.url);
  const requestedCategory = (searchParams.get('category') || 'wins').toLowerCase();
  const category = ALLOWED_CATEGORIES.has(requestedCategory) ? requestedCategory : 'wins';
  
  const proxyUrl = new URL(process.env.PROXY_API_URL || 'http://127.0.0.1:24454');
  proxyUrl.pathname = '/api/leaderboard';
  proxyUrl.search = new URLSearchParams({ category }).toString();
  try {
    const res = await fetch(proxyUrl, { next: { revalidate: 30 } });
    if (!res.ok) throw new Error('Proxy responded with error');
    const data = await res.json();
    return NextResponse.json(data);
  } catch (err) {
    return NextResponse.json({ error: 'Leaderboard unavailable' }, { status: 503 });
  }
}
