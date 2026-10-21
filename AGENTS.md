# SupplySync-BE 작업 기준

- 이 저장소는 Java 21, Spring Boot, Gradle 프로젝트다. `개인폴더/image1.png`~`image3.png`는 Python 데이터 프로젝트의 예시이므로 개념만 참고하고 명령어와 디렉터리 구조를 그대로 복사하지 않는다.
- 확정 입력 데이터는 `개인폴더/Input/rfq_dataset_v2.2` 하나다. 원본 파일은 수정하지 않는다. 변환이 필요하면 결과를 별도 위치에 두고 출처와 변환 방법을 문서화한다.
- 기능이나 실행 방법이 바뀌면 README에 프로젝트 목적, 환경 변수, 실행·테스트 방법을 최신 상태로 유지한다.
- Java 코드 변경은 관련 Gradle/JUnit 테스트로 검증한다. CI와 테스트 배지는 실제 GitHub Actions 워크플로가 있을 때만 연결한다.
- 노트북 작업이 실제로 생길 때만 노트북/HTML 공유 절차를 추가한다. 현재 백엔드에 Python 예시의 `data-project/`, `notebooks/`, `src/clean.py` 구조를 도입하지 않는다.
- 변경 이력은 Git 커밋과 필요한 설계 문서에 기록한다. 다른 예시의 작성자·소유권·저작권 문구를 프로젝트 소스에 복사하지 않는다.
- 새 문서의 RAG 적재와 벡터 DB 관리는 후속 단계다. 현재 우선순위는 기존 업무 API와 Agent 실행·RabbitMQ 통합 검증이다.
