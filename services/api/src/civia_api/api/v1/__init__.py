from fastapi import APIRouter

from civia_api.api.v1 import auth, me, projects

router = APIRouter(prefix="/api/v1")
router.include_router(auth.router)
router.include_router(me.router)
router.include_router(projects.router)
