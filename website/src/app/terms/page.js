'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

export default function TermsOfService() {
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
            Terms of Service
          </h1>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: '700', marginBottom: '0.5rem', color: '#fff' }}>1. Acceptance of Terms</h2>
              <p style={{ color: 'var(--text-secondary)' }}>
                By connecting to the MaceCup Minecraft Network (e.g., lobby-practice, event-1) or using the MaceCup.xyz website, you agree to be bound by these Terms of Service, all applicable laws, and network regulations.
              </p>
            </div>

            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: '700', marginBottom: '0.5rem', color: '#fff' }}>2. Gameplay Rules & Fair Play</h2>
              <p style={{ color: 'var(--text-secondary)' }}>
                You agree not to utilize modified clients, hacked clients (including reach, velocity modifiers, clickers, or combat assists), macros, or exploits to gain an unfair advantage in crystal PvP. Infractions will result in an immediate hardware ban and erasure of your rankings from the public leaderboards.
              </p>
            </div>

            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: '700', marginBottom: '0.5rem', color: '#fff' }}>3. Emote and Chat Policies</h2>
              <p style={{ color: 'var(--text-secondary)' }}>
                Custom emotes registered on our network must not contain offensive, discriminatory, or highly toxic language. Staff reserves the right to remove custom emotes or mute/ban offending accounts.
              </p>
            </div>

            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: '700', marginBottom: '0.5rem', color: '#fff' }}>4. Disclaimer of Liability</h2>
              <p style={{ color: 'var(--text-secondary)' }}>
                The MaceCup Network and MaceCup.xyz are provided "as is". We are not affiliated with Mojang Studios or Microsoft, and we accept no liability for server downtime or data corruption.
              </p>
            </div>
          </div>
        </section>
      </main>

      <footer>
        <p>&copy; {new Date().getFullYear()} MaceCup.xyz. Inspired by cpvp.gg. Handcrafted and verified.</p>
        <div style={{ marginTop: '0.75rem', display: 'flex', justifyContent: 'center', gap: '1rem', fontSize: '0.85rem' }}>
          <Link href="/privacy" style={{ color: 'var(--text-secondary)', textDecoration: 'none', transition: 'color 0.2s' }} onMouseOver={(e) => e.target.style.color = '#fff'} onMouseOut={(e) => e.target.style.color = 'var(--text-secondary)'}>Privacy Policy</Link>
          <span style={{ color: 'var(--panel-border)' }}>•</span>
          <Link href="/terms" style={{ color: '#fff', textDecoration: 'none' }}>Terms of Service</Link>
        </div>
      </footer>
    </>
  );
}
