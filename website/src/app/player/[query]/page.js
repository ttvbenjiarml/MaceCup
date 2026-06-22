'use client';

import { useState, useEffect, use } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

export default function PlayerProfile({ params }) {
  const router = useRouter();
  const unwrappedParams = use(params);
  const query = unwrappedParams.query;

  const [playerData, setPlayerData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    async function fetchPlayer() {
      setLoading(true);
      setError(null);
      try {
        const res = await fetch(`/api/player/${encodeURIComponent(query)}`);
        if (res.status === 404) {
          setError('Player not found in database. Make sure they have joined the server at least once.');
        } else if (!res.ok) {
          throw new Error('Failed to retrieve player profile');
        } else {
          const data = await res.json();
          setPlayerData(data);
        }
      } catch (err) {
        console.error('Error fetching player:', err);
        setError(err.message || 'An unexpected error occurred.');
      } finally {
        setLoading(false);
      }
    }
    if (query) {
      fetchPlayer();
    }
  }, [query]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      router.push(`/player/${encodeURIComponent(searchQuery.trim())}`);
      setSearchQuery('');
    }
  };

  const getRankBadgeClass = (rating) => {
    if (rating >= 2000) return 'rank-1';
    if (rating >= 1500) return 'rank-2';
    if (rating >= 1200) return 'rank-3';
    return 'rank-other';
  };

  const getRankTitle = (rating) => {
    if (rating >= 2500) return 'Mace Master';
    if (rating >= 2000) return 'Grandmaster';
    if (rating >= 1700) return 'Diamond PvP';
    if (rating >= 1400) return 'Gold Challenger';
    if (rating >= 1100) return 'Silver Fighter';
    return 'Bronze Recruit';
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
              placeholder="Enter player name"
              className="search-input-nav"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </form>
        </div>
      </header>

      <main>
        {/* Navigation back */}
        <div style={{ marginBottom: '1.5rem' }}>
          <Link 
            href="/" 
            style={{ 
              color: 'var(--text-secondary)', 
              textDecoration: 'none', 
              fontSize: '0.95rem',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
              transition: 'color 0.2s ease'
            }}
            onMouseOver={(e) => e.target.style.color = '#fff'}
            onMouseOut={(e) => e.target.style.color = 'var(--text-secondary)'}
          >
            &larr; Back to Leaderboards
          </Link>
        </div>

        {loading ? (
          <div className="glass-panel" style={{ textAlign: 'center', padding: '5rem', color: 'var(--text-secondary)' }}>
            <div className="pulse-loader" style={{ fontSize: '1.25rem', fontWeight: '600' }}>
              Loading player dossier...
            </div>
          </div>
        ) : error ? (
          <div className="glass-panel" style={{ textAlign: 'center', padding: '4rem 2rem' }}>
            <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>⚠️</div>
            <h2 style={{ fontSize: '1.75rem', fontWeight: '800', marginBottom: '0.75rem' }}>Dossier Unavailable</h2>
            <p className="subtitle" style={{ maxWidth: '500px', margin: '0 auto 2rem' }}>
              {error}
            </p>
            <Link 
              href="/"
              className="tab-btn active"
              style={{ textDecoration: 'none', display: 'inline-block', padding: '0.75rem 1.5rem' }}
            >
              Return Home
            </Link>
          </div>
        ) : (
          <div className="profile-grid">
            {/* Sidebar with 3D skin and base metadata */}
            <div className="profile-sidebar">
              <div className="skin-render-container">
                <Image
                  src={`https://crafatar.com/renders/body/${playerData.uuid}?overlay=true`}
                  alt={`${playerData.username} 3D render`}
                  width={220}
                  height={380}
                  style={{ maxHeight: '380px', objectFit: 'contain', filter: 'drop-shadow(0 10px 20px rgba(0,0,0,0.5))' }}
                  unoptimized
                />
              </div>

              <h1 className="profile-username">{playerData.username}</h1>
              
              <div 
                className={`rank-badge ${getRankBadgeClass(playerData.stats.rating)}`} 
                style={{ 
                  width: 'auto', 
                  height: 'auto', 
                  borderRadius: '20px', 
                  padding: '0.3rem 1rem', 
                  fontSize: '0.85rem',
                  textTransform: 'uppercase',
                  letterSpacing: '0.05rem',
                  fontWeight: '800',
                  marginBottom: '1rem'
                }}
              >
                {getRankTitle(playerData.stats.rating)}
              </div>

              <div className="profile-uuid">{playerData.uuid}</div>
            </div>

            {/* Main profile contents */}
            <div>
              {/* Stats panel */}
              <div className="glass-panel" style={{ padding: '1.75rem', marginBottom: '2rem' }}>
                <h2 className="sub-section-title">
                  <span>📊</span> Player Stats
                </h2>
                
                <div className="stats-cards-grid">
                  <div className="stat-item-card">
                    <span className="stat-item-label">Match Rating</span>
                    <span className="stat-item-value" style={{ color: 'var(--accent-orange)' }}>{playerData.stats.rating}</span>
                  </div>

                  <div className="stat-item-card">
                    <span className="stat-item-label">Cash Cup Points</span>
                    <span className="stat-item-value" style={{ color: '#f1c40f' }}>{playerData.stats.cashCupPoints}</span>
                  </div>

                  <div className="stat-item-card">
                    <span className="stat-item-label">Total Wins</span>
                    <span className="stat-item-value">{playerData.stats.wins}</span>
                  </div>

                  <div className="stat-item-card">
                    <span className="stat-item-label">Solo / Duo Wins</span>
                    <span className="stat-item-value" style={{ fontSize: '1.2rem', marginTop: '0.4rem' }}>
                      {playerData.stats.soloWins} / {playerData.stats.duoWins}
                    </span>
                  </div>

                  <div className="stat-item-card">
                    <span className="stat-item-label">Kills / Deaths</span>
                    <span className="stat-item-value" style={{ fontSize: '1.2rem', marginTop: '0.4rem' }}>
                      {playerData.stats.kills} / {playerData.stats.deaths}
                    </span>
                  </div>

                  <div className="stat-item-card">
                    <span className="stat-item-label">K/D Ratio</span>
                    <span className="stat-item-value" style={{ color: 'var(--text-primary)' }}>{playerData.stats.kd.toFixed(2)}</span>
                  </div>

                  <div className="stat-item-card">
                    <span className="stat-item-label">Highest Slam</span>
                    <span className="stat-item-value">{Math.round(playerData.stats.highestSlam)}m</span>
                  </div>

                  <div className="stat-item-card">
                    <span className="stat-item-label">Totems Popped</span>
                    <span className="stat-item-value" style={{ color: '#e67e22' }}>{playerData.stats.totemPops}</span>
                  </div>

                  <div className="stat-item-card">
                    <span className="stat-item-label">Heads Collected</span>
                    <span className="stat-item-value">{playerData.stats.heads}</span>
                  </div>

                  <div className="stat-item-card">
                    <span className="stat-item-label">Event Entries</span>
                    <span className="stat-item-value" style={{ color: '#10b981' }}>{playerData.stats.eventEntries}</span>
                  </div>
                </div>
              </div>

              {/* Cosmetics panel */}
              <div className="glass-panel" style={{ padding: '1.75rem', marginBottom: '2rem' }}>
                <h2 className="sub-section-title">
                  <span>✨</span> Equipped Cosmetics
                </h2>
                
                {Object.keys(playerData.cosmetics || {}).length === 0 ? (
                  <p style={{ color: 'var(--text-secondary)', fontStyle: 'italic', padding: '0.5rem 0' }}>
                    No cosmetics currently equipped. Unlock them in the lobby cosmetic chest!
                  </p>
                ) : (
                  <div className="cosmetics-grid">
                    {Object.entries(playerData.cosmetics).map(([category, value]) => (
                      <div className="cosmetic-card" key={category}>
                        <div className="cosmetic-icon">
                          {category === 'cape' ? '🧣' : category === 'hat' ? '👒' : category === 'particle' ? '💫' : '⭐'}
                        </div>
                        <div>
                          <div className="cosmetic-category">{category}</div>
                          <div className="cosmetic-name">{value}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Emotes panel */}
              <div className="glass-panel" style={{ padding: '1.75rem' }}>
                <h2 className="sub-section-title">
                  <span>💬</span> Custom Emotes
                </h2>
                
                {Object.keys(playerData.customEmotes || {}).length === 0 ? (
                  <p style={{ color: 'var(--text-secondary)', fontStyle: 'italic', padding: '0.5rem 0' }}>
                    No custom emotes registered. Staff approval required.
                  </p>
                ) : (
                  <div className="emotes-list">
                    {Object.entries(playerData.customEmotes).map(([name, body]) => (
                      <div className="emote-bubble" key={name}>
                        <strong>{name}</strong>
                        <span className="emote-body">{`"${body}"`}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </main>

      <footer>
        <p>&copy; {new Date().getFullYear()} MaceCup.xyz. Inspired by cpvp.gg. Handcrafted and verified.</p>
        <div style={{ marginTop: '0.75rem', display: 'flex', justifyContent: 'center', gap: '1rem', fontSize: '0.85rem' }}>
          <Link href="/privacy" style={{ color: 'var(--text-secondary)', textDecoration: 'none', transition: 'color 0.2s' }} onMouseOver={(e) => e.target.style.color = '#fff'} onMouseOut={(e) => e.target.style.color = 'var(--text-secondary)'}>Privacy Policy</Link>
          <span style={{ color: 'var(--panel-border)' }}>•</span>
          <Link href="/terms" style={{ color: 'var(--text-secondary)', textDecoration: 'none', transition: 'color 0.2s' }} onMouseOver={(e) => e.target.style.color = '#fff'} onMouseOut={(e) => e.target.style.color = 'var(--text-secondary)'}>Terms of Service</Link>
        </div>
      </footer>
    </>
  );
}
