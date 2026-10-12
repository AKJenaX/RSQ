FROM python:3.10-slim

# Set working directory
WORKDIR /app

# Copy requirements file first to leverage Docker layer caching
COPY requirements.txt .

# Install dependencies without caching to keep the image small
RUN pip install --no-cache-dir -r requirements.txt

# Copy the application code into the container
COPY app/ ./app/

# Expose the port (Cloud Run defaults to 8080)
EXPOSE 8080

# Cloud Run dynamically provides the PORT environment variable.
# We use sh -c to ensure the environment variable is properly evaluated.
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8080}"]
