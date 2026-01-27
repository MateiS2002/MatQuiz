from google import genai
from google.genai import types
from app.models.schemas import QuizResultSchema
import logging


logger = logging.getLogger(__name__)

class AiService:
    def __init__(self, api_key: str):
        self.client = genai.Client(api_key=api_key)
        self.model_id = "gemini-2.5-flash"

    def generate_quiz(self, topic: str, difficulty: str, room_code: str) -> str:
        prompt = (
            f"Generate a quiz with 5 questions about {topic}. "
            f"The difficulty should be {difficulty}. "
            "Ensure the questions are challenging but fair and also they should feel fun for an online quiz game not academic."
        )



        logger.info(f"Calling Gemini API for room {room_code} with topic {topic}and difficulty {difficulty}")

        response = self.client.models.generate_content(
            model = self.model_id,
            contents = prompt,
            config = types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=QuizResultSchema,
            )
        )

        result = QuizResultSchema.model_validate_json(response.text)
        result.roomCode = room_code

        return result.model_dump_json()