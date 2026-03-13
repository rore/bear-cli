# Helper module within user-auth block
def validate_username(username: str) -> bool:
    return len(username) > 0
