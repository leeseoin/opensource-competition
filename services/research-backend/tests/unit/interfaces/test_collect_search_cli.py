"""실제 수집 CLI가 잘못된 입력을 명확한 종료 코드로 처리하는지 검증한다."""

from research_backend.interfaces.cli.collect_search import run


def test_invalid_limit_returns_failure_without_http_or_db_call(capsys) -> None:
    """범위를 벗어난 limit은 외부 연결 전에 종료 코드 1로 거부해야 한다."""

    exit_code = run(["--merchant", "abcmart", "--query", "구두", "--limit", "51"])

    assert exit_code == 1
    assert "수집·저장 실패" in capsys.readouterr().err
