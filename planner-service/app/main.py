"""Planner service entrypoint.

Run locally:
    uvicorn app.main:app --reload --port 8001
"""

from __future__ import annotations

import logging
import os

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from .api.planner import router as planner_router

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Life Planner - Planning Service",
    description=(
        "Deterministic, constraint-aware scheduling. Internal service: it is "
        "called by the Spring Boot backend and owns no persistent data."
    ),
    version="0.1.0",
)

app.include_router(planner_router)


@app.exception_handler(Exception)
async def unhandled_error(request: Request, exc: Exception) -> JSONResponse:
    """Never leak a stack trace to a caller (spec section 61)."""
    logger.exception("Unhandled error on %s", request.url.path)
    return JSONResponse(
        status_code=500,
        content={
            "error": "PLANNER_ERROR",
            "message": "The planning service could not complete this request.",
        },
    )


@app.get("/health")
def health() -> dict:
    """Liveness probe. Spring Boot uses this to decide whether the
    intelligent layer is reachable, so it can degrade honestly rather than
    pretending a plan was generated."""
    return {"status": "ok", "service": "planner", "version": app.version}
