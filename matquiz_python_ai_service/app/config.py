import os
from dotenv import load_dotenv

load_dotenv()

class Config:
    GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
    RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
    RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))

    RABBITMQ_USER = os.getenv("RABBITMQ_USER")
    RABBITMQ_PASSWORD = os.getenv("RABBITMQ_PASSWORD")
    MAX_PROCESS_RETRIES = int(os.getenv("MAX_PROCESS_RETRIES", "3"))

    GENERATION_QUEUE = "quiz_generation_queue"
    GENERATION_EXCHANGE = "quiz_generation_exchange"
    GENERATION_ROUTING_KEY = "quiz_generation_key"

    RESULTS_QUEUE = "quiz_results_queue"
    RESULTS_EXCHANGE = "quiz_results_exchange"
    RESULTS_ROUTING_KEY = "quiz_results_key"

    @classmethod
    def validate(cls):
        if not cls.GEMINI_API_KEY:
            raise ValueError("GEMINI_API_KEY is not set in environment variables.")
        if not cls.RABBITMQ_USER or not cls.RABBITMQ_USER.strip():
            raise ValueError("RABBITMQ_USER is not set in environment variables.")
        if not cls.RABBITMQ_PASSWORD or not cls.RABBITMQ_PASSWORD.strip():
            raise ValueError("RABBITMQ_PASSWORD is not set in environment variables.")
        if cls.RABBITMQ_PORT <= 0:
            raise ValueError("RABBITMQ_PORT must be a positive number.")
        if cls.MAX_PROCESS_RETRIES < 0:
            raise ValueError("MAX_PROCESS_RETRIES must be zero or greater.")
