import aio_pika
import logging

from app.config import Config

logger = logging.getLogger(__name__)


class RabbitService:
    def __init__(self, host: str):
        self.host = host
        self.connection = None
        self.channel = None

    async def connect(self):
        self.connection = await aio_pika.connect_robust(
            host=self.host,
            port=5672,
            login=Config.RABBITMQ_USER.strip(),
            password=Config.RABBITMQ_PASSWORD.strip(),
            virtualhost="/"  # This is the "default" vhost
        )
        self.channel = await self.connection.channel()

        await self.channel.declare_queue(Config.RESULTS_QUEUE, durable=True)
        logger.info(f"Connected to RabbitMQ at {self.host}")

    async def publish_result(self, result_json: str):
        # Get the specific exchange instead of the default one
        exchange = await self.channel.declare_exchange(
            Config.RESULTS_EXCHANGE,
            type=aio_pika.ExchangeType.TOPIC,
            durable=True
        )

        await exchange.publish(
            aio_pika.Message(
                body=result_json.encode(),
                content_type="application/json"
            ),
            routing_key=Config.RESULTS_ROUTING_KEY,
        )
        logger.info(f"Published to exchange '{Config.RESULTS_EXCHANGE}' with key '{Config.RESULTS_ROUTING_KEY}'")

    async def start_listening(self, callback):
        queue = await self.channel.declare_queue(Config.GENERATION_QUEUE, durable=True)

        async with queue.iterator() as queue_iter:
            logger.info("Service is listening for messages...")
            async for message in queue_iter:
                async with message.process():
                    # Execute the callback provided by main.py
                    await callback(message.body)