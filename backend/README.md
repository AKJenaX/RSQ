# RSQ Backend

FastAPI backend for handling secure Razorpay operations.

## Setup

1. **Create Virtual Environment**:
   ```bash
   cd backend
   python -m venv .venv
   source .venv/bin/activate  # On Windows: .venv\Scripts\activate
   ```

2. **Install Dependencies**:
   ```bash
   pip install -r requirements.txt
   ```

3. **Configure Environment**:
   - Copy `.env.example` to `.env`.
   - Fill in your `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`.

4. **Run Application**:
   ```bash
   uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
   ```

## API Endpoints

- **GET /health**: Health check.
- **POST /donations/create_order**: Creates a new Razorpay Order.
- **POST /donations/verify_payment**: Verifies the HMAC-SHA256 signature from Razorpay.

## Android Emulator Consideration

When connecting from the Android Emulator, use `10.0.2.2:8000` instead of `localhost:8000` to reach your host machine.

## Payment Flow

```
Android
  ↓
POST /donations/create_order
  ↓
FastAPI
  ↓
Razorpay Orders API
  ↓
order_id
  ↓
Android Razorpay Checkout
  ↓
payment_id + order_id + signature
  ↓
POST /donations/verify_payment
  ↓
FastAPI verifies HMAC signature
  ↓
verified
  ↓
Android can update donation state in Firestore
```
