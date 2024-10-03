package capston2024.bustracker.service;

import capston2024.bustracker.domain.Station;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ErrorCode;
import capston2024.bustracker.repository.StationRepository;
import capston2024.bustracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StationRepository stationRepository;

    @InjectMocks
    private UserService userService;

    private User user;
    private Station station;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        user = new User();
        user.setId("user-123");
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setMyStations(new ArrayList<>());

        station = new Station();
        station.setId("station-123");
        station.setName("Central Station");
    }

    @Test
    public void testAddMyStation_Success() {
        // given
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(stationRepository.findById("station-123")).thenReturn(Optional.of(station));

        // when
        boolean result = userService.addMyStation("user-123", "station-123");

        // then
        assertTrue(result);
        verify(userRepository, times(1)).save(user);
        assertEquals(1, user.getMyStations().size());
        assertEquals("Central Station", user.getMyStations().get(0).getName());
    }

    @Test
    public void testAddMyStation_StationAlreadyAdded() {
        // given
        user.getMyStations().add(station);
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(stationRepository.findById("station-123")).thenReturn(Optional.of(station));

        // when
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.addMyStation("user-123", "station-123");
        });

        // then
        assertEquals(ErrorCode.DUPLICATE_ENTITY, exception.getErrorCode());
        verify(userRepository, never()).save(user);
    }

    @Test
    public void testAddMyStation_UserNotFound() {
        // given
        when(userRepository.findById("user-123")).thenReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.addMyStation("user-123", "station-123");
        });

        // then
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    public void testAddMyStation_StationNotFound() {
        // given
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(stationRepository.findById("station-123")).thenReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.addMyStation("user-123", "station-123");
        });

        // then
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, exception.getErrorCode());
    }
}
