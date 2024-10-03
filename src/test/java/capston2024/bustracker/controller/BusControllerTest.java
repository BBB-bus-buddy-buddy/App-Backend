//package capston2024.bustracker.controller;
//
//import capston2024.bustracker.config.SecurityConfig;
//import capston2024.bustracker.config.dto.BusRegisterRequestDTO;
//import capston2024.bustracker.domain.Bus;
//import capston2024.bustracker.service.BusService;
//import capston2024.bustracker.service.CustomOAuth2UserService;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.context.annotation.Import;
//import org.springframework.http.MediaType;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.util.Arrays;
//import java.util.List;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@WebMvcTest(BusController.class)
//@Import(SecurityConfig.class)  // 만약 SecurityConfig가 있을 경우
//public class BusControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockBean
//    private BusService busService;
//
//    @MockBean
//    private CustomOAuth2UserService customOAuth2UserService;  // 모킹 추가
//
//    private BusRegisterRequestDTO busRegisterRequestDTO;
//
//    @BeforeEach
//    public void setup() {
//        busRegisterRequestDTO = new BusRegisterRequestDTO();
//        busRegisterRequestDTO.setBusNumber("1001");
//        busRegisterRequestDTO.setStationNames(Arrays.asList("Station A", "Station B"));
//    }
//
//    @Test
//    @WithMockUser(roles = "ADMIN")  // 인증된 사용자 역할 추가
//    public void testCreateBus_Success() throws Exception {
//        Mockito.when(busService.createBus(any(BusRegisterRequestDTO.class)))
//                .thenReturn("1001번 버스가 성공적으로 등록되었습니다.");
//
//        mockMvc.perform(post("/api/bus/create")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(new ObjectMapper().writeValueAsString(busRegisterRequestDTO))
//                        .with(SecurityMockMvcRequestPostProcessors.csrf()))  // CSRF 토큰 추가
//                .andExpect(status().isOk())
//                .andExpect(content().string("1001번 버스가 성공적으로 등록되었습니다."));
//    }
//
//    @Test
//    @WithMockUser(roles = "ADMIN")
//    public void testCreateBus_Failure() throws Exception {
//        Mockito.when(busService.createBus(any(BusRegisterRequestDTO.class)))
//                .thenReturn("이미 있는 버스 번호입니다.");
//
//        mockMvc.perform(post("/api/bus/create")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(new ObjectMapper().writeValueAsString(busRegisterRequestDTO))
//                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
//                .andExpect(status().isBadRequest())
//                .andExpect(content().string("이미 있는 버스 번호입니다."));
//    }
//
//    @Test
//    @WithMockUser(roles = "ADMIN")
//    public void testDeleteBus_Success() throws Exception {
//        Mockito.when(busService.removeBus(eq("1001")))
//                .thenReturn("1001번 버스가 성공적으로 삭제되었습니다.");
//
//        mockMvc.perform(delete("/api/bus/delete/1001")
//                        .with(SecurityMockMvcRequestPostProcessors.csrf()))  // CSRF 토큰 추가
//                .andExpect(status().isOk())
//                .andExpect(content().string("1001번 버스가 성공적으로 삭제되었습니다."));
//    }
//
//    @Test
//    @WithMockUser(roles = "ADMIN")
//    public void testDeleteBus_Failure() throws Exception {
//        Mockito.when(busService.removeBus(eq("1001")))
//                .thenReturn("버스를 찾을 수 없습니다.");
//
//        mockMvc.perform(delete("/api/bus/delete/1001")
//                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
//                .andExpect(status().isBadRequest())
//                .andExpect(content().string("버스를 찾을 수 없습니다."));
//    }
//
//    @Test
//    @WithMockUser(roles = "USER")  // 일반 사용자 역할 추가
//    public void testGetAllBuses() throws Exception {
//        List<BusRegisterRequestDTO> buses = Arrays.asList(busRegisterRequestDTO);
//        Mockito.when(busService.getAllBuses()).thenReturn(buses);
//
//        mockMvc.perform(get("/api/bus/list"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.size()").value(1))
//                .andExpect(jsonPath("$[0].busNumber").value("1001"))
//                .andExpect(jsonPath("$[0].stationNames[0]").value("Station A"));
//    }
//
//    @Test
//    @WithMockUser(roles = "USER")
//    public void testGetBusByNumber_Success() throws Exception {
//        Mockito.when(busService.getBusByNumber(eq("1001"))).thenReturn(busRegisterRequestDTO);
//
//        mockMvc.perform(get("/api/bus/get/1001"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.busNumber").value("1001"))
//                .andExpect(jsonPath("$.stationNames[0]").value("Station A"));
//    }
//
//    @Test
//    @WithMockUser(roles = "USER")
//    public void testGetBusByNumber_Failure() throws Exception {
//        Mockito.when(busService.getBusByNumber(eq("1001"))).thenReturn(null);
//
//        mockMvc.perform(get("/api/bus/get/1001"))
//                .andExpect(status().isNotFound());
//    }
//}
