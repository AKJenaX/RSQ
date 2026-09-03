import razorpay
from .config import settings
from fastapi import HTTPException

class RazorpayClient:
    def __init__(self):
        self.client = razorpay.Client(auth=(settings.RAZORPAY_KEY_ID, settings.RAZORPAY_KEY_SECRET))

    def create_order(self, amount: int, receipt: str):
        if amount < settings.MIN_DONATION_PAISE or amount > settings.MAX_DONATION_PAISE:
            raise HTTPException(
                status_code=400,
                detail=f"Amount must be between ₹{settings.MIN_DONATION_PAISE/100} and ₹{settings.MAX_DONATION_PAISE/100}"
            )

        data = {
            "amount": amount,
            "currency": "INR",
            "receipt": receipt,
            "notes": {
                "project": "RSQ Relief Fund"
            }
        }
        try:
            order = self.client.order.create(data=data)
            return order
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))

    def verify_signature(self, order_id: str, payment_id: str, signature: str):
        params_dict = {
            'razorpay_order_id': order_id,
            'razorpay_payment_id': payment_id,
            'razorpay_signature': signature
        }
        try:
            # The SDK method utility_verify_payment_signature raises error if invalid
            self.client.utility.verify_payment_signature(params_dict)
            return True
        except Exception:
            return False

razorpay_client = RazorpayClient()
