## Spring AI 스터디 프로젝트 주제 
배달 서비스 CS(고객센터) 도메인에 특화된 Spring Boot 기반 RAG + 실시간 채팅 상담 시스템
기존의 프로젝트를 RAG기반 CS 챗봇으로 직접 구현 

---

## MVP
- pgvector 기반 cs_faq 검색 (Dense-only similarity search)
- 정책 질문 (RAG) / 실시간 조회 질문 (Tool Calling) 분리 처리 (진행 중)
- llm-as-judge 평가 체계 - 생성된 답변의 품질을 llm이 검증하고 기준에 미달한 질문은 별도로 저장해 추적 
  
2차 고도화
- Reranker 도입 및 RAG 속도 최적화
- Kafka 기반 메시지 팬아웃 (수정 예정)

---
## 기술 특색
- RAG와 Tool Calling 경계 분리 : 정책/절차성 질문은 RAG, 관련 데이터 조회 부분은 Tool Calling으로 처리 
- 관련/무관 질문 유사도 분포 실측으로 임계값을 0.5로 확정
- Query Rewriting(사용자 질문을 llm을 통해 재작성하여 검색 정확도를 높이려 함)은 실제 테스트 결과 속도 문제로 제외 (추후 재도입 검토)
- 스케쥴러가 아닌 PostgreSQL Listen/Notify 기반 임베딩 파이프라인 : 서버에 주기적인 부하를 주는 폴링 방식이 대신, 이벤트 기반 방식을 통해 트리거 발생 시에만 임베딩 시작
- virtual thread 기반 동시성 처리 - 동시 요청 20개 기준 약 8544ms -> 4845(약 43% 단축) 확인 (추후 동시 요청을 늘려서 테스트 예정) 

---

## 시스템 아키텍쳐 (추후 변경 예정)
```
클라이언트 (HTTP POST 질문 전송)
        │
        ▼
메시지 핸들러 (요청 검증, DB 저장)
        │
        ▼
   정책 질문인가?
   ├─ Yes → RAG 코어 (검색 → 생성, cs_faq)
   └─ No  → Tool Calling (주문 · 배달상태 · 회원 조회)
        │
        ▼
   LLM 답변 생성 (RAG 결과 + Tool 결과 종합)
        │
        ▼
   SSE 스트리밍 (SseEmitter로 토큰 단위 전달)
```
