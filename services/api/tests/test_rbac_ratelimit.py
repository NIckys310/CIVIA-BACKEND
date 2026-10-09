import itertools

from civia_api.models import Role
from civia_api.security.ratelimit import MemoryRateLimiter
from civia_api.security.rbac import ROLE_PERMISSIONS, Permission, has_permission


def test_every_role_has_permissions_defined() -> None:
    assert set(ROLE_PERMISSIONS) == set(Role)


def test_only_engineer_or_admin_can_approve_reports() -> None:
    allowed = {r for r in Role if has_permission(r, Permission.REPORT_APPROVE)}
    assert allowed == {Role.ADMIN, Role.ENGINEER_IN_CHARGE}


def test_viewer_is_read_only() -> None:
    assert ROLE_PERMISSIONS[Role.VIEWER] == {Permission.PROJECT_READ}


def test_permissions_are_monotonic_by_role() -> None:
    order = [Role.VIEWER, Role.COLLABORATOR, Role.REVIEWER, Role.ENGINEER_IN_CHARGE, Role.ADMIN]
    for lower, higher in itertools.pairwise(order):
        assert ROLE_PERMISSIONS[lower] <= ROLE_PERMISSIONS[higher]


async def test_memory_rate_limiter_blocks_after_limit() -> None:
    rl = MemoryRateLimiter()
    results = [await rl.hit("k", limit=3, window_seconds=60) for _ in range(4)]
    assert results == [True, True, True, False]
    await rl.reset("k")
    assert await rl.hit("k", limit=3, window_seconds=60)
