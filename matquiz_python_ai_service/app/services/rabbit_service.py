import aio_pika
import logging

from app.config import Config

logger = logging.getLogger(__name__)


class NonRetryableMessageError(Exception):
    """Signals payload-level errors that should not be retried."""


class RabbitService:
    def __init__(self, host: str):
        self.host = host
        self.connection = None
        self.channel = None

    async def connect(self):
        self.connection = await aio_pika.connect_robust(
            host=self.host,
            port=Config.RABBITMQ_PORT,
            login=Config.RABBITMQ_USER.strip(),
            password=Config.RABBITMQ_PASSWORD.strip(),
            virtualhost="/"  # This is the "default" vhost
        )
        self.channel = await self.connection.channel()
        await self.channel.set_qos(prefetch_count=1)

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
                try:
                    await callback(message.body)
                    await message.ack()
                except NonRetryableMessageError as exc:
                    logger.error("Dropping non-retryable message: %s", exc)
                    await message.reject(requeue=False)
                except Exception as exc:
                    headers = message.headers or {}
                    retry_count = int(headers.get("x-retry-count", 0))

                    if retry_count >= Config.MAX_PROCESS_RETRIES:
                        logger.error(
                            "Dropping message after %s retries. Last error: %s",
                            Config.MAX_PROCESS_RETRIES,
                            exc,
                        )
                        await message.reject(requeue=False)
                        continue

                    next_retry = retry_count + 1
                    try:
                        await self._republish_for_retry(message, next_retry)
                        await message.ack()
                        logger.warning(
                            "Retrying message (%s/%s) due to error: %s",
                            next_retry,
                            Config.MAX_PROCESS_RETRIES,
                            exc,
                        )
                    except Exception as republish_exc:
                        logger.exception("Failed to republish message for retry: %s", republish_exc)
                        await message.nack(requeue=True)

    async def _republish_for_retry(self, message: aio_pika.IncomingMessage, next_retry: int):
        headers = dict(message.headers or {})
        headers["x-retry-count"] = next_retry

        retry_message = aio_pika.Message(
            body=message.body,
            content_type=message.content_type or "application/json",
            delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
            headers=headers,
        )

        await self.channel.default_exchange.publish(
            retry_message,
            routing_key=Config.GENERATION_QUEUE,
        )
