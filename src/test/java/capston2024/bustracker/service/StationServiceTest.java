package capston2024.bustracker.service;

import capston2024.bustracker.domain.Station;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ErrorCode;
import capston2024.bustracker.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StationServiceTest {

    private StationService stationService;

    private StationRepository stationRepository;

    private Station station;

    @BeforeEach
    public void setup() {
        stationRepository = mock(StationRepository.class);
        stationService = new StationService(stationRepository);

        station = new Station();
        station.setId("12345");
        station.setName("Central Station");
        station.setOrganizationId("Org-123");
    }

    // 모든 정류장 조회 성공 테스트
    @Test
    public void testGetAllStations_Success() {
        Mockito.when(stationRepository.findAll()).thenReturn(List.of(station));

        var stations = stationService.getAllStations();
        assertNotNull(stations);
        assertEquals(1, stations.size());
        assertEquals("Central Station", stations.get(0).getName());
    }

    // 정류장 생성 성공 테스트
    @Test
    public void testCreateStation_Success() {
        Mockito.when(stationRepository.findByName("Central Station")).thenReturn(Optional.empty());
        Mockito.when(stationRepository.save(any(Station.class))).thenReturn(station);

        var createdStation = stationService.createStation(station);
        assertNotNull(createdStation);
        assertEquals("Central Station", createdStation.getName());
    }

    // 정류장 업데이트 성공 테스트
    @Test
    public void testUpdateStation_Success() {
        Station updatedStation = new Station();
        updatedStation.setName("Updated Station");

        Mockito.when(stationRepository.findById("12345")).thenReturn(Optional.of(station));
        Mockito.when(stationRepository.save(any(Station.class))).thenReturn(updatedStation);

        var result = stationService.updateStation("12345", updatedStation);
        assertEquals("Updated Station", result.getName());
    }

    // 정류장 삭제 성공 테스트
    @Test
    public void testDeleteStation_Success() {
        Mockito.when(stationRepository.findById("12345")).thenReturn(Optional.of(station));
        doNothing().when(stationRepository).delete(any(Station.class));

        stationService.deleteStation("12345");
        verify(stationRepository, times(1)).delete(any(Station.class));
    }

    // 정류장 조회 실패 테스트
    @Test
    public void testGetStationById_Failure() {
        Mockito.when(stationRepository.findById("99999")).thenReturn(Optional.empty());

        BusinessException thrown = assertThrows(
                BusinessException.class,
                () -> stationService.getStationById("99999")
        );
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, thrown.getErrorCode());
    }

    // 중복 정류장 생성 시 실패 테스트
    @Test
    public void testCreateStation_Failure_Duplicate() {
        Mockito.when(stationRepository.findByName("Central Station")).thenReturn(Optional.of(station));

        BusinessException thrown = assertThrows(
                BusinessException.class,
                () -> stationService.createStation(station)
        );
        assertEquals(ErrorCode.DUPLICATE_ENTITY, thrown.getErrorCode());
    }
}
