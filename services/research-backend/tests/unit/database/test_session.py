"""DB 연결 설정과 세션 생성 규칙을 검증한다."""

from sqlalchemy.orm import Session

from research_backend.infrastructure.database.session import (
    create_database_engine,
    create_session_factory,
    get_database_url,
)


def test_database_url_uses_environment(monkeypatch) -> None:
    """DB 환경변수가 있으면 로컬 기본값보다 우선해야 한다."""

    expected = "postgresql+psycopg://user:password@example:5432/example"
    monkeypatch.setenv("PURCHASE_RESEARCH_DATABASE_URL", expected)

    assert get_database_url() == expected


def test_session_factory_uses_given_engine() -> None:
    """전달한 Engine으로 commit 후 객체를 유지하는 세션을 만들어야 한다."""

    engine = create_database_engine("sqlite+pysqlite:///:memory:")
    session_factory = create_session_factory(engine)

    with session_factory() as session:
        assert isinstance(session, Session)
        assert session.bind is engine
        assert session.expire_on_commit is False

    engine.dispose()
