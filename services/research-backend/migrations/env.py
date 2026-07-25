"""Alembic이 애플리케이션 모델과 DB 연결 설정을 읽도록 구성한다."""

from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool

from research_backend.infrastructure.database import Base
from research_backend.infrastructure.database.session import get_database_url

config = context.config
config.set_main_option("sqlalchemy.url", get_database_url().replace("%", "%%"))

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata


def run_migrations_offline() -> None:
    """DB 접속 없이 SQL 스크립트를 생성한다.

    Alembic 설정의 DB URL과 모델 metadata를 사용하며 출력은 Alembic context에
    기록한다. URL이나 migration 정의가 잘못되면 Alembic 예외를 전달한다.
    """

    context.configure(
        url=config.get_main_option("sqlalchemy.url"),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        compare_type=True,
    )

    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    """실제 PostgreSQL 연결에서 migration을 transaction으로 실행한다.

    Alembic 설정을 입력으로 사용하고 적용 결과를 DB에 기록한다. 연결 실패 또는
    migration 오류가 발생하면 transaction을 중단하고 예외를 전달한다.
    """

    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )

    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata, compare_type=True)

        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
