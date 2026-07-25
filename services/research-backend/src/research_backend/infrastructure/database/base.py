"""모든 SQLAlchemy 모델이 공유하는 선언 기반을 정의한다."""

from sqlalchemy import MetaData
from sqlalchemy.orm import DeclarativeBase


NAMING_CONVENTION = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}


class Base(DeclarativeBase):
    """DB 모델의 공통 metadata와 제약조건 이름 규칙을 제공한다.

    입력과 출력은 없으며, 하위 모델 선언이 잘못되면 SQLAlchemy가 애플리케이션
    시작 또는 migration 생성 시점에 오류를 발생시킨다.
    """

    metadata = MetaData(naming_convention=NAMING_CONVENTION)
