# Service with sys.path mutation - boundary bypass violation
import sys

sys.path.append("/tmp")
