# Service with undeclared reach violation - imports socket
import socket

def connect():
    s = socket.socket()
    return s
