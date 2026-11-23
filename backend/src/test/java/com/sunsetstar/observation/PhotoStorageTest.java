package com.sunsetstar.observation;

import com.sunsetstar.global.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 사진 저장 규칙 테스트. @TempDir: 테스트마다 빈 임시 폴더를 만들어 주고 끝나면 지운다.
 * MockMultipartFile: 브라우저가 올린 파일인 척하는 가짜 파일
 */
class PhotoStorageTest {

    /** JPG 파일 맨 앞 모양(FF D8 FF) + 아무 내용 */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @TempDir
    Path tempDir;

    private PhotoStorage storage;

    @BeforeEach
    void setUp() {
        storage = new PhotoStorage(new UploadProperties(tempDir.toString()));
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("photo", name, contentType, content);
    }

    @Test
    void JPG를_무작위_이름으로_저장하고_다시_찾을_수_있다() throws Exception {
        String fileName = storage.save(file("노을.jpg", "image/jpeg", JPEG));

        assertThat(fileName).endsWith(".jpg").doesNotContain("노을");
        assertThat(Files.readAllBytes(tempDir.resolve(fileName))).isEqualTo(JPEG);
        assertThat(storage.find(fileName)).hasValueSatisfying(photo ->
                assertThat(photo.contentType()).isEqualTo("image/jpeg"));
    }

    @Test
    void 확장자가_아니라_파일_내용으로_형식을_정한다() {
        // 이름은 .jpg지만 내용은 PNG
        String fileName = storage.save(file("photo.jpg", "image/jpeg", PNG));

        assertThat(fileName).endsWith(".png");
    }

    @Test
    void 이미지가_아니면_400() {
        // 이름과 형식은 이미지인 척하지만 내용은 글자
        MockMultipartFile fake = file("photo.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes());

        assertThatThrownBy(() -> storage.save(fake))
                .isInstanceOf(ApiException.class)
                .hasMessage(PhotoStorage.TYPE_INVALID);
    }

    @Test
    void 사진_10MB를_넘으면_400() {
        byte[] big = new byte[(int) ObservationRules.PHOTO_MAX_BYTES + 1];
        System.arraycopy(JPEG, 0, big, 0, JPEG.length);

        assertThatThrownBy(() -> storage.save(file("big.jpg", "image/jpeg", big)))
                .isInstanceOf(ApiException.class)
                .hasMessage(PhotoStorage.TOO_LARGE);
    }

    @Test
    void 사진이_없으면_null() {
        assertThat(storage.save(null)).isNull();
        assertThat(storage.save(file("empty.jpg", "image/jpeg", new byte[0]))).isNull();
    }

    @Test
    void 우리가_만든_이름이_아니면_꺼내_주지_않는다() {
        assertThat(storage.find("../application-secret.yml")).isEmpty();
        assertThat(storage.find("hello.jpg")).isEmpty();
    }
}
