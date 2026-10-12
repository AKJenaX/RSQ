import firebase_admin
from firebase_admin import credentials, firestore
from .config import settings
import os
import logging

logger = logging.getLogger("rsq_backend")

def initialize_firebase():
    if not firebase_admin._apps:
        path = settings.FIREBASE_SERVICE_ACCOUNT_PATH
        if path:
            path = path.strip("\"' ")

        if not path:
            err_msg = "FIREBASE_SERVICE_ACCOUNT_PATH environment variable is missing or empty."
            logger.error(f"[FIREBASE_INIT] {err_msg}")
            raise RuntimeError(err_msg)

        if not os.path.exists(path):
            err_msg = f"Firebase service account file not found at path: '{path}'"
            logger.error(f"[FIREBASE_INIT] {err_msg}")
            raise RuntimeError(err_msg)

        logger.info(f"[FIREBASE_INIT] Initializing Firebase Admin with service account at '{path}'...")
        cred = credentials.Certificate(path)
        firebase_admin.initialize_app(cred, options={
            "projectId": settings.FIREBASE_PROJECT_ID
        })
        logger.info(f"[FIREBASE_INIT] Successfully initialized Firebase Admin for project '{settings.FIREBASE_PROJECT_ID}'.")

    return firestore.client()

db = initialize_firebase()
