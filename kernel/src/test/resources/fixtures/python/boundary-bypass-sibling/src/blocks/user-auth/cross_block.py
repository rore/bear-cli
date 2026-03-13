# Importing from sibling block (boundary bypass)
from blocks.payment.processor import process_payment

def authenticate_with_payment(amount: float) -> bool:
    return process_payment(amount)
