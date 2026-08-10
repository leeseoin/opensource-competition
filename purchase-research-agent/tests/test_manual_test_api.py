"""Python Swagger 단계 테스트 API의 문서와 Backend 연결을 검증한다."""

from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from app.main import app
from app.services.backend_store_service import BackendStoreError


class ManualTestApiTests(unittest.TestCase):
    """외부 요청 없이 단계별 Swagger API의 입력, 출력과 실패 안내를 확인한다."""

    def setUp(self) -> None:
        """각 테스트에 lifespan과 HTTP route를 포함한 FastAPI client를 준비한다."""

        self.client_context = TestClient(app)
        self.client = self.client_context.__enter__()

    def tearDown(self) -> None:
        """테스트 client와 application lifespan을 종료한다."""

        self.client_context.__exit__(None, None, None)

    def test_openapi_exposes_numbered_manual_steps(self) -> None:
        """Swagger 문서에 00부터 03까지 수동 검증 경로와 소량 기본값이 노출되는지 검증한다."""

        document = self.client.get("/openapi.json").json()

        paths = document["paths"]
        self.assertIn("/api/v1/manual-test/00-readiness", paths)
        self.assertIn("/api/v1/manual-test/01-collection-tasks", paths)
        self.assertIn("/api/v1/manual-test/02-collection-jobs/{job_id}", paths)
        self.assertIn("/api/v1/manual-test/03-products", paths)
        schema = document["components"]["schemas"]["ManualCollectionTaskRequest"]
        self.assertEqual(schema["properties"]["limit"]["default"], 3)
        self.assertEqual(schema["properties"]["query"]["default"], "구두")

    @patch("app.api.endpoints.manual_test.BackendStoreService.health", new_callable=AsyncMock)
    def test_readiness_explains_disabled_worker(self, health: AsyncMock) -> None:
        """일반 API 실행에서는 Backend와 Worker 상태를 구분해 안내하는지 검증한다."""

        health.return_value = {"status": "UP"}

        response = self.client.get("/api/v1/manual-test/00-readiness")

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertFalse(body["ready"])
        self.assertTrue(body["productBackend"]["ready"])
        self.assertFalse(body["pythonQueueWorker"]["enabled"])

    @patch(
        "app.api.endpoints.manual_test.BackendStoreService.create_collection_task",
        new_callable=AsyncMock,
    )
    def test_creates_small_collection_task_with_defaults(self, create: AsyncMock) -> None:
        """1단계 기본 입력이 camelCase Backend 계약과 다음 단계 안내로 변환되는지 검증한다."""

        create.return_value = {"jobId": "job-001", "taskId": "task-001", "status": "QUEUED"}

        response = self.client.post("/api/v1/manual-test/01-collection-tasks", json={})

        self.assertEqual(response.status_code, 202)
        request = create.await_args.args[0]
        self.assertEqual(request["merchant"], "abcmart")
        self.assertEqual(request["limit"], 3)
        self.assertEqual(request["maxAttempts"], 2)
        self.assertNotIn("priceMax", request["filters"])
        self.assertEqual(request["filters"]["sizes"], [])
        self.assertIn("jobId", response.json()["nextStep"])

    @patch(
        "app.api.endpoints.manual_test.BackendStoreService.get_collection_job",
        new_callable=AsyncMock,
    )
    def test_completed_job_points_to_product_step(self, get_job: AsyncMock) -> None:
        """완료된 2단계 job 응답이 저장 상품 조회를 다음 단계로 안내하는지 검증한다."""

        get_job.return_value = {"jobId": "job-001", "status": "SUCCESS", "productCount": 3}

        response = self.client.get("/api/v1/manual-test/02-collection-jobs/job-001")

        self.assertEqual(response.status_code, 200)
        self.assertIn("3단계", response.json()["nextStep"])

    @patch(
        "app.api.endpoints.manual_test.BackendStoreService.search_products",
        new_callable=AsyncMock,
    )
    def test_queries_stored_products(self, search: AsyncMock) -> None:
        """3단계 검색 조건이 Product Backend 상품 조회 인자로 전달되는지 검증한다."""

        search.return_value = {"totalCount": 1, "hasNext": False, "products": [{"id": 1}]}

        response = self.client.get(
            "/api/v1/manual-test/03-products",
            params={"merchant": "29cm", "query": "운동화", "limit": 5},
        )

        self.assertEqual(response.status_code, 200)
        search.assert_awaited_once_with(merchant="29cm", query="운동화", limit=5)
        self.assertEqual(response.json()["totalCount"], 1)

    @patch(
        "app.api.endpoints.manual_test.BackendStoreService.create_collection_task",
        new_callable=AsyncMock,
    )
    def test_backend_failure_has_readiness_guidance(self, create: AsyncMock) -> None:
        """Backend 실패가 비밀값 없이 준비 단계 확인 안내를 반환하는지 검증한다."""

        create.side_effect = BackendStoreError("Product Backend 연결 실패")

        response = self.client.post("/api/v1/manual-test/01-collection-tasks", json={})

        self.assertEqual(response.status_code, 502)
        self.assertIn("0단계", response.json()["detail"]["check"])


if __name__ == "__main__":
    unittest.main()
