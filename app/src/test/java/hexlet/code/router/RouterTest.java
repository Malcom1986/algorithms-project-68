package hexlet.code.router;

import org.junit.jupiter.api.BeforeAll;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class RouterTest {

    private static List<Map<String, Object>> routes;

    @BeforeAll
    public static void setUp() {
        routes = List.of(
            Map.of(
                "method", "POST",
                "path", "users/long/:id",
                "handler", Map.of("body", "handler1"),
                "constraints", Map.of("id", "\\d+")
            ),
            Map.of(
                "path", "users/long/:way",
                "handler", Map.of("body", "handler2"),
                "constraints", Map.of("way", "[a-z]")
            ),
            Map.of(
                "path", "users/long/way/:name",
                "handler", Map.of("body", "handler3"),
                "constraints", Map.of("name", "[a-z]+")
            ),
            Map.of(
                "path", "api/:id/:name/risc-v",
                "handler", Map.of("body", "handler4"),
                "constraints", Map.of("id", ".", "name", "^[a-z]+$")
            ),
            Map.of(
                "method", "PUT",
                "path", "api/:id/:uid",
                "handler", Map.of("body", "handler5")
            ),
            Map.of(
                "path", "api/to/Japan/",
                "handler", Map.of("body", "handler6")
            ),
            Map.of(
                "path", "/",
                "handler", Map.of("body", "root")
            )
        );
    }

    @ParameterizedTest
    @MethodSource("getParams")
    public void testRotes(Map<String, String> request, Map<String, Object> expected, String expectedHandlerResult) {

        var result = Router.serve(routes, request);
        assertThat(result).containsAllEntriesOf(expected);

        var handler = (Map) result.get("handler");
        var body = handler.get("body");
        assertThat(body).isEqualTo(expectedHandlerResult);
    }

    public static Stream<Arguments> getParams() {
        return Stream.of(
            Arguments.of(
                Map.of("path", "users/long/1", "method", "POST"),
                Map.of("params", Map.of("id", "1")),
                "handler1"
            ),
            Arguments.of(
                Map.of("path", "users/long/a"),
                Map.of("params", Map.of("way", "a")),
                "handler2"
            ),
            Arguments.of(
                Map.of("path", "users/long/way/to"),
                Map.of("params", Map.of("name", "to")),
                "handler3"
            ),
            Arguments.of(
                Map.of("path", "api/id/names/risc-v"),
                Map.of("params", Map.of("id", "id", "name", "names")),
                "handler4"
            ),
            Arguments.of(
                Map.of("path", "api/v1/Risc/", "method", "PUT"),
                Map.of("params", Map.of("id", "v1", "uid", "Risc")),
                "handler5"
            ),
            Arguments.of(
                Map.of("path", "api/to/Japan/"),
                Map.of("params", Map.of()),
                "handler6"
            ),
            Arguments.of(
                Map.of("path", "/"),
                Map.of("params", Map.of()),
                "root"
            )
        );
    }
}
