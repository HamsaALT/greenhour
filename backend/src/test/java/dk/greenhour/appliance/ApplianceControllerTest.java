package dk.greenhour.appliance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import dk.greenhour.common.NotFoundException;
import dk.greenhour.config.SecurityConfig;
import dk.greenhour.household.Household;
import dk.greenhour.market.PriceArea;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApplianceController.class)
@Import(SecurityConfig.class)
class ApplianceControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ApplianceService service;

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/appliances")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsOnlyTheCallersAppliances() throws Exception {
        var household = new Household("user-1", "Test home", PriceArea.DK1);
        when(service.list("user-1")).thenReturn(List.of(
            new Appliance(household, "Dishwasher", ApplianceKind.DISHWASHER, new BigDecimal("1.2"), 120)));

        mvc.perform(get("/api/appliances").with(jwt().jwt(j -> j.subject("user-1"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Dishwasher"))
            .andExpect(jsonPath("$[0].energyPerRunKwh").value(2.4));
    }

    @Test
    void createsAnAppliance() throws Exception {
        var household = new Household("user-1", "Test home", PriceArea.DK1);
        when(service.create(any(), eq("Car"), eq(ApplianceKind.EV_CHARGER), any(), eq(240)))
            .thenReturn(new Appliance(household, "Car", ApplianceKind.EV_CHARGER, new BigDecimal("11"), 240));

        mvc.perform(post("/api/appliances")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Car", "kind": "EV_CHARGER", "powerKw": 11, "runMinutes": 240}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.energyPerRunKwh").value(44.0));
    }

    @Test
    void rejectsRunTimesThatDoNotFitMarketSlots() throws Exception {
        mvc.perform(post("/api/appliances")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Dryer", "kind": "DRYER", "powerKw": 2.5, "runMinutes": 100}"""))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnrealisticPower() throws Exception {
        mvc.perform(post("/api/appliances")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Kiln", "kind": "OTHER", "powerKw": 50, "runMinutes": 60}"""))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void returnsNotFoundForSomeoneElsesAppliance() throws Exception {
        when(service.update(eq("user-2"), eq(7L), any(), any(), any(), anyInt()))
            .thenThrow(new NotFoundException("Appliance 7 not found"));

        mvc.perform(put("/api/appliances/7")
                .with(jwt().jwt(j -> j.subject("user-2")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Dishwasher", "kind": "DISHWASHER", "powerKw": 1.2, "runMinutes": 120}"""))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Appliance 7 not found"));
    }
}
