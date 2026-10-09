"""Rate limiting de ventana fija. Redis en entornos reales; memoria en desarrollo/tests."""

import time
from functools import lru_cache
from typing import Protocol

from redis.asyncio import Redis

from civia_api.config import get_settings


class RateLimiter(Protocol):
    async def hit(self, key: str, *, limit: int, window_seconds: int) -> bool:
        """Registra un intento. Devuelve False si se superó el límite en la ventana."""

    async def reset(self, key: str) -> None: ...


class MemoryRateLimiter:
    """Solo para un proceso (desarrollo y tests). No usar con varias réplicas."""

    def __init__(self) -> None:
        self._buckets: dict[str, tuple[float, int]] = {}

    async def hit(self, key: str, *, limit: int, window_seconds: int) -> bool:
        now = time.monotonic()
        started, count = self._buckets.get(key, (now, 0))
        if now - started >= window_seconds:
            started, count = now, 0
        count += 1
        self._buckets[key] = (started, count)
        return count <= limit

    async def reset(self, key: str) -> None:
        self._buckets.pop(key, None)


class RedisRateLimiter:
    def __init__(self, redis: Redis) -> None:
        self._redis = redis

    async def hit(self, key: str, *, limit: int, window_seconds: int) -> bool:
        redis_key = f"rl:{key}"
        async with self._redis.pipeline(transaction=True) as pipe:
            pipe.incr(redis_key)
            pipe.expire(redis_key, window_seconds, nx=True)
            count, _ = await pipe.execute()
        return int(count) <= limit

    async def reset(self, key: str) -> None:
        await self._redis.delete(f"rl:{key}")


@lru_cache
def get_rate_limiter() -> RateLimiter:
    url = get_settings().redis_url
    if url:
        return RedisRateLimiter(Redis.from_url(url))
    return MemoryRateLimiter()
