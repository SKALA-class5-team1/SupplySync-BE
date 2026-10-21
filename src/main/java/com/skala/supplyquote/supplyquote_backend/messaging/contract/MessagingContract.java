package com.skala.supplyquote.supplyquote_backend.messaging.contract;

/**
 * RabbitMQ 메시지 규약의 이름 상수. 생산자·소비자가 같은 값을 쓰도록 한 곳에 모은다.
 * 상세 규칙(재시도, DLQ, 멱등 처리)은 docs/BACKEND.md 참고.
 */
public final class MessagingContract {

    private MessagingContract() {
    }

    /** 스키마 호환성이 깨지면 올린다. 소비자는 모르는 버전을 DLQ로 보낸다. */
    public static final int SCHEMA_VERSION = 1;

    // --- Exchange ---
    public static final String EXCHANGE = "supplysync.analysis";
    public static final String DEAD_LETTER_EXCHANGE = "supplysync.analysis.dlx";

    // --- Routing key = 메시지 type ---
    public static final String RK_ANALYSIS_REQUESTED = "analysis.requested";
    /** 요구 확인·승인·반려 후 같은 thread로 Agent를 재개한다. analysis.requested와 같은 큐로 들어간다. */
    public static final String RK_ANALYSIS_RESUME = "analysis.resume";
    public static final String RK_ANALYSIS_COMPLETED = "analysis.completed";
    public static final String RK_ANALYSIS_FAILED = "analysis.failed";
    /** PENDING 상태 문서 청크의 임베딩 생성을 요청한다. */
    public static final String RK_DOCUMENT_EMBED_REQUESTED = "document.embed.requested";

    // --- Queue ---
    /** 분석 작업 요청과 재개 요청(analysis.requested, analysis.resume). 소비자가 Agent 서버를 호출한다. */
    public static final String QUEUE_ANALYSIS_REQUEST = "analysis.request.q";
    public static final String QUEUE_ANALYSIS_REQUEST_DLQ = "analysis.request.dlq";
    /** 분석 완료·실패 이벤트. 소비자가 화면 알림(SSE)과 상태 갱신을 맡는다. */
    public static final String QUEUE_ANALYSIS_RESULT = "analysis.result.q";
    public static final String QUEUE_ANALYSIS_RESULT_DLQ = "analysis.result.dlq";
    /** 문서 청크 임베딩 요청. 소비자가 EmbeddingService를 호출한다. */
    public static final String QUEUE_DOCUMENT_EMBED = "document.embed.q";
    public static final String QUEUE_DOCUMENT_EMBED_DLQ = "document.embed.dlq";

    // --- Message header ---
    public static final String HEADER_MESSAGE_ID = "messageId";
    public static final String HEADER_REQUEST_ID = "requestId";
    public static final String HEADER_SCHEMA_VERSION = "schemaVersion";
}
