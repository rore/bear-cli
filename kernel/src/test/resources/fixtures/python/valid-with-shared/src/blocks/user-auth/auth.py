# Auth with shared import (allowed)
from blocks._shared.utils import format_error

def authenticate(username: str) -> str:
    if not username:
        return format_error("Username required")
    return "OK"
