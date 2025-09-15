package com.back;

import lombok.Data;
import java.time.LocalDateTime;


@Data
public class Article {
    private Long id;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
    private String title;
    private String body;
    private boolean blind;
}