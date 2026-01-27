import os
from dotenv import load_dotenv

load_dotenv()

class Config:
    GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
    RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
    RABBITMQ_PORT = os.getenv("RABBITMQ_PORT", 5672)

    RABBITMQ_USER = os.getenv("RABBITMQ_USER")
    RABBITMQ_PASSWORD = os.getenv("RABBITMQ_PASSWORD")

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