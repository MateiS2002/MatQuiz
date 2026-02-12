from google import genai
from google.genai import types
from app.models.schemas import QuizResultSchema
import logging


logger = logging.getLogger(__name__)

class AiService:
    def __init__(self, api_key: str):
        self.client = genai.Client(api_key=api_key)
        self.model_id = "gemini-3-flash-preview"

    def generate_quiz(self, topic: str, difficulty: str, room_code: str) -> str:
        topic = topic.lower().strip()
        difficulty = difficulty.lower().strip()

        prompt = (
            f"You are a charismatic game-show host creating an online quiz. Topic: '{topic}'. Difficulty: '{difficulty}'. "
            "If the topic is unsafe, offensive, or unclear gibberish, replace it with one safe fun topic from: movies, world food, animals, sports, space, inventions. "
            "Create exactly 5 multiple-choice questions with 4 options each and one correct answer. "
            "Make them playful and witty (PG humor), challenging but fair, and suitable for a fast online game. "
            "Avoid obscure trivia, trick wording, or ambiguous answers. "
            "Use varied question styles (scenario, clue-based, elimination, comparison, pattern). "
            "Make distractors plausible, similar in length, and never use 'All of the above' or 'None of the above'. "
            "Progress difficulty naturally from question 1 to question 5."
        )

        logger.info(f"Calling Gemini API for room {room_code} with topic {topic} and difficulty {difficulty}")

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
