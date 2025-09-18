package com.back;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
public class Article {
    private Long Id;
    private String Title;
    private String Body;
    private LocalDateTime CreatedDate;
    private LocalDateTime ModifiedDate;
    private boolean isBlind;

}
