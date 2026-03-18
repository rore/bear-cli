// Bad import — escapes block root
import { config } from "../../config.js";

export function authenticate(username: string): string {
  return `token-for-${username}`;
}
