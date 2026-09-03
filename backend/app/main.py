from fastapi import FastAPI, HTTPException, Body
from .schemas import OrderRequest, OrderResponse, VerificationRequest, VerificationResponse
from .razorpay_service import razorpay_client
from .firebase_config import db
from datetime import datetime
import uuid

app = FastAPI(title="RSQ Backend")

@app.get("/health")
async def health_check():
    return {"status": "ok"}

@app.post("/donations/create_order", response_model=OrderResponse)
async def create_order(request: OrderRequest):
    # Ensure a unique receipt if not provided
    receipt = request.receipt or f"receipt_{uuid.uuid4().hex[:10]}"

    order = razorpay_client.create_order(
        amount=request.amount,
        receipt=receipt
    )

    return OrderResponse(
        id=order["id"],
        entity=order["entity"],
        amount=order["amount"],
        currency=order["currency"],
        status=order["status"]
    )

@app.post("/donations/verify_payment", response_model=VerificationResponse)
async def verify_payment(request: VerificationRequest):
    # 1. Verify signature
    is_valid = razorpay_client.verify_signature(
        order_id=request.razorpay_order_id,
        payment_id=request.razorpay_payment_id,
        signature=request.razorpay_signature
    )

    if not is_valid:
        raise HTTPException(status_code=400, detail="Invalid payment signature")

    # 2. Fetch payment details from Razorpay to verify amount
    try:
        payment = razorpay_client.client.payment.fetch(request.razorpay_payment_id)
        # Razorpay amount is in paise, convert to INR for comparison
        actual_amount = payment['amount'] / 100.0

        if abs(actual_amount - request.amount) > 0.01: # Use a small epsilon for float comparison
             raise HTTPException(status_code=400, detail="Payment amount mismatch")

    except Exception as e:
        if isinstance(e, HTTPException): raise e
        raise HTTPException(status_code=500, detail=f"Razorpay verification error: {str(e)}")

    # 3. Payment is valid, now create/update donation in Firestore
    try:
        # Idempotency check: check if this payment_id already exists
        donation_ref = db.collection("donations").document(request.razorpay_payment_id)
        doc = donation_ref.get()

        if doc.exists:
            return VerificationResponse(
                status="success",
                message="Payment already verified and recorded",
                donation_id=request.razorpay_payment_id
            )

        # Create new donation record
        # Note: In a production app, we would verify the amount against the order in Razorpay

        now = datetime.now()
        donation_data = {
            "id": request.razorpay_payment_id,
            "userId": request.user_id,
            "donorName": request.donor_name,
            "amount": request.amount,
            "date": now.strftime("%Y-%m-%d"),
            "status": "Completed",
            "orderId": request.razorpay_order_id,
            "paymentId": request.razorpay_payment_id,
            "timestamp": int(now.timestamp() * 1000)
        }

        donation_ref.set(donation_data)

        return VerificationResponse(
            status="success",
            message="Payment verified and recorded successfully",
            donation_id=request.razorpay_payment_id
        )

    except Exception as e:
        # Do not leak internal error details in production
        raise HTTPException(status_code=500, detail=f"Firestore error: {str(e)}")
