// Clean TypeScript file — imports from _shared (allowed)
import { formatDate } from "../_shared/utils.js";

export function authenticate(username: string): string {
  return `token-for-${username}-${formatDate()}`;
}
