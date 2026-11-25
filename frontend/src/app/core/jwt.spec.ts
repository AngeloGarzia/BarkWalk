import { rolesFromToken } from './jwt';

function tokenWith(payload: object): string {
  // Comme le serveur : JSON encodé en UTF-8 puis base64url.
  const encode = (value: object) => {
    const bytes = new TextEncoder().encode(JSON.stringify(value));
    const binary = Array.from(bytes, (b) => String.fromCharCode(b)).join('');
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  };
  return `${encode({ alg: 'HS256' })}.${encode(payload)}.signature`;
}

describe('rolesFromToken', () => {
  it('reads the roles claim of the access token', () => {
    expect(rolesFromToken(tokenWith({ sub: '2', roles: ['USER', 'MEMBER'] }))).toEqual(['USER', 'MEMBER']);
  });

  it('decodes base64url payloads containing non-ASCII characters', () => {
    expect(rolesFromToken(tokenWith({ name: 'Zoé ÿ', roles: ['ADMIN'] }))).toEqual(['ADMIN']);
  });

  it('returns no roles for a missing or malformed token', () => {
    expect(rolesFromToken(null)).toEqual([]);
    expect(rolesFromToken('not-a-jwt')).toEqual([]);
    expect(rolesFromToken(tokenWith({ sub: '2' }))).toEqual([]);
  });
});
