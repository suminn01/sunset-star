package com.sunsetstar.observation;

import com.sunsetstar.auth.User;
import com.sunsetstar.auth.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관측 기록을 진짜 서버와 MySQL로 확인한다 (기능명세서 F7 완료 기준).
 *
 * 계정 두 개(수민, 다른 사람)를 만들어서
 * - 수민이 사진과 함께 기록 → 목록·사진·통계에 보인다
 * - 다른 사람으로 로그인하면 수민의 기록이 목록에 없고, 사진을 달라고 하면 403
 *
 * 브라우저 두 개를 쓰는 것처럼 HttpClient(쿠키 저장소)를 계정마다 따로 만든다.
 * 끝나면 만든 기록, 사진 파일, 회원을 모두 지운다. MySQL이 켜져 있어야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ObservationFlowTest {

    private static final String PASSWORD = "sunset2026";
    /** JPG 파일 맨 앞 모양(FF D8 FF) + 아무 내용 */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4, 5, 6, 7, 8};

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObservationRepository observationRepository;
    @Autowired
    private UploadProperties uploadProperties;
    @Autowired
    private JsonMapper jsonMapper;

    private final List<String> emails = new ArrayList<>();

    @AfterEach
    void cleanUp() throws IOException {
        for (String email : emails) {
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                continue;
            }
            for (Observation observation : observationRepository.findByUserId(user.getId())) {
                if (observation.getPhotoPath() != null) {
                    Files.deleteIfExists(Path.of(uploadProperties.dir()).resolve(observation.getPhotoPath()));
                }
                observationRepository.delete(observation);
            }
            userRepository.delete(user);
        }
    }

    /** 새 계정으로 가입하고 로그인한 "브라우저" 하나 */
    private HttpClient loggedInBrowser(String nickname) throws Exception {
        String email = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        emails.add(email);
        HttpClient browser = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
        String signup = """
                {"email": "%s", "password": "%s", "nickname": "%s"}""".formatted(email, PASSWORD, nickname);
        assertThat(postJson(browser, "/api/auth/signup", signup).statusCode()).isEqualTo(201);
        String login = """
                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD);
        assertThat(postJson(browser, "/api/auth/login", login).statusCode()).isEqualTo(200);
        return browser;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> postJson(HttpClient browser, String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return browser.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 브라우저의 FormData처럼 multipart/form-data 본문을 직접 만든다.
     * 칸마다 "--경계선, 칸 이름, 빈 줄, 값" 순서로 이어 붙이고, 마지막에 "--경계선--"로 닫는다.
     */
    private HttpResponse<String> postObservation(HttpClient browser, Map<String, String> fields, byte[] photo)
            throws Exception {
        String boundary = "----sunset-star-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            body.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
                    + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        if (photo != null) {
            body.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"photo\"; filename=\"sky.jpg\"\r\n"
                    + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(photo);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(uri("/api/observations"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return browser.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, String> fields(int predictedScore, String rating) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("type", "SUNSET");
        fields.put("observedDate", LocalDate.now(ZoneId.of("Asia/Seoul")).toString());
        fields.put("latitude", "37.5665");
        fields.put("longitude", "126.978");
        fields.put("predictedScore", String.valueOf(predictedScore));
        fields.put("rating", rating);
        fields.put("memo", "구름이 분홍색으로 물들었어요");
        return fields;
    }

    @Test
    void 내_기록은_나만_볼_수_있다() throws Exception {
        HttpClient sumin = loggedInBrowser("수민");

        // 1. 수민: 사진과 함께 기록 → 201
        HttpResponse<String> created = postObservation(sumin, fields(80, "GOOD"), JPEG);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode observation = jsonMapper.readTree(created.body());
        String photoUrl = observation.get("photoUrl").asString();
        assertThat(photoUrl).isEqualTo("/api/observations/" + observation.get("id").asLong() + "/photo");

        // 2. 수민: 사진 없이 하나 더 (75점인데 별로였음 → 빗나감)
        assertThat(postObservation(sumin, fields(75, "BAD"), null).statusCode()).isEqualTo(201);

        // 3. 수민: 목록 2개, 사진이 올린 그대로 나온다, 적중률 50%
        assertThat(jsonMapper.readTree(get(sumin, "/api/observations").body()).get("totalCount").asLong())
                .isEqualTo(2);
        HttpResponse<byte[]> photo = sumin.send(HttpRequest.newBuilder(uri(photoUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(photo.statusCode()).isEqualTo(200);
        assertThat(photo.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(photo.body()).isEqualTo(JPEG);
        JsonNode stats = jsonMapper.readTree(get(sumin, "/api/observations/stats").body());
        assertThat(stats.get("hitRate").asInt()).isEqualTo(50);
        assertThat(stats.get("judgedCount").asInt()).isEqualTo(2);

        // 4. 다른 사람: 목록이 비어 있고, 수민의 사진은 403
        HttpClient other = loggedInBrowser("다른사람");
        assertThat(jsonMapper.readTree(get(other, "/api/observations").body()).get("totalCount").asLong())
                .isZero();
        HttpResponse<String> forbidden = get(other, photoUrl);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.body()).contains("FORBIDDEN");
    }

    @Test
    void 로그인하지_않으면_기록할_수_없다() throws Exception {
        HttpClient guest = HttpClient.newHttpClient();

        HttpResponse<String> response = postObservation(guest, fields(80, "GOOD"), null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void 이미지가_아닌_파일은_400이고_기록도_남지_않는다() throws Exception {
        HttpClient sumin = loggedInBrowser("수민");

        HttpResponse<String> response = postObservation(sumin, fields(80, "GOOD"),
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(jsonMapper.readTree(get(sumin, "/api/observations").body()).get("totalCount").asLong())
                .isZero();
    }
}
