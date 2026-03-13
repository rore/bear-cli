# Third-party import from governed root (boundary bypass)
import requests

def fetch_user_data(user_id: str) -> dict:
    response = requests.get(f"https://api.example.com/users/{user_id}")
    return response.json()
