import { describe, expect, it } from 'vitest';
import { decodeJwtPayload } from './jwt.util';

describe('decodeJwtPayload', () => {
  // Décode un payload JWT base64url valide.
  it('lit les claims d’un jeton bien formé', () => {
    const payload = btoa(JSON.stringify({ sub: '42', roles: ['USER'] }))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
    const token = `hdr.${payload}.sig`;
    expect(decodeJwtPayload(token)).toEqual({ sub: '42', roles: ['USER'] });
  });

  // Ignore un jeton incomplet.
  it('retourne null si le jeton est tronqué', () => {
    expect(decodeJwtPayload('only-one-part')).toBeNull();
  });

  // Ignore un payload non JSON.
  it('retourne null si le payload est illisible', () => {
    expect(decodeJwtPayload('a.%%% .c'.replace(' ', ''))).toBeNull();
  });
});
