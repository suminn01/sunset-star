package com.sunsetstar.observation;

import com.sunsetstar.global.ApiException;
import com.sunsetstar.global.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 관측 기록 사진을 서버 폴더(app.upload.dir)에 저장하고 꺼낸다 (기능명세서 F7).
 *
 * DB에는 사진 자체가 아니라 파일 이름만 넣는다. 사진은 크고, DB는 글자·숫자를 다루는 데 알맞아서다.
 *
 * 저장 규칙
 * - 파일 이름은 올린 사람이 정한 이름을 쓰지 않고 무작위(UUID)로 만든다.
 *   "../../비밀.txt" 같은 이름으로 다른 폴더에 파일을 쓰는 공격을 막고, 이름이 겹치지 않게 하려는 것이다
 * - 형식은 파일 앞부분(매직 넘버)을 직접 읽어서 판단한다. 확장자나 브라우저가 알려 준 형식은 바꿔치기할 수 있어서다
 * - 형식·크기가 틀리면 400. 형식은 맞는데 디스크에 쓰다 실패하면 null을 돌려주고 기록은 그대로 저장한다
 */
@Component
public class PhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(PhotoStorage.class);

    /** 우리가 만든 파일 이름 모양: UUID.확장자. 이 모양이 아니면 꺼내 주지 않는다 */
    private static final Pattern STORED_NAME =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");

    static final String TYPE_INVALID = "사진은 JPG, PNG, WEBP만 올릴 수 있어요";
    static final String TOO_LARGE = "사진은 10MB 이하로 올려 주세요";

    private final Path directory;

    public PhotoStorage(UploadProperties properties) {
        this.directory = Path.of(properties.dir()).toAbsolutePath().normalize();
    }

    /** 저장한 사진 정보: 파일 이름과 형식(image/jpeg 등) */
    public record StoredPhoto(Path path, String contentType) {
    }

    /**
     * 사진을 저장하고 파일 이름을 돌려준다.
     *
     * @return 저장한 파일 이름. 사진이 없으면 null, 디스크에 쓰다 실패해도 null
     * @throws ApiException 형식이나 크기가 틀리면 400 INVALID_PARAMETER
     */
    public String save(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        if (photo.getSize() > ObservationRules.PHOTO_MAX_BYTES) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER, TOO_LARGE);
        }
        ImageType type = detectType(photo);

        String fileName = UUID.randomUUID() + "." + type.extension;
        try {
            Files.createDirectories(directory);
            photo.transferTo(directory.resolve(fileName));
            return fileName;
        } catch (IOException e) {
            log.warn("사진 저장 실패, 기록만 저장합니다: {}", e.getMessage());
            return null;
        }
    }

    /** 저장해 둔 사진을 찾는다. 파일이 없거나 이름이 우리가 만든 모양이 아니면 빈 값 */
    public Optional<StoredPhoto> find(String fileName) {
        if (fileName == null || !STORED_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        Path path = directory.resolve(fileName);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
        return Arrays.stream(ImageType.values())
                .filter(type -> type.extension.equals(extension))
                .findFirst()
                .map(type -> new StoredPhoto(path, type.contentType));
    }

    /** 파일 앞 12바이트를 읽어 진짜 이미지 형식을 알아낸다 */
    private ImageType detectType(MultipartFile photo) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = photo.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER, TYPE_INVALID);
        }
        return Arrays.stream(ImageType.values())
                .filter(type -> type.matches(head, read))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PARAMETER, TYPE_INVALID));
    }

    /**
     * 받는 이미지 형식과 파일 맨 앞의 표시(매직 넘버).
     * JPG는 FF D8 FF, PNG는 89 'P' 'N' 'G', WEBP는 'RIFF' + 4바이트 + 'WEBP'로 시작한다.
     */
    private enum ImageType {
        JPEG("jpg", "image/jpeg") {
            @Override
            boolean matches(byte[] h, int n) {
                return n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            }
        },
        PNG("png", "image/png") {
            @Override
            boolean matches(byte[] h, int n) {
                return n >= 4 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            }
        },
        WEBP("webp", "image/webp") {
            @Override
            boolean matches(byte[] h, int n) {
                return n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                        && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            }
        };

        final String extension;
        final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        abstract boolean matches(byte[] head, int read);
    }
}
