from httpx import ASGITransport, AsyncClient

from civia_api.main import create_app


async def test_health_returns_ok() -> None:
    async with AsyncClient(transport=ASGITransport(app=create_app()), base_url="http://t") as c:
        res = await c.get("/api/v1/health")
    assert res.status_code == 200
    assert res.json()["status"] == "ok"
