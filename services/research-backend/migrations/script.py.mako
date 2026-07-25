"""${message}

Revision ID: ${up_revision}
Revises: ${down_revision | comma,n}
Create Date: ${create_date}
"""

from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa
${imports if imports else ""}

revision: str = ${repr(up_revision)}
down_revision: str | Sequence[str] | None = ${repr(down_revision)}
branch_labels: str | Sequence[str] | None = ${repr(branch_labels)}
depends_on: str | Sequence[str] | None = ${repr(depends_on)}


def upgrade() -> None:
    """새 DB 구조를 적용하고 실패 시 Alembic transaction을 중단한다."""

    ${upgrades if upgrades else "pass"}


def downgrade() -> None:
    """이 revision의 DB 구조를 되돌리고 실패 시 예외를 전달한다."""

    ${downgrades if downgrades else "pass"}
