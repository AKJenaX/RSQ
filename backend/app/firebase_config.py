import firebase_admin
from firebase_admin import credentials, firestore
from .config import settings
import os

def initialize_firebase():
    if not firebase_admin._apps:
        if settings.FIREBASE_SERVICE_ACCOUNT_PATH and os.path.exists(settings.FIREBASE_SERVICE_ACCOUNT_PATH):
            cred = credentials.Certificate(settings.FIREBASE_SERVICE_ACCOUNT_PATH)
            firebase_admin.initialize_app(cred)
        else:
            # Fallback to application default credentials or project ID
            firebase_admin.initialize_app(options={
                'projectId': settings.FIREBASE_PROJECT_ID,
            })
    return firestore.client()

db = initialize_firebase()
