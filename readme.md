# 프로젝트 개요
## MySQL/JDBC 유틸리티 클래스 SimpleDb 구현

## 🎯 과제 목표

- 순수 **JDBC**로 경량 DB 유틸리티(**SimpleDb**)를 구현한다.
- **멀티스레드 환경**(예: Spring WebMVC)에서 안전하게 동작하는 **커넥션 관리**를 설계한다.
- **트랜잭션(Commit/Rollback)**, **SQL 빌더**, **DTO/엔티티 매핑** 등 핵심 기능을 스스로 설계/구현한다.
- 제공된 **단위 테스트(SimpleDbTest)** 전 항목 `통과(✅ t001~t019)`를 최종 목표로 한다.

# 작업내용
Article.java
- article 테이블 데이터를 담는 클래스 추가
SimpleDb.java
-트랜잭션 처리 기능: startTransaction, commit, rollback
-결과를 DTO에 쉽게 매핑
Sql.java
-SQL 문을 쉽게 만들 수 있는 append, appendIn 메서드 구현
-데이터베이스에 실행할 수 있는 메서드 제공: insert, update, delete, selectRows, selectRow 구현

이번 프로젝트로 Jdbc를 경험해 볼 수 있는 좋은 기회였고
SQL 빌드 → 파라미터 바인딩 → 실행 → 결과 반환의 흐름을 간략하게 이해 할 수 있었다.
