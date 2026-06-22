'use client';

import { useState, useEffect } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

export default function Home() {
  const router = useRouter();
  const [searchQuery, setSearchQuery] = useState('');
  const [activeTab, setActiveTab] = useState('wins');
  const [leaderboard, setLeaderboard] = useState([]);
  const [leaderboardLoading, setLeaderboardLoading] = useState(true);
  // Fetch leaderboard data when activeTab changes
  useEffect(() => {
    async function fetchLeaderboard() {
      setLeaderboardLoading(true);
      try {
        const res = await fetch(`/api/leaderboard?category=${activeTab}`);
        if (res.ok) {
          const data = await res.json();
          setLeaderboard(data);
        } else {
          setLeaderboard([]);
        }
      } catch (err) {
        console.error('Failed to fetch leaderboard', err);
        setLeaderboard([]);
      } finally {
        setLeaderboardLoading(false);
      }
    }
    fetchLeaderboard();
  }, [activeTab]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      router.push(`/player/${encodeURIComponent(searchQuery.trim())}`);
    }
  };

  const getCategoryLabel = (cat) => {
    return cat.charAt(0).toUpperCase() + cat.slice(1);
  };

  const formatValue = (val, cat) => {
    if (cat === 'slam') {
      return `${Math.round(val)}m`;
    }
    return val.toLocaleString();
  };

  return (
    <>
      <header>
        <div className="nav-container">
          <Link href="/" className="brand-link">
            MaceCup.xyz
          </Link>
          
          <form onSubmit={handleSearchSubmit}>
            <input
              type="text"
              placeholder="Search player username or UUID..."
              className="search-input-nav"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </form>
        </div>
      </header>

      <main>
        {/* Hero Welcome */}
        <section className="glass-panel" style={{ padding: '3rem 2.5rem', textAlign: 'center', position: 'relative', overflow: 'hidden' }}>
          <h1 className="gradient-title" style={{ fontSize: '3.5rem', lineHeight: '1.1' }}>
            MaceCup.xyz
          </h1>
          <p className="subtitle" style={{ fontSize: '1.25rem', maxWidth: '600px', margin: '0 auto 2rem' }}>
            The premier competitive Minecraft Crystal PvP network. Track live leaderboards, stats, custom emotes, and active player ratings.
          </p>
          
          <form onSubmit={handleSearchSubmit} style={{ maxWidth: '500px', margin: '0 auto', display: 'flex', gap: '0.75rem' }}>
            <input
              type="text"
              placeholder="Enter player name (e.g. Benji)..."
              className="search-input-nav"
              style={{ width: '100%', padding: '0.8rem 1.5rem', fontSize: '1rem', borderRadius: '12px' }}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
            <button
              type="submit"
              style={{
                background: 'var(--gradient-accent)',
                border: 'none',
                color: '#fff',
                fontWeight: '700',
                padding: '0.8rem 1.8rem',
                borderRadius: '12px',
                cursor: 'pointer',
                fontFamily: 'inherit',
                fontSize: '1rem'
              }}
            >
              Search
            </button>
          </form>
        </section>

        {/* Leaderboard Card */}
        <section className="glass-panel">
          <h2 style={{ fontSize: '1.75rem', fontWeight: '800', marginBottom: '0.2rem' }}>Leaderboards</h2>
          <p className="subtitle" style={{ marginBottom: '1.5rem' }}>Top 50 active competitive players on the MaceCup Network</p>
          
          <div className="tabs-container">
            {['wins', 'kills'].map((cat) => (
              <button
                key={cat}
                className={`tab-btn ${activeTab === cat ? 'active' : ''}`}
                onClick={() => setActiveTab(cat)}
              >
                {getCategoryLabel(cat)}
              </button>
            ))}
          </div>

          {leaderboardLoading ? (
            <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-secondary)' }}>
              Loading leaderboard rankings...
            </div>
          ) : leaderboard.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-secondary)' }}>
              No player stats found in the database. Join the server to register!
            </div>
          ) : (
            <div className="table-wrapper">
              <table className="leaderboard-table">
                <thead>
                  <tr>
                    <th style={{ width: '80px' }}>Rank</th>
                    <th>Player</th>
                    <th style={{ textAlign: 'center' }}>Rating</th>
                    <th style={{ textAlign: 'center' }}>Wins (Solo/Duo)</th>
                    <th style={{ textAlign: 'center' }}>Kills / Deaths</th>
                    <th style={{ textAlign: 'center' }}>K/D</th>
                    <th style={{ textAlign: 'right' }}>{getCategoryLabel(activeTab)}</th>
                  </tr>
                </thead>
                <tbody>
                  {leaderboard.map((player) => (
                    <tr key={player.uuid}>
                      <td>
                        <span className={`rank-badge rank-${player.rank <= 3 ? player.rank : 'other'}`}>
                          {player.rank}
                        </span>
                      </td>
                      <td>
                        <div className="player-info">
                          <Image
                            src={`https://crafatar.com/avatars/${player.uuid}?size=32&overlay`}
                            alt={player.username}
                            width={32}
                            height={32}
                            className="player-avatar"
                            unoptimized
                          />
                          <Link href={`/player/${player.username}`} className="player-name-link">
                            {player.username}
                          </Link>
                        </div>
                      </td>
                      <td style={{ textAlign: 'center', fontWeight: '600', color: 'var(--accent-orange)' }}>
                        {player.rating}
                      </td>
                      <td style={{ textAlign: 'center', color: 'var(--text-secondary)' }}>
                        {player.wins} <span style={{ fontSize: '0.8rem' }}>({player.soloWins}/{player.duoWins})</span>
                      </td>
                      <td style={{ textAlign: 'center', color: 'var(--text-secondary)' }}>
                        {player.kills} / {player.deaths}
                      </td>
                      <td style={{ textAlign: 'center', color: 'var(--text-primary)', fontWeight: '500' }}>
                        {player.kd.toFixed(2)}
                      </td>
                      <td style={{ textAlign: 'right' }} className="bold-value">
                        {formatValue(player.value, activeTab)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </main>

      <footer>
        <p>&copy; {new Date().getFullYear()} MaceCup Network. Inspired by cpvp.gg. Handcrafted and verified.</p>
      </footer>
    </>
  );
}
