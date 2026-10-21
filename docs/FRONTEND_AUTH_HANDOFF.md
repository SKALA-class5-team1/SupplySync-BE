# 프론트엔드 인증 연동 안내

## 로그인과 토큰

`POST /api/auth/login`은 인증 없이 호출한다.

```json
{"login_id":"hb_buyer01","password":"demo1234"}
```

성공 시 HTTP 200과 아래 형식의 응답을 받는다. 모든 JSON 필드 이름은 `snake_case`이다.

```json
{
  "access_token": "<JWT>",
  "token_type": "Bearer",
  "expires_in": 3600,
  "user": {
    "user_id": 1,
    "login_id": "hb_buyer01",
    "name": "박구매",
    "user_type": "CUSTOMER",
    "company_id": 1,
    "company_name": "한별모터스"
  }
}
```

이후 `/api/**` 요청에 `Authorization: Bearer <access_token>`을 붙인다. `GET /api/me`로 현재 계정과 역할을 다시 확인할 수 있다. 만료 시간 기본값은 3600초이며 서버의 `JWT_ACCESS_TOKEN_SECONDS` 설정에 따라 바뀔 수 있으므로 응답의 `expires_in`을 사용한다. 현재 리프레시 토큰·로그아웃 API는 없다. 만료되면 다시 로그인한다. 토큰을 URL 파라미터에 넣지 않는다.

## 접근 권한

| 기능 | CUSTOMER | MANAGER |
| --- | --- | --- |
| `GET /api/rfqs`, `GET /api/rfqs/{id}`, `GET /api/rfqs/{id}/quotes` | 소속 회사 RFQ만 | 전체 RFQ |
| `GET /api/rfqs?mine=true` | 본인이 올린 RFQ만 | 본인이 올린 RFQ만 |
| `POST /api/rfqs` | 가능 | 403 |
| `GET /api/rfqs/{id}/analysis` | 403 | 가능 |
| `GET /api/ai/tasks/{id}`, `/stream` | 소속 회사 작업만 | 전체 작업 |
| `POST /api/ai/tasks/{id}/confirmation`, `/approval` | 403 | 가능 |

RFQ 업로드 요청은 `multipart/form-data`로 `file`(.md)과 `rfq_number`를 보낸다. 서버는 로그인한 고객의 `user_id`와 `company_id`로 저장한다. 확인·승인 이력에는 로그인한 담당자의 `user_id`가 기록된다. 다른 회사 RFQ와 작업은 존재 여부를 숨기기 위해 404로 응답한다.

담당자 분석 화면의 `GET /api/rfqs/{id}/analysis`에는 `review.final_plan`(부품 ID별 확정 수량), `quotes[].issue_date`, `quotes[].details`가 포함된다. 담당자용 `details`에는 `recipient`, `incoterms`, `price_breakdown`, **내부 원가 정보인** `parts_cost_breakdown`이 있다. 고객의 `GET /api/rfqs/{id}/quotes`에는 등록된 견적의 `issue_date`와 공개 가능한 `details`만 포함되며 `parts_cost_breakdown`은 응답에 없다.

## SSE

`GET /api/ai/tasks/{task_id}/stream`도 `Authorization` 헤더가 필요하다. 브라우저의 기본 `EventSource`는 임의의 인증 헤더를 설정할 수 없으므로 `fetch`의 스트림 읽기 기능이나 헤더를 지원하는 SSE 클라이언트를 사용한다. 응답 `Content-Type`은 `text/event-stream`이다. 연결은 최대 5분 또는 토큰 만료 시 닫힌다. 재연결할 때 `GET /api/ai/tasks/{task_id}`로 최신 상태를 먼저 확인한다.

```ts
const response = await fetch(`${apiBase}/api/ai/tasks/${taskId}/stream`, {
  headers: { Authorization: `Bearer ${accessToken}` },
  signal: abortController.signal,
});
if (!response.ok) throw new Error(`SSE HTTP ${response.status}`);
// response.body의 SSE 프레임(event:, data:)을 파싱해 처리한다.
```

고객 스트림에는 `STEP_START`, `STEP_END`, `DONE`, `FAILED`의 단계·시간만 전달한다. 담당자는 추가 이벤트와 메시지를 받는다. 고객의 작업 상태 응답에서는 내부 `failure_reason`, `trace_url`이 `null`이다. `current_step`은 Agent가 `/internal/tasks/{id}/events`로 보낸 `step`을 Spring이 저장한 값이다.

담당자가 `CLARIFICATION_REQUIRED` 또는 `APPROVAL_REQUIRED` 이벤트를 받으면 `GET /api/rfqs/{id}/analysis`를 다시 호출해 이슈·견적 상세를 가져온다. SSE 본문에는 `issues`, `quote_id` 전체가 실리지 않는다.

## 오류와 개발 설정

오류 응답은 `{code, message, details, timestamp}` 형식이다. 인증 정보가 없거나 토큰이 잘못됐거나 만료되면 401 `UNAUTHORIZED`, 역할이 맞지 않으면 403 `FORBIDDEN`, 다른 회사 자료는 404 `NOT_FOUND`를 처리한다. 로그인 실패도 401이다.

프론트 주소가 Spring과 다른 origin이면 백엔드 `.env`의 `FRONTEND_ORIGINS`에 정확한 origin을 쉼표로 구분해 넣는다(예: `http://localhost:5173`). `INTERNAL_API_KEY`는 Agent·MCP가 `/internal/**`을 호출할 때 사용하는 별도 키이며 브라우저에 전달하지 않는다.
