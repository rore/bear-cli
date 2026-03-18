// Bad import — reaches sibling block
import { charge } from "../payment/index.js";

export function authenticate(username: string): string {
  return `token-for-${username}`;
}
