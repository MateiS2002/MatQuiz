import json
import logging
import asyncio
from app.config import Config
from app.utils.logger import setup_logger
from app.services.ai_service import AiService
from app.services.rabbit_service import RabbitService

setup_logger()
logger = logging.getLogger(__name__)


async def main():
    try:
        Config.validate()

        ai_service = AiService(Config.GEMINI_API_KEY)
        rabbit_service = RabbitService(Config.RABBITMQ_HOST)

        await rabbit_service.connect()

        async def process_request(body: bytes):
            try:
                data = json.loads(body.decode())
                room_code = data.get("roomCode")
                topic = data.get("topic")
                difficulty = data.get("difficulty")

                logger.info(f"Processing request for room: {room_code}")

                quiz_json = ai_service.generate_quiz(topic, difficulty, room_code)

                # Return Result
                await rabbit_service.publish_result(quiz_json)

            except Exception as ee:
                logger.error(f"Failed to process message: {ee}")

        # Start the worker loop
        await rabbit_service.start_listening(process_request)

    except Exception as e:
        logger.critical(f"Service failed to start: {e}")


if __name__ == "__main__":
    asyncio.run(main())