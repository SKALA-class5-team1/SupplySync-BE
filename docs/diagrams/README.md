# 다이어그램

원본은 Mermaid(`*.mmd`)이고, 문서에 넣기 위한 PNG를 같이 둔다. 설명과 읽는 순서는 [../DESIGN.md](../DESIGN.md) 참고.

| 파일 | 내용 |
| --- | --- |
| 01-system | 시스템 구성도 |
| 02-deployment | 배포 구성 (docker network, 포트, 볼륨) |
| 03-layers | MVC 레이어 구조 |
| 04-domain-classes | 도메인(Model) 클래스 |
| 05-service-classes | Controller · Service · Infrastructure 클래스 |
| 06-erd | ERD (데이터셋 v2.1 + 백엔드 테이블) |
| 07-task-state | 분석 작업(ai_tasks) 상태 머신 |
| 08-quote-state | 견적(quotes) 상태 머신 |
| 09-seq-intake | RFQ 접수 → 분석 시작 시퀀스 |
| 10-seq-agent-run | Agent 실행 중 (MCP, 요구 확인, 진행 이벤트) 시퀀스 |
| 11-seq-approval | 승인 → 재검증 → 등록 시퀀스 |
| 12-langgraph-overview | LangGraph 전체 흐름 (요약) |
| 13-langgraph-detail | LangGraph 전체 흐름 (노드 단위) |
| 14-rabbitmq | RabbitMQ 토폴로지 |
| 15-seq-embedding | 임베딩 적재 파이프라인 시퀀스 |

## PNG 다시 만들기

`.mmd`를 고친 뒤 PNG를 갱신한다. Mermaid CLI는 브라우저(Chrome)가 필요하다.

```bash
# Chrome이 설치되어 있으면 경로를 지정한다 (macOS 예시)
echo '{"executablePath":"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome","args":["--no-sandbox"]}' > /tmp/pp.json

cd docs/diagrams
for f in *.mmd; do
  npx -y -p @mermaid-js/mermaid-cli mmdc -q -p /tmp/pp.json -c mermaid.config.json \
      -i "$f" -o "${f%.mmd}.png" -s 2 -b white
done
```

GitHub에서는 `.mmd` 내용을 ` ```mermaid ` 코드 블록으로 붙여 넣으면 PNG 없이도 렌더링된다.
