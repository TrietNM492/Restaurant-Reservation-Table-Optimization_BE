package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.config.CorsConfig;
import com.restaurant.ireservation.config.CustomJwtAuthenticationConverter;
import com.restaurant.ireservation.config.SecurityConfig;
import com.restaurant.ireservation.dto.response.TableUtilizationDashboardDTO;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.security.SessionManager;
import com.restaurant.ireservation.service.TableAnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, CorsConfig.class, CustomJwtAuthenticationConverter.class, GlobalException.class})
class DashboardSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TableAnalyticsService tableAnalyticsService;

    @MockBean
    private SessionManager sessionManager;

    private static final String URL = "/api/v1/dashboard/tables";

    private TableUtilizationDashboardDTO mockResponse() {
        return TableUtilizationDashboardDTO.builder()
                .startDate(LocalDate.now().minusDays(30))
                .endDate(LocalDate.now())
                .daysAnalyzed(31)
                .totalTables(10)
                .totalReservations(25L)
                .overallUtilizationRate(18.5)
                .averagePartySize(4.2)
                .mostPopularTable("Table #1")
                .peakHour("19:00 - 20:00")
                .tableUsageStats(Collections.emptyList())
                .hourlyPeakDistribution(Collections.emptyList())
                .partySizeDistribution(Collections.emptyList())
                .build();
    }

    @Test
    @DisplayName("ROLE_ADMIN: should return HTTP 200 OK")
    void getDashboard_asAdmin_returns200() throws Exception {
        when(tableAnalyticsService.getTableUtilizationDashboard(any(), any())).thenReturn(mockResponse());

        mockMvc.perform(get(URL)
                        .with(jwt().jwt(builder -> builder.subject("admin_user"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTables").value(10))
                .andExpect(jsonPath("$.data.overallUtilizationRate").value(18.5));

        verify(tableAnalyticsService, times(1)).getTableUtilizationDashboard(any(), any());
    }

    @Test
    @DisplayName("ROLE_MANAGER: should return HTTP 200 OK")
    void getDashboard_asManager_returns200() throws Exception {
        when(tableAnalyticsService.getTableUtilizationDashboard(any(), any())).thenReturn(mockResponse());

        mockMvc.perform(get(URL)
                        .with(jwt().jwt(builder -> builder.subject("manager_user"))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTables").value(10));

        verify(tableAnalyticsService, times(1)).getTableUtilizationDashboard(any(), any());
    }

    @Test
    @DisplayName("ROLE_STAFF: should be blocked with HTTP 403 Forbidden")
    void getDashboard_asStaff_returns403Forbidden() throws Exception {
        mockMvc.perform(get(URL)
                        .with(jwt().jwt(builder -> builder.subject("staff_user"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(tableAnalyticsService, never()).getTableUtilizationDashboard(any(), any());
    }

    @Test
    @DisplayName("ROLE_CUSTOMER: should be blocked with HTTP 403 Forbidden")
    void getDashboard_asCustomer_returns403Forbidden() throws Exception {
        mockMvc.perform(get(URL)
                        .with(jwt().jwt(builder -> builder.subject("customer_user"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(tableAnalyticsService, never()).getTableUtilizationDashboard(any(), any());
    }

    @Test
    @DisplayName("Anonymous / No token: should be blocked with HTTP 401 Unauthorized")
    void getDashboard_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());

        verify(tableAnalyticsService, never()).getTableUtilizationDashboard(any(), any());
    }
}
