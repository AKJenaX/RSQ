from fastapi import FastAPI, HTTPException, Body
from .schemas import OrderRequest, OrderResponse, VerificationRequest, VerificationResponse
from .razorpay_service import razorpay_client
from .firebase_config import db
from firebase_admin import firestore
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

    # 3. Payment is valid, now create/update donation in Firestore using an atomic transaction
    try:
        transaction = db.transaction()
        donation_ref = db.collection("donations").document(request.razorpay_payment_id)
        balance_ref = db.collection("funds").document("global_balance")

        @firestore.transactional
        def update_in_transaction(transaction, donation_ref, balance_ref, request_data):
            # A. Idempotency check: check if this payment_id already exists
            snapshot = donation_ref.get(transaction=transaction)
            if snapshot.exists:
                return "ALREADY_EXISTS"

            # B. Create donation record
            now = datetime.now()
            donation_doc = {
                "id": request_data.razorpay_payment_id,
                "userId": request_data.user_id,
                "donorName": request_data.donor_name,
                "amount": request_data.amount,
                "date": now.strftime("%Y-%m-%d"),
                "status": "Completed",
                "orderId": request_data.razorpay_order_id,
                "paymentId": request_data.razorpay_payment_id,
                "timestamp": int(now.timestamp() * 1000)
            }
            transaction.set(donation_ref, donation_doc)

            # C. Update Global Balance
            balance_snapshot = balance_ref.get(transaction=transaction)
            if balance_snapshot.exists:
                balance_data = balance_snapshot.to_dict()
                transaction.update(balance_ref, {
                    "totalBalance": balance_data.get("totalBalance", 0.0) + request_data.amount,
                    "totalDonations": balance_data.get("totalDonations", 0) + 1,
                    "updatedAt": firestore.firestore.SERVER_TIMESTAMP
                })
            else:
                transaction.set(balance_ref, {
                    "totalBalance": request_data.amount,
                    "totalDonations": 1,
                    "updatedAt": firestore.firestore.SERVER_TIMESTAMP
                })

            return "SUCCESS"

        result = update_in_transaction(transaction, donation_ref, balance_ref, request)

        if result == "ALREADY_EXISTS":
            return VerificationResponse(
                status="success",
                message="Payment already verified and recorded",
                donation_id=request.razorpay_payment_id
            )

        return VerificationResponse(
            status="success",
            message="Payment verified and recorded successfully",
            donation_id=request.razorpay_payment_id
        )

    except Exception as e:
        # Do not leak internal error details in production
        raise HTTPException(status_code=500, detail=f"Firestore error: {str(e)}")
