'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

export default function PrivacyPolicy() {
  const router = useRouter();
  const [searchQuery, setSearchQuery] = useState('');

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      router.push(`/player/${encodeURIComponent(searchQuery.trim())}`);
    }
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

      <main className="animate-fade-in">
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

        <section className="glass-panel" style={{ padding: '2.5rem', lineHeight: '1.6' }}>
          <h1 className="gradient-title" style={{ fontSize: '2.25rem', marginBottom: '1.5rem' }}>
            Privacy Policy
          </h1>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: '700', marginBottom: '0.5rem', color: '#fff' }}>1. Information We Collect</h2>
              <p style={{ color: 'var(--text-secondary)' }}>
                We collect information related to your Minecraft gameplay on the MaceCup Network, including your username, UUID, in-game stats (kills, deaths, wins, ratings, slams, totem pops), custom emotes, and cosmetic preferences. We do not collect personal identifyable information outside of what is exposed by public Mojang APIs.
              </p>
            </div>

            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: '700', marginBottom: '0.5rem', color: '#fff' }}>2. How We Use Information</h2>
              <p style={{ color: 'var(--text-secondary)' }}>
                The collected information is solely used to populate leaderboards, player stats dossiers, and verify cosmetic unlocks. Live statistics are queried directly from the network database.
              </p>
            </div>

            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: '700', marginBottom: '0.5rem', color: '#fff' }}>3. Public Dossiers</h2>
              <p style={{ color: 'var(--text-secondary)' }}>
                Please note that all in-game performance metrics and statistics are public and accessible to anyone via our search function. By playing on the MaceCup Network, you acknowledge and agree that your public gameplay statistics will be displayed publicly on MaceCup.xyz.
              </p>
            </div>

            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: '700', marginBottom: '0.5rem', color: '#fff' }}>4. Cookies and Web Analytics</h2>
              <p style={{ color: 'var(--text-secondary)' }}>
                We do not use tracking or advertising cookies. Basic anonymous logs may be gathered by Vercel for website performance monitoring and security logs.
              </p>
            </div>
          </div>
        </section>
      </main>

      <footer>
        <p>&copy; {new Date().getFullYear()} MaceCup.xyz. Inspired by cpvp.gg. Handcrafted and verified.</p>
        <div style={{ marginTop: '0.75rem', display: 'flex', justifyContent: 'center', gap: '1rem', fontSize: '0.85rem' }}>
          <Link href="/privacy" style={{ color: '#fff', textDecoration: 'none' }}>Privacy Policy</Link>
          <span style={{ color: 'var(--panel-border)' }}>•</span>
          <Link href="/terms" style={{ color: 'var(--text-secondary)', textDecoration: 'none', transition: 'color 0.2s' }} onMouseOver={(e) => e.target.style.color = '#fff'} onMouseOut={(e) => e.target.style.color = 'var(--text-secondary)'}>Terms of Service</Link>
        </div>
      </footer>
    </>
  );
}
