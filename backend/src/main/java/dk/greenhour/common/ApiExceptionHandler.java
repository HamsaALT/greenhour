package dk.greenhour.common;

import java.util.Map;

import dk.greenhour.market.MarketDataUnavailableException;
import dk.greenhour.planning.InvalidStatusChangeException;
import dk.greenhour.planning.NoFeasibleWindowException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps domain exceptions to RFC 9457 problem responses. Validation errors are handled by the base class.
 *
 * Besides the English detail, each problem carries a stable {@code code} (and {@code params}
 * where useful) so the web app can show its own message in the user's language.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), Map.of());
    }

    @ExceptionHandler(NoFeasibleWindowException.class)
    ProblemDetail noWindow(NoFeasibleWindowException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.reason().name(), e.getMessage(), e.params());
    }

    @ExceptionHandler(InvalidStatusChangeException.class)
    ProblemDetail invalidStatus(InvalidStatusChangeException e) {
        return problem(HttpStatus.CONFLICT, e.code(), e.getMessage(), Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "DUPLICATE",
            "That conflicts with existing data, for example an appliance with the same name.", Map.of());
    }

    @ExceptionHandler(MarketDataUnavailableException.class)
    ProblemDetail upstream(MarketDataUnavailableException e) {
        return problem(HttpStatus.BAD_GATEWAY, "MARKET_DATA_UNAVAILABLE", e.getMessage(), Map.of());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail, Map<String, Object> params) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        if (!params.isEmpty()) {
            problem.setProperty("params", params);
        }
        return problem;
    }
}
