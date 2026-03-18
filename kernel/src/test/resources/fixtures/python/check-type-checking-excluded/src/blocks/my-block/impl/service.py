# Service with TYPE_CHECKING import - should NOT produce findings
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    import socket

def get_type_hint():
    # This is just for type hints, not runtime
    pass
