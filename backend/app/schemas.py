from pydantic import BaseModel, Field, field_validator
from typing import Optional

class OrderRequest(BaseModel):
    amount: int = Field(..., description="Amount in paise")
    currency: str = "INR"
    receipt: Optional[str] = None

class OrderResponse(BaseModel):
    id: str
    entity: str
    amount: int
    currency: str
    status: str

class VerificationRequest(BaseModel):
    razorpay_order_id: str
    razorpay_payment_id: str
    razorpay_signature: str
    amount: float = Field(..., description="Amount in INR")
    donor_name: str
    user_id: str

class VerificationResponse(BaseModel):
    status: str
    message: str
    donation_id: Optional[str] = None
