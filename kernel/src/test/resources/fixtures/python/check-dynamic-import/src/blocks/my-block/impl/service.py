# Service with importlib.import_module - boundary bypass violation
import importlib

mod = importlib.import_module("socket")
