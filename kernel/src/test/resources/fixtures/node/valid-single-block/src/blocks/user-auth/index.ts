// Clean TypeScript file — no boundary violations
export function authenticate(username: string): string {
  return `token-for-${username}`;
}
