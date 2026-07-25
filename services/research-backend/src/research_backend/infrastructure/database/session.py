"""환경변수 기반 SQLAlchemy Engine과 세션 생성을 제공한다."""

import os

from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import Session, sessionmaker

DEFAULT_DATABASE_URL = (
    "postgresql+psycopg://purchase_research:purchase_research@127.0.0.1:35432/purchase_research"
)


def get_database_url() -> str:
    """환경변수의 DB URL을 반환하고, 없으면 로컬 개발 기본값을 반환한다.

    입력은 없고 SQLAlchemy가 이해할 수 있는 URL 문자열을 출력한다. 잘못된 URL은
    Engine 생성 또는 최초 연결 시 SQLAlchemy 예외로 보고된다.
    """

    return os.getenv("PURCHASE_RESEARCH_DATABASE_URL", DEFAULT_DATABASE_URL)


def create_database_engine(database_url: str | None = None) -> Engine:
    """DB URL로 연결 확인 기능이 포함된 SQLAlchemy Engine을 생성한다.

    URL을 생략하면 환경변수 또는 로컬 기본값을 사용한다. URL 형식이나 DB 연결에
    문제가 있으면 SQLAlchemy 또는 psycopg 예외를 호출자에게 그대로 전달한다.
    """

    return create_engine(database_url or get_database_url(), pool_pre_ping=True)


def create_session_factory(engine: Engine) -> sessionmaker[Session]:
    """주어진 Engine을 사용하는 transaction 세션 팩토리를 생성한다.

    SQLAlchemy Engine을 입력받아 Session 생성기를 출력한다. 유효하지 않은 Engine은
    SQLAlchemy가 세션 생성 또는 사용 시 오류로 보고한다.
    """

    return sessionmaker(bind=engine, expire_on_commit=False)
