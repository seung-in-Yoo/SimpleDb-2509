package com.back;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
// Article 엔티티 (DB의 article 테이블과 매핑)
public class Article {
    private Long id;                // PK
    private LocalDateTime createdDate;  // 생성일
    private LocalDateTime modifiedDate; // 수정일
    private String title;           // 제목
    private String body;            // 본문
    private boolean isBlind;        // 숨김 여부
}
