import json
import logging
import asyncio
from app.config import Config
from app.utils.logger import setup_logger
from app.services.ai_service import AiService
from app.services.rabbit_service import RabbitService, NonRetryableMessageError

setup_logger()
logger = logging.getLogger(__name__)


def _require_non_blank_text(value, field_name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise NonRetryableMessageError(f"Invalid payload: '{field_name}' is required.")
    return value.strip()


async def main():
    try:
        Config.validate()

        ai_service = AiService(Config.GEMINI_API_KEY)
        rabbit_service = RabbitService(Config.RABBITMQ_HOST)

        await rabbit_service.connect()

        async def process_request(body: bytes):
            try:
                data = json.loads(body.decode())
            except json.JSONDecodeError as exc:
                raise NonRetryableMessageError("Invalid payload: expected JSON body.") from exc

            room_code = _require_non_blank_text(data.get("roomCode"), "roomCode").upper()
            topic = _require_non_blank_text(data.get("topic"), "topic")
            difficulty = _require_non_blank_text(data.get("difficulty"), "difficulty").upper()

            logger.info("Processing request for room: %s", room_code)

            quiz_json = ai_service.generate_quiz(topic, difficulty, room_code)

            # Return Result
            await rabbit_service.publish_result(quiz_json)

        # Start the worker loop
        await rabbit_service.start_listening(process_request)

    except Exception as e:
        logger.critical(f"Service failed to start: {e}")


if __name__ == "__main__":
    asyncio.run(main())
