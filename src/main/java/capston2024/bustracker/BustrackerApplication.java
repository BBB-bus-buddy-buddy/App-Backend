package capston2024.bustracker;

import capston2024.bustracker.config.AppProperties;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.PassengerTripEvent;
import capston2024.bustracker.domain.Route;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.PassengerTripEventRepository;
import capston2024.bustracker.repository.RouteRepository;
import capston2024.bustracker.repository.StationRepository;
import com.mongodb.DBRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
@Slf4j
public class BustrackerApplication {

    private static final String SEED_ORGANIZATION_ID = "coshow";
    private static final String PRIORITY_STATION_NAME = "울산과학대학교(동부)";
    private static final int TARGET_EVENT_COUNT = 2000;
    private static final int LOOKBACK_DAYS = 30;

    public static void main(String[] args) {
        SpringApplication.run(BustrackerApplication.class, args);
    }

    @Bean
    CommandLineRunner passengerTripEventSeeder(
            PassengerTripEventRepository tripEventRepository,
            BusRepository busRepository,
            RouteRepository routeRepository,
            StationRepository stationRepository
    ) {
        return args -> {
            long existingCount = tripEventRepository.countByOrganizationId(SEED_ORGANIZATION_ID);
            if (existingCount >= TARGET_EVENT_COUNT) {
                log.info("PassengerTripEvent mock data already prepared ({} records)", existingCount);
                return;
            }

            List<Bus> buses = busRepository.findByOrganizationId(SEED_ORGANIZATION_ID);
            List<Route> routes = routeRepository.findByOrganizationId(SEED_ORGANIZATION_ID);
            Map<String, Station> stationMap = stationRepository.findAllByOrganizationId(SEED_ORGANIZATION_ID)
                    .stream()
                    .collect(Collectors.toMap(Station::getId, station -> station));

            if (buses.isEmpty() || routes.isEmpty() || stationMap.isEmpty()) {
                log.warn("Skip PassengerTripEvent seeding - buses:{}, routes:{}, stations:{}",
                        buses.size(), routes.size(), stationMap.size());
                return;
            }

            Random random = new Random();
            List<PassengerTripEvent> seedEvents = new ArrayList<>();
            int eventsToCreate = TARGET_EVENT_COUNT - (int) existingCount;
            long now = System.currentTimeMillis();

            Map<String, List<Route>> stationRoutes = buildStationRouteIndex(routes);
            Map<String, List<Bus>> busesByRoute = groupBusesByRoute(buses);
            Station crowdedStation = stationMap.values().stream()
                    .filter(station -> PRIORITY_STATION_NAME.equals(station.getName()))
                    .findFirst()
                    .orElse(null);

            if (crowdedStation != null) {
                int crowdedQuota = Math.min(eventsToCreate,
                        Math.max(15, (int) Math.round(eventsToCreate * 0.45)));
                addEventsForStation(crowdedStation, crowdedQuota, seedEvents, random, now,
                        stationRoutes, busesByRoute, routes, buses);
            }

            List<Station> remainingStations = stationMap.values().stream()
                    .filter(station -> crowdedStation == null || !station.getId().equals(crowdedStation.getId()))
                    .collect(Collectors.toList());
            Collections.shuffle(remainingStations, random);

            int cursor = 0;
            while (seedEvents.size() < eventsToCreate && !remainingStations.isEmpty()) {
                Station targetStation = remainingStations.get(cursor % remainingStations.size());
                addEventsForStation(targetStation, 1, seedEvents, random, now,
                        stationRoutes, busesByRoute, routes, buses);
                cursor++;
            }

            if (!seedEvents.isEmpty()) {
                tripEventRepository.saveAll(seedEvents);
                log.info("Seeded {} PassengerTripEvent docs for organization '{}'", seedEvents.size(),
                        SEED_ORGANIZATION_ID);
            }
        };
    }

