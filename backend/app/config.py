import os
from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional

class Settings(BaseSettings):
    RAZORPAY_KEY_ID: str = ""
    RAZORPAY_KEY_SECRET: str = ""

    # Firebase Admin credentials (path to service account JSON)
    # If not provided, it will try to use default credentials (ADC)
    FIREBASE_SERVICE_ACCOUNT_PATH: Optional[str] = None
    FIREBASE_PROJECT_ID: str = "rsq-app"

    # Sensible defaults for donation range
    MIN_DONATION_PAISE: int = 10000  # ₹100
    MAX_DONATION_PAISE: int = 10000000  # ₹100,000

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )

settings = Settings()
