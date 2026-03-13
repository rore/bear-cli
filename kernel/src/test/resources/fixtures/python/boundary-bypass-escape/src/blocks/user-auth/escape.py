# Escaping block root via relative import (boundary bypass)
from ...nongoverned import helper  # Escapes to src/nongoverned

def use_helper() -> str:
    return helper.do_something()
