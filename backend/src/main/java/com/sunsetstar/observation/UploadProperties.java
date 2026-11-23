package com.sunsetstar.observation;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yml의 app.upload.* 설정을 읽는다.
 *
 * @param dir 사진을 저장할 폴더. 예: uploads (서버를 켠 backend 폴더 안에 생긴다)
 */
@Validated
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(@NotBlank String dir) {
}
