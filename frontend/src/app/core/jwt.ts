// Lecture (sans vérification) des claims du JWT d'accès : l'API reste seule juge des droits.
export function rolesFromToken(token: string | null): string[] {
  const payload = token?.split('.')[1];
  if (!payload) {
    return [];
  }
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const claims = JSON.parse(new TextDecoder().decode(bytes)) as { roles?: unknown };
    return Array.isArray(claims.roles) ? claims.roles.filter((r): r is string => typeof r === 'string') : [];
  } catch {
    return [];
  }
}
