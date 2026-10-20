package com.skala.supplyquote.supplyquote_backend.messaging.contract;

import java.util.List;

/**
 * document.embed.requested: 문서 청크를 임베딩하도록 요청한다. 소비자(Spring)가 PENDING 청크를 배치로 모아
 * AI 서버 POST /embeddings(bge-m3, 1024차원)에 보내고, 받은 벡터를 저장한 뒤 embedding_status를 DONE으로 바꾼다.
 * 청크 내용은 메시지에 싣지 않고 소비자가 DB에서 읽는다.
 *
 * @param chunkIds 대상 청크. 비어 있으면 PENDING(또는 content_hash가 바뀐) 청크 전체
 */
public record DocumentEmbedPayload(List<String> chunkIds) {
}
