from fastapi import APIRouter

from civia_api.api.v1 import account, activity, auth, me, projects

router = APIRouter(prefix="/api/v1")
router.include_router(auth.router)
router.include_router(account.router)
router.include_router(me.router)
router.include_router(projects.router)
router.include_router(activity.router)