    private Map<String, List<Route>> buildStationRouteIndex(List<Route> routes) {
        Map<String, List<Route>> index = new HashMap<>();
        for (Route route : routes) {
            if (route.getStations() == null) {
                continue;
            }
            for (Route.RouteStation routeStation : route.getStations()) {
                String stationId = extractDbRefId(routeStation.getStationId());
                if (stationId == null) {
                    continue;
                }
                index.computeIfAbsent(stationId, key -> new ArrayList<>()).add(route);
            }
        }
        return index;
    }

    private Map<String, List<Bus>> groupBusesByRoute(List<Bus> buses) {
        Map<String, List<Bus>> byRoute = new HashMap<>();
        for (Bus bus : buses) {
            DBRef routeRef = bus.getRouteId();
            String routeId = routeRef != null && routeRef.getId() != null
                    ? routeRef.getId().toString()
                    : null;
            if (routeId == null) {
                continue;
            }
            byRoute.computeIfAbsent(routeId, key -> new ArrayList<>()).add(bus);
        }
        return byRoute;
    }

    private void addEventsForStation(Station station,
                                     int desiredCount,
                                     List<PassengerTripEvent> collector,
                                     Random random,
                                     long now,
                                     Map<String, List<Route>> stationRoutes,
                                     Map<String, List<Bus>> busesByRoute,
                                     List<Route> routes,
                                     List<Bus> buses) {
        for (int i = 0; i < desiredCount; i++) {
            Route route = pickRouteForStation(station, stationRoutes, routes, random);
            Bus bus = pickBusForRoute(route, busesByRoute, buses, random);
            collector.add(createEvent(station, route, bus, random, now));
        }
    }

    private Route pickRouteForStation(Station station,
                                      Map<String, List<Route>> stationRoutes,
                                      List<Route> fallbackRoutes,
                                      Random random) {
        List<Route> candidates = stationRoutes.get(station.getId());
        if (candidates != null && !candidates.isEmpty()) {
            return candidates.get(random.nextInt(candidates.size()));
        }
        return fallbackRoutes.get(random.nextInt(fallbackRoutes.size()));
    }

    private Bus pickBusForRoute(Route route,
                                Map<String, List<Bus>> busesByRoute,
                                List<Bus> fallbackBuses,
                                Random random) {
        if (route != null) {
            List<Bus> routeBuses = busesByRoute.get(route.getId());
            if (routeBuses != null && !routeBuses.isEmpty()) {
                return routeBuses.get(random.nextInt(routeBuses.size()));
            }
        }
        return fallbackBuses.get(random.nextInt(fallbackBuses.size()));
    }

    private PassengerTripEvent createEvent(Station station,
                                           Route route,
                                           Bus bus,
                                           Random random,
                                           long now) {
        long timestamp = now - (long) (random.nextDouble() * Duration.ofDays(LOOKBACK_DAYS).toMillis());
        PassengerTripEvent.EventType eventType = random.nextDouble() < 0.6
                ? PassengerTripEvent.EventType.BOARD
                : PassengerTripEvent.EventType.ALIGHT;

        double latitude = 0.0;
        double longitude = 0.0;
        if (station.getLocation() != null) {
            longitude = station.getLocation().getX();
            latitude = station.getLocation().getY();
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "AUTO_SEED");
        metadata.put("stationName", station.getName());
        if (route != null && route.getRouteName() != null) {
            metadata.put("routeName", route.getRouteName());
        }

        return PassengerTripEvent.builder()
                .userId("mock-user-" + UUID.randomUUID())
                .organizationId(SEED_ORGANIZATION_ID)
                .busNumber(bus.getBusNumber())
                .stationId(station.getId())
                .eventType(eventType)
                .latitude(latitude)
                .longitude(longitude)
                .distanceToBus(5 + random.nextDouble() * 25)
                .estimatedBusSpeed(10 + random.nextDouble() * 35)
                .timestamp(timestamp)
                .metadata(metadata)
                .build();
    }

    private String extractDbRefId(DBRef ref) {
        return ref != null && ref.getId() != null ? ref.getId().toString() : null;
    }

}
