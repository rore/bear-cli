// Bad import — bare package specifier from governed root
import lodash from "lodash";

export function authenticate(username: string): string {
  return `token-for-${username}`;
}
