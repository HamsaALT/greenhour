package dk.greenhour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Runs against a real PostgreSQL with all Flyway migrations applied, including the
 * demo data. Anna is one of the demo users from V2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class PlanningFlowIT {

    private static final String ANNA = "3f0c2a8e-5d1b-4c7e-9a43-2b6f8e1d7c01";
    private static final String JONAS = "8b4e6d2a-1c3f-4a5b-9e7d-6f2a1b3c4d02";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void marketDataIsPublic() throws Exception {
        mvc.perform(get("/api/market").param("area", "DK2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.area").value("DK2"))
            .andExpect(jsonPath("$.slots.length()").value(greaterThanOrEqualTo(92)))
            .andExpect(jsonPath("$.slots[0].demoData").value(true));
    }

    @Test
    void demoUserSeesTheirHouseholdAndAppliances() throws Exception {
        mvc.perform(get("/api/household").with(as(ANNA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceArea").value("DK2"));

        mvc.perform(get("/api/appliances").with(as(ANNA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void newUserGetsAHouseholdOnFirstVisit() throws Exception {
        mvc.perform(get("/api/household").with(jwt().jwt(j -> j.subject("brand-new").claim("given_name", "Maja"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Maja's home"));

        mvc.perform(get("/api/appliances").with(as("brand-new")))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void plansARunAndTracksTheSaving() throws Exception {
        long applianceId = firstApplianceId(ANNA);
        Instant start = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.HOURS);
        String body = """
            {"applianceId": %d, "earliestStart": "%s", "deadline": "%s", "goal": "PRICE"}"""
            .formatted(applianceId, start, start.plus(Duration.ofHours(12)));

        JsonNode preview = read(mvc.perform(post("/api/runs/preview").with(as(ANNA))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        assertThat(preview.get("savedDkk").decimalValue()).isNotNegative();
        assertThat(preview.get("options").size()).isGreaterThan(1);

        JsonNode run = read(mvc.perform(post("/api/runs").with(as(ANNA))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PLANNED"))
            .andReturn().getResponse().getContentAsString());
        assertThat(run.get("plannedStart").asText()).isEqualTo(preview.get("start").asText());

        long runId = run.get("id").asLong();
        mvc.perform(patch("/api/runs/{id}", runId).with(as(ANNA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"DONE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applianceName").isNotEmpty());

        // Done is final.
        mvc.perform(patch("/api/runs/{id}", runId).with(as(ANNA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"CANCELLED\"}"))
            .andExpect(status().isConflict());

        mvc.perform(get("/api/runs/summary").with(as(ANNA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completedRuns").value(greaterThan(1)));
    }

    @Test
    void cannotPlanWithSomeoneElsesAppliance() throws Exception {
        long annasAppliance = firstApplianceId(ANNA);
        Instant start = Instant.now().plus(Duration.ofHours(1));
        String body = """
            {"applianceId": %d, "earliestStart": "%s", "deadline": "%s", "goal": "PRICE"}"""
            .formatted(annasAppliance, start, start.plus(Duration.ofHours(6)));

        mvc.perform(post("/api/runs").with(as(JONAS)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound());
    }

    @Test
    void explainsWhenPricesAreNotAvailable() throws Exception {
        long applianceId = firstApplianceId(ANNA);
        Instant start = Instant.now().plus(Duration.ofDays(3));
        String body = """
            {"applianceId": %d, "earliestStart": "%s", "deadline": "%s", "goal": "PRICE"}"""
            .formatted(applianceId, start, start.plus(Duration.ofHours(8)));

        mvc.perform(post("/api/runs/preview").with(as(ANNA)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("PRICES_NOT_PUBLISHED"))
            .andExpect(jsonPath("$.detail").value(containsString("published")));
    }

    @Test
    void duplicateApplianceNameIsAConflict() throws Exception {
        mvc.perform(post("/api/appliances").with(as(JONAS)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Opvaskemaskine", "kind": "DISHWASHER", "powerKw": 1.2, "runMinutes": 120}"""))
            .andExpect(status().isConflict());
    }

    @Test
    void reportViewsAreReadableByThePowerBiRole() {
        Integer savingsRows = jdbc.queryForObject("select count(*) from report_run_savings", Integer.class);
        assertThat(savingsRows).isGreaterThan(50);

        Boolean canRead = jdbc.queryForObject(
            "select has_table_privilege('powerbi_reader', 'report_daily_market', 'select')", Boolean.class);
        Boolean canReadBaseTable = jdbc.queryForObject(
            "select has_table_privilege('powerbi_reader', 'scheduled_run', 'select')", Boolean.class);
        assertThat(canRead).isTrue();
        assertThat(canReadBaseTable).isFalse();

        Integer profileHours = jdbc.queryForObject(
            "select count(distinct hour_of_day) from report_hourly_profile", Integer.class);
        assertThat(profileHours).isEqualTo(24);
    }

    private long firstApplianceId(String owner) throws Exception {
        String body = mvc.perform(get("/api/appliances").with(as(owner)))
            .andReturn().getResponse().getContentAsString();
        return read(body).get(0).get("id").asLong();
    }

    private JsonNode read(String body) throws Exception {
        return json.readTree(body);
    }

    private static RequestPostProcessor as(String subject) {
        return jwt().jwt(j -> j.subject(subject));
    }
}
