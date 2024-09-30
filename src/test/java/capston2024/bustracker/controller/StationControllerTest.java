package capston2024.bustracker.controller;

import capston2024.bustracker.domain.Station;
import capston2024.bustracker.service.StationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StationController.class)
@AutoConfigureMockMvc(addFilters = false)  // 인증 필터 비활성화
public class StationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StationService stationService;

    private Station station;

    @BeforeEach
    public void setup() {
        station = new Station();
        station.setId("12345");
        station.setName("Central Station");
        station.setOrganizationId("Org-123");
    }

    // 특정 정류장 이름으로 조회 성공 테스트
    @Test
    @WithMockUser
    public void testGetStationByName_Success() throws Exception {
        List<Station> stations = Arrays.asList(station);
        Mockito.when(stationService.getStation(eq("Central Station"))).thenReturn(stations);

        mockMvc.perform(get("/api/station/find")
                        .param("name", "Central Station"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].name").value("Central Station"));
    }

    // 모든 정류장 조회 성공 테스트
    @Test
    @WithMockUser
    public void testGetAllStations_Success() throws Exception {
        List<Station> stations = Arrays.asList(station);
        Mockito.when(stationService.getAllStations()).thenReturn(stations);

        mockMvc.perform(get("/api/station/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].name").value("Central Station"));
    }

    // ID로 특정 정류장 조회 성공 테스트
    @Test
    @WithMockUser
    public void testGetStationById_Success() throws Exception {
        Mockito.when(stationService.getStationById(eq("12345"))).thenReturn(station);

        mockMvc.perform(get("/api/station/12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Central Station"));
    }

    // 새로운 정류장 생성 성공 테스트
    @Test
    @WithMockUser
    public void testCreateStation_Success() throws Exception {
        Mockito.when(stationService.createStation(any(Station.class))).thenReturn(station);

        mockMvc.perform(post("/api/station/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(station)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Central Station"));
    }

    // 정류장 업데이트 성공 테스트
    @Test
    @WithMockUser
    public void testUpdateStation_Success() throws Exception {
        Station updatedStation = new Station();
        updatedStation.setId("12345");
        updatedStation.setName("Updated Station");
        updatedStation.setOrganizationId("Org-123");

        Mockito.when(stationService.updateStation(eq("12345"), any(Station.class)))
                .thenReturn(updatedStation);

        mockMvc.perform(put("/api/station/update/12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(updatedStation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Station"));
    }

    // 정류장 삭제 성공 테스트
    @Test
    @WithMockUser
    public void testDeleteStation_Success() throws Exception {
        Mockito.doNothing().when(stationService).deleteStation(eq("12345"));

        mockMvc.perform(delete("/api/station/delete/12345"))
                .andExpect(status().isNoContent());
    }
}
