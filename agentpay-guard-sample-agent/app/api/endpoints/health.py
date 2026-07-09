from fastapi import APIRouter

router = APIRouter()


@router.get("", summary="서버 상태 확인")
async def health_check() -> dict:
    return {"status": "ok"}
