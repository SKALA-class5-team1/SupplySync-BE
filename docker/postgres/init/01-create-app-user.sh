#!/bin/bash
# 최초 기동(빈 볼륨) 때만 실행된다. 관리자(postgres) 권한이 필요한 초기 설정을 모아 둔다.
#  1) 앱 전용 계정: DB와 public 스키마의 소유자가 되어 Flyway가 테이블을 만들 수 있다.
#  2) pgvector 확장: superuser만 만들 수 있어서 Flyway(앱 계정)가 아니라 여기서 만든다.
#  3) rag MCP 읽기 전용 계정(선택): doc_chunks 조회 권한은 테이블이 생긴 뒤 Flyway V1이 부여한다.
set -euo pipefail

psql -v ON_ERROR_STOP=1 \
     -v app_user="$APP_DB_USER" \
     -v app_pass="$APP_DB_PASSWORD" \
     -v db_name="$POSTGRES_DB" \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_pass';
ALTER DATABASE :"db_name" OWNER TO :"app_user";
ALTER SCHEMA public OWNER TO :"app_user";
CREATE EXTENSION IF NOT EXISTS vector;
EOSQL

if [ -n "${RAG_DB_USER:-}" ] && [ -n "${RAG_DB_PASSWORD:-}" ]; then
  psql -v ON_ERROR_STOP=1 \
       -v rag_user="$RAG_DB_USER" \
       -v rag_pass="$RAG_DB_PASSWORD" \
       --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE :"rag_user" LOGIN PASSWORD :'rag_pass';
EOSQL
  # Flyway V1은 'rag_reader' 역할이 있을 때만 권한을 부여하므로, 이름이 다르면 맞춰 준다
  if [ "$RAG_DB_USER" != "rag_reader" ]; then
    psql -v ON_ERROR_STOP=1 -v rag_user="$RAG_DB_USER" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE rag_reader NOLOGIN;
GRANT rag_reader TO :"rag_user";
EOSQL
  fi
else
  echo "RAG_DB_USER/RAG_DB_PASSWORD가 비어 있어 rag 읽기 전용 계정은 만들지 않습니다."
fi
