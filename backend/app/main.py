from fastapi import FastAPI, HTTPException, Body
from .schemas import OrderRequest, OrderResponse, VerificationRequest, VerificationResponse
from .razorpay_service import razorpay_client
from .firebase_config import db
from firebase_admin import firestore
from datetime import datetime
import uuid
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("rsq_backend")

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
    logger.info(f"[VERIFY_PAYMENT] Stage 1: Verifying Razorpay signature for order_id={request.razorpay_order_id}, payment_id={request.razorpay_payment_id}")
    is_valid = razorpay_client.verify_signature(
        order_id=request.razorpay_order_id,
        payment_id=request.razorpay_payment_id,
        signature=request.razorpay_signature
    )

    if not is_valid:
        logger.warning(f"[VERIFY_PAYMENT] Stage 1 Failed: Invalid signature for order_id={request.razorpay_order_id}, payment_id={request.razorpay_payment_id}")
        raise HTTPException(status_code=400, detail="Invalid payment signature")
    logger.info("[VERIFY_PAYMENT] Stage 1 Success: Signature verified.")

    # 2. Fetch payment details from Razorpay to verify amount
    logger.info(f"[VERIFY_PAYMENT] Stage 2: Fetching payment details from Razorpay for payment_id={request.razorpay_payment_id}")
    try:
        payment = razorpay_client.client.payment.fetch(request.razorpay_payment_id)
        # Razorpay amount is in paise, convert to INR for comparison
        actual_amount = payment['amount'] / 100.0

        if abs(actual_amount - request.amount) > 0.01: # Use a small epsilon for float comparison
            logger.warning(f"[VERIFY_PAYMENT] Stage 2 Failed: Amount mismatch. Expected={request.amount}, Actual={actual_amount}")
            raise HTTPException(status_code=400, detail="Payment amount mismatch")
        logger.info(f"[VERIFY_PAYMENT] Stage 2 Success: Amount matched ({actual_amount} INR).")

    except Exception as e:
        if isinstance(e, HTTPException): raise e
        logger.error(f"[VERIFY_PAYMENT] Stage 2 Exception: {type(e).__name__} - {str(e)}")
        raise HTTPException(status_code=500, detail=f"Razorpay verification error: {str(e)}")

    # 3. Payment is valid, now create/update donation in Firestore using an atomic transaction
    logger.info(f"[VERIFY_PAYMENT] Stage 3: Initiating Firestore transaction for payment_id={request.razorpay_payment_id}")
    try:
        transaction = db.transaction()
        donation_ref = db.collection("donations").document(request.razorpay_payment_id)
        balance_ref = db.collection("funds").document("global_balance")

        @firestore.transactional
        def update_in_transaction(transaction, donation_ref, balance_ref, request_data):
            # 1. Perform ALL transactional reads BEFORE ANY transactional writes
            logger.info(f"[VERIFY_PAYMENT] Stage 3a: Reading donation and balance documents for payment_id={request_data.razorpay_payment_id}")
            donation_snapshot = donation_ref.get(transaction=transaction)
            balance_snapshot = balance_ref.get(transaction=transaction)
            logger.info("[VERIFY_PAYMENT] Stage 3b: Transactional reads complete")

            # 2. Idempotency check
            if donation_snapshot.exists:
                logger.info(f"[VERIFY_PAYMENT] Stage 3b: Idempotency check - payment_id={request_data.razorpay_payment_id} already exists.")
                return "ALREADY_EXISTS"

            # 3. Perform ALL transactional writes AFTER all reads are finished
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
            logger.info(f"[VERIFY_PAYMENT] Stage 3c: Writing donation document for payment_id={request_data.razorpay_payment_id}")

            if balance_snapshot.exists:
                balance_data = balance_snapshot.to_dict() or {}
                transaction.update(balance_ref, {
                    "totalBalance": balance_data.get("totalBalance", 0.0) + request_data.amount,
                    "totalDonations": balance_data.get("totalDonations", 0) + 1,
                    "updatedAt": firestore.firestore.SERVER_TIMESTAMP
                })
                logger.info("[VERIFY_PAYMENT] Stage 3d: Updating global balance document.")
            else:
                transaction.set(balance_ref, {
                    "totalBalance": request_data.amount,
                    "totalDonations": 1,
                    "updatedAt": firestore.firestore.SERVER_TIMESTAMP
                })
                logger.info("[VERIFY_PAYMENT] Stage 3d: Creating global balance document.")

            return "SUCCESS"

        result = update_in_transaction(transaction, donation_ref, balance_ref, request)
        logger.info(f"[VERIFY_PAYMENT] Stage 3e: Transaction committed. Result={result}")

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
        logger.error(f"[VERIFY_PAYMENT] Stage 3 Exception: {type(e).__name__} - {str(e)}")
        # Do not leak internal error details in production
        raise HTTPException(status_code=500, detail=f"Firestore error: {str(e)}")
