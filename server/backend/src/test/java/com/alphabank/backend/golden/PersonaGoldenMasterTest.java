package com.alphabank.backend.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.alphabank.backend.dto.response.FieldDiffDto;
import com.alphabank.backend.security.DemoUserAuthFilter;
import com.alphabank.backend.stub.StubIds;
import com.alphabank.backend.util.JsonDiff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PersonaGoldenMasterTest {

    private static final Path ROOT = repositoryRoot();
    private static final Path GOLDEN = ROOT.resolve("server/backend/src/test/resources/golden/persona-paths.json");
    private static final String UPDATE = "golden.update";
    private static final String API = "/api/v1/applications";
    private static final int ML_PORT = 8001;
    private static final int MOCKS_PORT = 8002;
    private static final String UNDERWRITER = StubIds.UNDERWRITER_ID.toString();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    static final GenericContainer<?> MOCKS = new GenericContainer<>(
            new ImageFromDockerfile("alphabank-golden-mocks", false).withFileFromPath(".", ROOT.resolve("mocks")))
            .withExposedPorts(MOCKS_PORT)
            .waitingFor(Wait.forHttp("/health").forPort(MOCKS_PORT));

    @Container
    static final GenericContainer<?> ML = new GenericContainer<>(
            new ImageFromDockerfile("alphabank-golden-ml", false).withFileFromPath(".", ROOT.resolve("ml")))
            .withEnv("LLM_REPLAY_PATH", "/app/eval/replay/llm_answers.jsonl")
            .withExposedPorts(ML_PORT)
            .waitingFor(Wait.forHttp("/health").forPort(ML_PORT));

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.ml-url", () -> "http://" + ML.getHost() + ":" + ML.getMappedPort(ML_PORT));
        registry.add("app.mocks-url", () -> "http://" + MOCKS.getHost() + ":" + MOCKS.getMappedPort(MOCKS_PORT));
    }

    @Test
    void personaPathsMatchGoldenMasterTwice() throws Exception {
        JsonNode first = runAllPaths();
        JsonNode second = runAllPaths();

        assertThat(diff(first, second)).as("второй прогон отличается от первого").isEmpty();
        if (Boolean.getBoolean(UPDATE) || !Files.exists(GOLDEN)) {
            Files.writeString(GOLDEN, objectMapper.writeValueAsString(first) + "\n", StandardCharsets.UTF_8);
        }
        JsonNode golden = objectMapper.readTree(Files.readString(GOLDEN, StandardCharsets.UTF_8));
        assertThat(diff(golden, first))
                .as("пути персонажей разошлись с эталоном %s; если правила менялись осознанно — "
                        + "обновите эталон: mvn test -Dtest=PersonaGoldenMasterTest -D%s=true", GOLDEN, UPDATE)
                .isEmpty();
    }

    private JsonNode runAllPaths() throws Exception {
        resetMocks();
        ObjectNode paths = objectMapper.createObjectNode();
        paths.set("coffee", coffee());
        paths.set("builder", builder());
        paths.set("seller", seller());
        paths.set("taxDebtUnpaid", taxDebt(false));
        paths.set("taxDebtPaid", taxDebt(true));
        paths.set("shell", shell());
        return paths;
    }

    private JsonNode coffee() throws Exception {
        PersonaPath path = start(StubIds.COFFEE_USER_ID.toString(), "EQUIPMENT", 200_000_000L, 36, "витрина");
        path.close(factor("debt_load"), "{\"method\":\"CONNECT_SOURCE\",\"sourceCode\":\"OFD\",\"consent\":true}");
        path.close(openOfType("ANOMALY"),
                "{\"method\":\"ANSWER\",\"optionCode\":\"RENOVATION\",\"text\":\"Был ремонт, точка закрывалась на 2 недели\"}");
        path.close(factor("purpose"), "{\"method\":\"ANSWER\",\"text\":\"2 кофемашины и витрина\"}");
        return path.submit();
    }

    private JsonNode builder() throws Exception {
        PersonaPath path = start(StubIds.BUILDER_USER_ID.toString(), "WORKING_CAPITAL", 300_000_000L, 12,
                "закупка материалов под договор подряда");
        path.close(factor("purpose"), "{\"method\":\"ANSWER\",\"text\":\"закупка материалов под договор подряда\"}");
        return path.submit();
    }

    private JsonNode seller() throws Exception {
        PersonaPath path = start(StubIds.SELLER_USER_ID.toString(), "WORKING_CAPITAL", 150_000_000L, 12,
                "закупка товара к сезону");
        path.close(factor("debt_load"),
                "{\"method\":\"CONNECT_SOURCE\",\"sourceCode\":\"MARKETPLACE\",\"consent\":true}");
        path.close(factor("purpose"), "{\"method\":\"ANSWER\",\"text\":\"закупка товара к сезону\"}");
        return path.submit();
    }

    private JsonNode taxDebt(boolean pay) throws Exception {
        PersonaPath path = start(StubIds.TAX_DEBT_USER_ID.toString(), "WORKING_CAPITAL", 80_000_000L, 12, "запчасти");
        path.close(factor("debt_load"),
                "{\"method\":\"CONNECT_SOURCE\",\"sourceCode\":\"ACQUIRING\",\"consent\":true}");
        path.close(factor("purpose"), "{\"method\":\"ANSWER\",\"text\":\"закупка запчастей на склад\"}");
        if (pay) {
            path.close(openOfType("STOP_FACTOR"), "{\"method\":\"PAY\"}");
        }
        return path.submit();
    }

    private JsonNode shell() throws Exception {
        return start(StubIds.SHELL_USER_ID.toString(), "WORKING_CAPITAL", 200_000_000L, 12, "оборотка").submit();
    }

    private PersonaPath start(String user, String product, long amount, int termMonths, String goal) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("productCode", product, "amount", amount,
                "termMonths", termMonths, "goal", goal, "consents", Map.of("bki", true, "personalData", true)));
        JsonNode created = call(post(API).contentType(MediaType.APPLICATION_JSON).content(body), user);
        PersonaPath path = new PersonaPath(user, created.at("/application/id").asText());
        path.record("evaluate", call(post(API + "/" + path.id + "/evaluate"), user));
        return path;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String user) throws Exception {
        String body = mvc.perform(request.header(DemoUserAuthFilter.HEADER, user))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private static Predicate<JsonNode> factor(String scoringFactor) {
        return spot -> scoringFactor.equals(spot.path("scoringFactor").asText());
    }

    private static Predicate<JsonNode> openOfType(String type) {
        return spot -> type.equals(spot.path("type").asText()) && "OPEN".equals(spot.path("status").asText());
    }

    private final class PersonaPath {

        private final String user;
        private final String id;
        private final ArrayNode steps = objectMapper.createArrayNode();
        private JsonNode details;

        private PersonaPath(String user, String id) {
            this.user = user;
            this.id = id;
        }

        private void record(String step, JsonNode response) {
            assertThat(response.has("application")).as("шаг %s: %s", step, response).isTrue();
            details = response;
            ObjectNode node = objectMapper.createObjectNode();
            node.put("step", step);
            node.put("status", response.at("/application/status").asText());
            node.set("fork", fork(response.get("fork")));
            ArrayNode spots = node.putArray("spots");
            response.get("spots").forEach(spot -> spots.add(spot(spot)));
            steps.add(node);
        }

        private void close(Predicate<JsonNode> which, String body) throws Exception {
            JsonNode spot = find(details.get("spots"), which);
            record("close " + spot.get("title").asText(), call(post(API + "/" + id + "/spots/"
                    + spot.get("id").asText() + "/close").contentType(MediaType.APPLICATION_JSON).content(body), user));
        }

        private JsonNode submit() throws Exception {
            record("submit", call(post(API + "/" + id + "/submit"), user));
            JsonNode full = call(get(API + "/" + id), UNDERWRITER);
            ObjectNode result = objectMapper.createObjectNode();
            result.set("steps", steps);
            result.set("decision", decision(full));
            ArrayNode offers = result.putArray("offers");
            full.get("offers").forEach(offer -> offers.add(offer(offer)));
            ArrayNode history = result.putArray("forkHistory");
            call(get(API + "/" + id + "/fork-history"), user).forEach(fork -> history.add(fork(fork)));
            JsonNode verify = call(post(API + "/" + id + "/fork/verify"), user);
            ObjectNode verification = result.putObject("verify");
            verification.set("reproducible", verify.get("reproducible"));
            verification.set("requestHash", verify.get("requestHash"));
            verification.set("diff", verify.get("diff"));
            return result;
        }
    }

    private ObjectNode fork(JsonNode fork) {
        if (fork == null || fork.isNull()) {
            return null;
        }
        ObjectNode node = objectMapper.createObjectNode();
        for (String field : List.of("rateFrom", "rateTo", "zone", "rulesVersion", "asOf")) {
            node.set(field, fork.get(field));
        }
        return node;
    }

    private ObjectNode spot(JsonNode spot) {
        ObjectNode node = objectMapper.createObjectNode();
        for (String field : List.of("title", "type", "status", "scoringFactor", "pricePp", "removable")) {
            node.set(field, spot.get(field));
        }
        JsonNode answer = spot.get("lastAnswer");
        if (answer != null && !answer.isNull()) {
            ObjectNode last = node.putObject("lastAnswer");
            last.set("verdict", answer.get("verdict"));
            last.set("optionCode", answer.get("optionCode"));
            last.set("reviewedBy", answer.get("reviewedBy"));
            ObjectNode purpose = pick(answer.get("purposeVerdict"), "status", "purpose", "generatedBy");
            if (purpose != null) {
                purpose.set("profile", pick(answer.get("purposeVerdict").get("profile"), "okved", "status"));
            }
            last.set("purpose", purpose);
            last.set("anomaly", anomaly(answer.get("anomalyVerdict")));
        }
        return node;
    }

    private ObjectNode anomaly(JsonNode verdict) {
        if (verdict == null || verdict.isNull()) {
            return null;
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.set("code", verdict.get("code"));
        node.set("classification", pick(verdict.get("classification"), "status", "code", "generatedBy", "violation"));
        node.set("check", pick(verdict.get("check"), "verdict", "rule", "explanation"));
        return node;
    }

    private ObjectNode decision(JsonNode full) {
        JsonNode decision = full.get("decision");
        if (decision == null || decision.isNull()) {
            return null;
        }
        ObjectNode node = pick(decision, "type", "decidedBy", "rulesVersion", "override", "comment", "requestHash");
        Map<String, String> titles = spots(full).stream()
                .collect(Collectors.toMap(spot -> spot.get("id").asText(), spot -> spot.get("title").asText()));
        node.put("reasonSpot", titles.get(decision.path("reasonSpotId").asText()));
        return node;
    }

    private ObjectNode offer(JsonNode offer) {
        ObjectNode node = pick(offer, "productCode", "amount", "termMonths", "collateralType", "proposedBy", "status",
                "isRequested", "isManual");
        node.set("fork", fork(offer.get("fork")));
        return node;
    }

    private ObjectNode pick(JsonNode source, String... fields) {
        if (source == null || source.isNull()) {
            return null;
        }
        ObjectNode node = objectMapper.createObjectNode();
        for (String field : fields) {
            node.set(field, source.get(field));
        }
        return node;
    }

    private static List<JsonNode> spots(JsonNode details) {
        return StreamSupport.stream(details.get("spots").spliterator(), false).toList();
    }

    private static JsonNode find(JsonNode spots, Predicate<JsonNode> which) {
        for (JsonNode spot : spots) {
            if (which.test(spot)) {
                return spot;
            }
        }
        throw new AssertionError("Пятно не найдено среди " + spots);
    }

    private static List<String> diff(JsonNode expected, JsonNode actual) {
        return JsonDiff.diff(expected, actual).stream()
                .map(FieldDiffDto::path)
                .map(path -> path + ": " + value(expected, path) + " → " + value(actual, path))
                .toList();
    }

    private static String value(JsonNode root, String path) {
        JsonNode node = root.at(path.substring(1).replace(".", "/").replaceAll("\\[(\\d+)]", "/$1"));
        return node.isMissingNode() ? "нет" : node.toString();
    }

    private static void resetMocks() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + MOCKS.getHost() + ":"
                + MOCKS.getMappedPort(MOCKS_PORT) + "/mock/reset")).POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private static Path repositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !(Files.isDirectory(dir.resolve("ml")) && Files.isDirectory(dir.resolve("mocks")))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Не найден корень репозитория с ml/ и mocks/");
        }
        return dir;
    }
}
