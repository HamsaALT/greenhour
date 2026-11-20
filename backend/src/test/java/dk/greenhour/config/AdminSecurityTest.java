package dk.greenhour.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import dk.greenhour.admin.AdminController;
import dk.greenhour.market.MarketDataImporter;
import dk.greenhour.market.MarketDataUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, ClockConfig.class})
class AdminSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MarketDataImporter importer;

    @Test
    void anonymousUsersAreRejected() throws Exception {
        mvc.perform(post("/api/admin/market-import")).andExpect(status().isUnauthorized());
    }

    @Test
    void regularUsersAreForbidden() throws Exception {
        mvc.perform(post("/api/admin/market-import")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminsCanTriggerAnImport() throws Exception {
        var from = Instant.parse("2026-11-17T23:00:00Z");
        when(importer.importRange(any(), any()))
            .thenReturn(new MarketDataImporter.ImportResult(from, from.plusSeconds(86400), 192, 96));

        mvc.perform(post("/api/admin/market-import")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slotsStored").value(192));
    }

    @Test
    void limitsHowFarBackAnImportCanGo() throws Exception {
        mvc.perform(post("/api/admin/market-import?daysBack=400")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void upstreamFailureBecomesBadGateway() throws Exception {
        when(importer.importRange(any(), any()))
            .thenThrow(new MarketDataUnavailableException("Could not fetch DayAheadPrices", null));

        mvc.perform(post("/api/admin/market-import")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isBadGateway());
    }

    @Test
    void showsNothingBeforeTheFirstImport() throws Exception {
        when(importer.lastStatus()).thenReturn(Optional.empty());

        mvc.perform(get("/api/admin/market-import")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isNotFound());
    }
}
