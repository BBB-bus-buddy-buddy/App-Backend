package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.*;
import capston2024.bustracker.domain.PassengerTripEvent;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.repository.PassengerTripEventRepository;
import capston2024.bustracker.repository.StationRepository;
import capston2024.bustracker.service.ai.AiInsightService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StationInsightService {

    private final PassengerTripEventRepository passengerTripEventRepository;
    private final StationRepository stationRepository;
    private final capston2024.bustracker.repository.RouteRepository routeRepository;
    private final BusService busService;
    private final AiInsightService aiInsightService;
    private final ZoneId zoneId = ZoneId.systemDefault();
    private final Map<String, Integer> busSeatCache = new ConcurrentHashMap<>();
    private final Map<String, String> stationToRouteCache = new ConcurrentHashMap<>();

    public StationStatsResponseDTO analyzeStation(String stationId, int lookbackDays) {
        long analysisEnd = System.currentTimeMillis();
        long analysisStart = analysisEnd - Duration.ofDays(lookbackDays).toMillis();
        List<PassengerTripEvent> events = passengerTripEventRepository
                .findByStationIdAndTimestampBetween(stationId, analysisStart, analysisEnd);

        return buildStationStats(stationId, lookbackDays, analysisStart, analysisEnd, events);
    }

    public NetworkInsightResponseDTO analyzeNetwork(int lookbackDays) {
        long analysisEnd = System.currentTimeMillis();
        long analysisStart = analysisEnd - Duration.ofDays(lookbackDays).toMillis();
        List<PassengerTripEvent> events = passengerTripEventRepository
                .findByTimestampBetween(analysisStart, analysisEnd);

        Map<String, StationAggregate> aggregates = new HashMap<>();
        for (PassengerTripEvent event : events) {
            if (event.getStationId() == null) {
                continue;
            }
            StationAggregate aggregate = aggregates.computeIfAbsent(event.getStationId(),
                    id -> new StationAggregate(id, resolveStationName(id)));
            aggregate.accumulate(event);
        }

        List<StationSummaryDTO> summaries = new ArrayList<>(aggregates.values().stream()
                .map(StationAggregate::toSummary)
                .collect(Collectors.toList()));

        appendStationsWithoutEvents(events, summaries, aggregates);
        summaries.sort(Comparator.comparingLong(StationSummaryDTO::getTotalBoardings).reversed());

        List<StationSummaryDTO> busiestStations = summaries.stream()
                .limit(5)
                .collect(Collectors.toList());

        List<StationSummaryDTO> overcrowdedStations = summaries.stream()
                .filter(summary -> summary.getUtilizationRate() > 1.1)
                .collect(Collectors.toList());

        List<String> recommendations = buildNetworkRecommendations(busiestStations, overcrowdedStations);

        return NetworkInsightResponseDTO.builder()
                .lookbackDays(lookbackDays)
                .analysisStartTimestamp(analysisStart)
                .analysisEndTimestamp(analysisEnd)
                .busiestStations(busiestStations)
                .overcrowdedStations(overcrowdedStations)
                .allStationSummaries(summaries)
                .recommendations(recommendations)
                .build();
    }

    public InsightQuestionResponseDTO answerQuestion(InsightQuestionRequestDTO request) {
        int lookbackDays = request.getLookbackDays() != null ? request.getLookbackDays() : 7;
        StationStatsResponseDTO stationStats = null;
        if (request.getStationId() != null && !request.getStationId().isBlank()) {
            stationStats = analyzeStation(request.getStationId(), lookbackDays);
        }

        NetworkInsightResponseDTO networkStats = analyzeNetwork(lookbackDays);

        if (stationStats == null && networkStats.getBusiestStations() != null
                && !networkStats.getBusiestStations().isEmpty()) {
            stationStats = analyzeStation(networkStats.getBusiestStations().get(0).getStationId(), lookbackDays);
        }

        StationStatsResponseDTO finalStationStats = stationStats;
        String answer = aiInsightService.generateInsightAnswer(
                        request.getQuestion(),
                        stationStats,
                        networkStats,
                        request.getExternalFactors())
                .orElseGet(() -> buildInsightAnswer(request.getQuestion(), finalStationStats, networkStats));

        return InsightQuestionResponseDTO.builder()
                .question(request.getQuestion())
                .answer(answer)
                .externalFactors(request.getExternalFactors())
                .stationStats(stationStats)
                .networkStats(networkStats)
                .build();
    }

    private StationStatsResponseDTO buildStationStats(String stationId,
                                                      int lookbackDays,
                                                      long analysisStart,
                                                      long analysisEnd,
                                                      List<PassengerTripEvent> events) {
        // 정류장 정보 조회 (좌표 포함)
        Station currentStation = stationRepository.findById(stationId).orElse(null);
        Double latitude = null;
        Double longitude = null;

        if (currentStation != null && currentStation.getLocation() != null) {
            latitude = currentStation.getLocation().getY();
            longitude = currentStation.getLocation().getX();
        }

        long totalBoardings = events.stream()
                .filter(event -> event.getEventType() == PassengerTripEvent.EventType.BOARD)
                .count();
        long totalAlightings = events.stream()
                .filter(event -> event.getEventType() == PassengerTripEvent.EventType.ALIGHT)
                .count();

        Map<Integer, Long> boardingsByHour = new HashMap<>();
        Map<Integer, Long> alightingsByHour = new HashMap<>();
        Map<DayOfWeek, Long> boardingsByDay = new HashMap<>();
        Map<BusKey, Long> boardingsPerBus = new HashMap<>();

        for (PassengerTripEvent event : events) {
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(event.getTimestamp()), zoneId);
            int hour = dateTime.getHour();
            DayOfWeek day = dateTime.getDayOfWeek();

            if (event.getEventType() == PassengerTripEvent.EventType.BOARD) {
                boardingsByHour.merge(hour, 1L, Long::sum);
                boardingsByDay.merge(day, 1L, Long::sum);
                BusKey key = buildBusKey(event.getBusNumber(), event.getOrganizationId());
                if (key != null) {
                    boardingsPerBus.merge(key, 1L, Long::sum);
                }
            } else if (event.getEventType() == PassengerTripEvent.EventType.ALIGHT) {
                alightingsByHour.merge(hour, 1L, Long::sum);
            }
        }

        List<StationPeakInfoDTO> topHours = boardingsByHour.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> StationPeakInfoDTO.builder()
                        .label(formatHourLabel(entry.getKey()))
                        .boardings(entry.getValue())
                        .alightings(alightingsByHour.getOrDefault(entry.getKey(), 0L))
                        .build())
                .collect(Collectors.toList());

        StationPeakInfoDTO busiestHour = topHours.isEmpty() ? null : topHours.get(0);

        StationPeakInfoDTO busiestDay = boardingsByDay.entrySet().stream()
                .sorted(Map.Entry.<DayOfWeek, Long>comparingByValue().reversed())
                .findFirst()
                .map(entry -> StationPeakInfoDTO.builder()
                        .label(entry.getKey().name())
                        .boardings(entry.getValue())
                        .alightings(0)
                        .build())
                .orElse(null);

        double utilizationRate = calculateUtilization(totalBoardings, boardingsPerBus);
        String recommendation = buildStationRecommendation(utilizationRate, totalBoardings, busiestHour);

        return StationStatsResponseDTO.builder()
                .stationId(stationId)
                .stationName(resolveStationName(stationId))
                .latitude(latitude)
                .longitude(longitude)
                .lookbackDays(lookbackDays)
                .analysisStartTimestamp(analysisStart)
                .analysisEndTimestamp(analysisEnd)
                .totalBoardings(totalBoardings)
                .totalAlightings(totalAlightings)
                .netPassengers(totalBoardings - totalAlightings)
                .utilizationRate(utilizationRate)
                .busiestHour(busiestHour)
                .busiestDay(busiestDay)
                .topHours(topHours)
                .recommendation(recommendation)
                .build();
    }

    private double calculateUtilization(long totalBoardings, Map<BusKey, Long> boardingsPerBus) {
        if (totalBoardings == 0 || boardingsPerBus.isEmpty()) {
            return 0.0;
        }

        long seatCapacity = 0;
        for (BusKey key : boardingsPerBus.keySet()) {
            int seats = getSeatCapacity(key.busNumber, key.organizationId);
            if (seats <= 0) {
                continue;
            }
            seatCapacity += seats;
        }

        if (seatCapacity <= 0) {
            return 0.0;
        }
        return (double) totalBoardings / seatCapacity;
    }

    private int getSeatCapacity(String busNumber, String organizationId) {
        if (busNumber == null || organizationId == null) {
            return 0;
        }
        String cacheKey = busNumber + ":" + organizationId;
        return busSeatCache.computeIfAbsent(cacheKey, key -> {
            try {
                return busService.getBusByNumberAndOrganization(busNumber, organizationId).getTotalSeats();
            } catch (Exception e) {
                log.debug("좌석 정보를 찾을 수 없습니다 - busNumber={}, organizationId={}", busNumber, organizationId);
                return 0;
            }
        });
    }

    private BusKey buildBusKey(String busNumber, String organizationId) {
        if (busNumber == null || organizationId == null) {
            return null;
        }
        return new BusKey(busNumber, organizationId);
    }

    private String resolveStationName(String stationId) {
        if (stationId == null) {
            return null;
        }
        Optional<Station> station = stationRepository.findById(stationId);
        return station.map(Station::getName).orElse(stationId);
    }

    private String formatHourLabel(int hour) {
        return String.format("%02d:00-%02d:00", hour, (hour + 1) % 24);
    }

    private String buildStationRecommendation(double utilizationRate, long totalBoardings, StationPeakInfoDTO busiestHour) {
        if (totalBoardings == 0) {
            return "최근 탑승 데이터가 충분하지 않습니다.";
        }
        if (utilizationRate > 1.2) {
            return String.format("이용률 %.0f%%로 극심한 혼잡. %s 시간대에 증설 권장.",
                    utilizationRate * 100, busiestHour != null ? busiestHour.getLabel() : "피크");
        }
        if (utilizationRate > 1.0) {
            return String.format("이용률 %.0f%%로 좌석 여유가 부족합니다. 배차 간격 조정 검토.",
                    utilizationRate * 100);
        }
        if (utilizationRate > 0.7) {
            return String.format("이용률 %.0f%%로 안정적입니다.", utilizationRate * 100);
        }
        return "혼잡도가 낮아 현재 용량으로 충분합니다.";
    }

    private List<String> buildNetworkRecommendations(List<StationSummaryDTO> busiest,
                                                     List<StationSummaryDTO> overcrowded) {
        List<String> recommendations = new ArrayList<>();
        if (!busiest.isEmpty()) {
            StationSummaryDTO top = busiest.get(0);
            recommendations.add(String.format("지난 기간 가장 혼잡한 정류장은 %s (%d회 탑승)입니다.",
                    top.getStationName(), top.getTotalBoardings()));
        }
        if (!overcrowded.isEmpty()) {
            overcrowded.forEach(summary -> recommendations.add(
                    String.format("%s 이용률 %.0f%% → 증설 또는 버스 추가 투입 권장",
                            summary.getStationName(), summary.getUtilizationRate() * 100)
            ));
        }
        if (recommendations.isEmpty()) {
            recommendations.add("모든 정류장이 안정적인 수준입니다.");
        }
        return recommendations;
    }

    private String buildInsightAnswer(String question,
                                      StationStatsResponseDTO stationStats,
                                      NetworkInsightResponseDTO networkStats) {
        StringBuilder answer = new StringBuilder();
        String lowerQuestion = question != null ? question.toLowerCase(Locale.ROOT) : "";

        if (stationStats != null) {
            answer.append(String.format("정류장 '%s'은 최근 %d일 동안 %d명이 탑승했고 이용률은 %.0f%%입니다. ",
                    stationStats.getStationName(),
                    stationStats.getLookbackDays(),
                    stationStats.getTotalBoardings(),
                    stationStats.getUtilizationRate() * 100));
            if (stationStats.getBusiestHour() != null) {
                answer.append(String.format("가장 혼잡한 시간대는 %s이며 평균 탑승 %d건입니다. ",
                        stationStats.getBusiestHour().getLabel(),
                        stationStats.getBusiestHour().getBoardings()));
            }
            answer.append(stationStats.getRecommendation()).append(" ");
        }

        if (networkStats != null && networkStats.getBusiestStations() != null
                && !networkStats.getBusiestStations().isEmpty()) {
            StationSummaryDTO topStation = networkStats.getBusiestStations().get(0);
            if (answer.length() == 0 || lowerQuestion.contains("네트워크") || lowerQuestion.contains("전체")) {
                answer.append(String.format("네트워크 전체 기준으로 '%s'이(가) 가장 혼잡하며 탑승 %d건, 이용률 %.0f%%입니다. ",
                        topStation.getStationName(),
                        topStation.getTotalBoardings(),
                        topStation.getUtilizationRate() * 100));
            }
        }

        if (question != null && question.contains("증설") && stationStats != null) {
            if (stationStats.getUtilizationRate() > 1.0) {
                answer.append("좌석 대비 수요가 많아 증설을 권장합니다. ");
            } else {
                answer.append("현재 용량으로도 수요를 감당하고 있습니다. ");
            }
        } else if (lowerQuestion.contains("bus") || lowerQuestion.contains("버스")) {
            if (networkStats != null && networkStats.getOvercrowdedStations() != null
                    && !networkStats.getOvercrowdedStations().isEmpty()) {
                answer.append(String.format("혼잡 정류장 수: %d. 버스 추가 투입을 고려하세요. ",
                        networkStats.getOvercrowdedStations().size()));
            } else {
                answer.append("추가 버스 투입이 필수적이지는 않습니다. ");
            }
        }

        if (answer.length() == 0) {
            answer.append("질문과 직접 매칭되는 데이터가 없어 기본 혼잡 현황을 안내했습니다. ");
        }
        return answer.toString().trim();
    }

    @Getter
    private static class BusKey {
        private final String busNumber;
        private final String organizationId;

        private BusKey(String busNumber, String organizationId) {
            this.busNumber = busNumber;
            this.organizationId = organizationId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BusKey busKey = (BusKey) o;
            return Objects.equals(busNumber, busKey.busNumber)
                    && Objects.equals(organizationId, busKey.organizationId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(busNumber, organizationId);
        }
    }

    private class StationAggregate {
        private final String stationId;
        private final String stationName;
        private long totalBoardings;
        private long totalAlightings;
        private final Map<Integer, Long> boardingsByHour = new HashMap<>();
        private final Map<BusKey, Long> boardingsPerBus = new HashMap<>();

        private StationAggregate(String stationId, String stationName) {
            this.stationId = stationId;
            this.stationName = stationName;
        }

        private void accumulate(PassengerTripEvent event) {
            if (event.getEventType() == PassengerTripEvent.EventType.BOARD) {
                totalBoardings++;
                LocalDateTime dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(event.getTimestamp()), zoneId);
                boardingsByHour.merge(dateTime.getHour(), 1L, Long::sum);
                BusKey key = buildBusKey(event.getBusNumber(), event.getOrganizationId());
                if (key != null) {
                    boardingsPerBus.merge(key, 1L, Long::sum);
                }
            } else if (event.getEventType() == PassengerTripEvent.EventType.ALIGHT) {
                totalAlightings++;
            }
        }

        private StationSummaryDTO toSummary() {
            double utilization = calculateUtilization(totalBoardings, boardingsPerBus);
            String peakHour = boardingsByHour.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .map(entry -> formatHourLabel(entry.getKey()))
                    .findFirst()
                    .orElse(null);
            String recommendation = buildStationRecommendation(utilization, totalBoardings,
                    peakHour != null ? StationPeakInfoDTO.builder()
                            .label(peakHour)
                            .boardings(boardingsByHour.entrySet().stream()
                                    .max(Map.Entry.comparingByValue())
                                    .map(Map.Entry::getValue)
                                    .orElse(0L))
                            .build() : null);
        return StationSummaryDTO.builder()
                .stationId(stationId)
                .stationName(stationName)
                .totalBoardings(totalBoardings)
                .totalAlightings(totalAlightings)
                .utilizationRate(utilization)
                .peakHourLabel(peakHour)
                .recommendation(recommendation)
                .build();
        }
    }

    private void appendStationsWithoutEvents(List<PassengerTripEvent> events,
                                             List<StationSummaryDTO> summaries,
                                             Map<String, StationAggregate> aggregates) {
        Set<String> aggregatedIds = new HashSet<>(aggregates.keySet());
        List<Station> candidateStations = resolveStationsForEvents(events);
        for (Station station : candidateStations) {
            if (station.getId() == null || aggregatedIds.contains(station.getId())) {
                continue;
            }
            summaries.add(StationSummaryDTO.builder()
                    .stationId(station.getId())
                    .stationName(station.getName())
                    .totalBoardings(0)
                    .totalAlightings(0)
                    .utilizationRate(0.0)
                    .peakHourLabel(null)
                    .recommendation("탑승 데이터가 부족합니다.")
                    .build());
        }
    }

    private List<Station> resolveStationsForEvents(List<PassengerTripEvent> events) {
        Set<String> organizationIds = events.stream()
                .map(PassengerTripEvent::getOrganizationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (organizationIds.isEmpty()) {
            return stationRepository.findAll();
        }

        if (organizationIds.size() == 1) {
            return stationRepository.findAllByOrganizationId(organizationIds.iterator().next());
        }

        return stationRepository.findAll().stream()
                .filter(station -> station.getOrganizationId() != null
                        && organizationIds.contains(station.getOrganizationId()))
                .collect(Collectors.toList());
    }

    /**
     * 필터를 적용한 네트워크 분석
     */
    public NetworkInsightResponseDTO analyzeNetworkWithFilters(NetworkAnalysisRequestDTO request) {
        // 1. 날짜 범위 계산
        DateRange dateRange = calculateDateRange(request);
        long analysisStart = dateRange.startTimestamp;
        long analysisEnd = dateRange.endTimestamp;

        // 2. 필터링된 이벤트 조회
        List<PassengerTripEvent> allEvents = passengerTripEventRepository
                .findByTimestampBetween(analysisStart, analysisEnd);

        // 3. 필터 적용
        List<PassengerTripEvent> filteredEvents = applyFilters(allEvents, request);

        // 4. 정류장별 집계
        Map<String, StationAggregate> stationAggregates = aggregateByStation(filteredEvents);
        List<StationSummaryDTO> allStationSummaries = new ArrayList<>(stationAggregates.values().stream()
                .map(StationAggregate::toSummary)
                .collect(Collectors.toList()));
        appendStationsWithoutEvents(filteredEvents, allStationSummaries, stationAggregates);
        allStationSummaries.sort(Comparator.comparingLong(StationSummaryDTO::getTotalBoardings).reversed());

        List<StationSummaryDTO> busiestStations = allStationSummaries.stream()
                .limit(5)
                .collect(Collectors.toList());

        List<StationSummaryDTO> overcrowdedStations = allStationSummaries.stream()
                .filter(summary -> summary.getUtilizationRate() > 1.1)
                .collect(Collectors.toList());

        // 5. 노선별 통계 (옵션)
        List<RouteSummaryDTO> routeSummaries = null;
        List<RouteSummaryDTO> busiestRoutes = null;
        if (Boolean.TRUE.equals(request.getIncludeRouteStats())) {
            routeSummaries = aggregateByRoute(filteredEvents, stationAggregates);
            busiestRoutes = routeSummaries.stream()
                    .sorted(Comparator.comparingLong(RouteSummaryDTO::getTotalBoardings).reversed())
                    .limit(5)
                    .collect(Collectors.toList());
        }

        // 6. 시간대별 통계 (옵션)
        List<TimeBasedStatsDTO> hourlyStats = null;
        List<TimeBasedStatsDTO> dailyStats = null;
        List<TimeBasedStatsDTO> weeklyStats = null;
        List<TimeBasedStatsDTO> monthlyStats = null;
        List<TimeBasedStatsDTO> dayOfWeekStats = null;

        if (Boolean.TRUE.equals(request.getIncludeTimeStats())) {
            String aggType = request.getAggregationType();
            if (aggType == null || aggType.equalsIgnoreCase("ALL") || aggType.equalsIgnoreCase("HOUR")) {
                hourlyStats = aggregateByHour(filteredEvents);
            }
            if (aggType == null || aggType.equalsIgnoreCase("ALL") || aggType.equalsIgnoreCase("DAY")) {
                dailyStats = aggregateByDay(filteredEvents, analysisStart, analysisEnd);
            }
            if (aggType == null || aggType.equalsIgnoreCase("ALL") || aggType.equalsIgnoreCase("WEEK")) {
                weeklyStats = aggregateByWeek(filteredEvents, analysisStart, analysisEnd);
            }
            if (aggType == null || aggType.equalsIgnoreCase("ALL") || aggType.equalsIgnoreCase("MONTH")) {
                monthlyStats = aggregateByMonth(filteredEvents, analysisStart, analysisEnd);
            }
            if (aggType == null || aggType.equalsIgnoreCase("ALL") || aggType.equalsIgnoreCase("DAY_OF_WEEK")) {
                dayOfWeekStats = aggregateByDayOfWeek(filteredEvents);
            }
        }

        // 7. 권장 사항 생성
        List<String> recommendations = buildNetworkRecommendations(busiestStations, overcrowdedStations);

        // 8. 필터 정보
        NetworkInsightResponseDTO.FilterInfoDTO appliedFilters = NetworkInsightResponseDTO.FilterInfoDTO.builder()
                .organizationId(request.getOrganizationId())
                .routeIds(request.getRouteIds())
                .stationIds(request.getStationIds())
                .aggregationType(request.getAggregationType())
                .build();

        return NetworkInsightResponseDTO.builder()
                .lookbackDays(request.getLookbackDays() != null ? request.getLookbackDays() :
                        (int) Duration.ofMillis(analysisEnd - analysisStart).toDays())
                .analysisStartTimestamp(analysisStart)
                .analysisEndTimestamp(analysisEnd)
                .startDate(dateRange.startDate)
                .endDate(dateRange.endDate)
                .busiestStations(busiestStations)
                .overcrowdedStations(overcrowdedStations)
                .allStationSummaries(allStationSummaries)
                .routeSummaries(routeSummaries)
                .busiestRoutes(busiestRoutes)
                .hourlyStats(hourlyStats)
                .dailyStats(dailyStats)
                .weeklyStats(weeklyStats)
                .monthlyStats(monthlyStats)
                .dayOfWeekStats(dayOfWeekStats)
                .recommendations(recommendations)
                .appliedFilters(appliedFilters)
                .build();
    }

    /**
     * 날짜 범위 계산
     */
    private DateRange calculateDateRange(NetworkAnalysisRequestDTO request) {
        LocalDate endDate;
        LocalDate startDate;

        if (request.getEndDate() != null && !request.getEndDate().isBlank()) {
            endDate = LocalDate.parse(request.getEndDate());
        } else {
            endDate = LocalDate.now(zoneId);
        }

        if (request.getStartDate() != null && !request.getStartDate().isBlank()) {
            startDate = LocalDate.parse(request.getStartDate());
        } else {
            int lookbackDays = request.getLookbackDays() != null ? request.getLookbackDays() : 7;
            startDate = endDate.minusDays(lookbackDays);
        }

        long startTimestamp = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long endTimestamp = endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1;

        return new DateRange(startDate.toString(), endDate.toString(), startTimestamp, endTimestamp);
    }

    /**
     * 필터 적용
     */
    private List<PassengerTripEvent> applyFilters(List<PassengerTripEvent> events, NetworkAnalysisRequestDTO request) {
        return events.stream()
                .filter(event -> {
                    // organizationId 필터
                    if (request.getOrganizationId() != null && !request.getOrganizationId().isBlank()) {
                        if (!request.getOrganizationId().equals(event.getOrganizationId())) {
                            return false;
                        }
                    }

                    // stationIds 필터
                    if (request.getStationIds() != null && !request.getStationIds().isEmpty()) {
                        if (event.getStationId() == null || !request.getStationIds().contains(event.getStationId())) {
                            return false;
                        }
                    }

                    // routeIds 필터 (정류장이 노선에 속하는지 확인)
                    if (request.getRouteIds() != null && !request.getRouteIds().isEmpty()) {
                        if (event.getStationId() == null) {
                            return false;
                        }
                        String routeId = findRouteIdForStation(event.getStationId(), request.getRouteIds());
                        if (routeId == null) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * 정류장별 집계
     */
    private Map<String, StationAggregate> aggregateByStation(List<PassengerTripEvent> events) {
        Map<String, StationAggregate> aggregates = new HashMap<>();
        for (PassengerTripEvent event : events) {
            if (event.getStationId() == null) {
                continue;
            }
            StationAggregate aggregate = aggregates.computeIfAbsent(event.getStationId(),
                    id -> new StationAggregate(id, resolveStationName(id)));
            aggregate.accumulate(event);
        }
        return aggregates;
    }

    /**
     * 노선별 집계
     */
    private List<RouteSummaryDTO> aggregateByRoute(List<PassengerTripEvent> events,
                                                     Map<String, StationAggregate> stationAggregates) {
        Map<String, RouteAggregate> routeAggregates = new HashMap<>();

        // 모든 노선 조회
        List<capston2024.bustracker.domain.Route> allRoutes = routeRepository.findAll();
        Map<String, capston2024.bustracker.domain.Route> routeMap = allRoutes.stream()
                .collect(Collectors.toMap(capston2024.bustracker.domain.Route::getId, r -> r));

        // 이벤트를 노선별로 그룹화
        for (PassengerTripEvent event : events) {
            if (event.getStationId() == null) {
                continue;
            }

            // 정류장이 속한 노선 찾기
            List<capston2024.bustracker.domain.Route> routes = allRoutes.stream()
                    .filter(route -> route.getStations() != null && route.getStations().stream()
                            .anyMatch(rs -> rs.getStationId() != null &&
                                    event.getStationId().equals(rs.getStationId().getId())))
                    .collect(Collectors.toList());

            for (capston2024.bustracker.domain.Route route : routes) {
                RouteAggregate aggregate = routeAggregates.computeIfAbsent(route.getId(),
                        id -> new RouteAggregate(id, route.getRouteName()));
                aggregate.accumulate(event);
            }
        }

        // DTO로 변환
        return routeAggregates.values().stream()
                .map(agg -> {
                    capston2024.bustracker.domain.Route route = routeMap.get(agg.routeId);
                    List<StationSummaryDTO> topStations = new ArrayList<>();

                    if (route != null && route.getStations() != null) {
                        topStations = route.getStations().stream()
                                .filter(rs -> rs.getStationId() != null)
                                .map(rs -> stationAggregates.get(rs.getStationId().getId()))
                                .filter(Objects::nonNull)
                                .map(StationAggregate::toSummary)
                                .sorted(Comparator.comparingLong(StationSummaryDTO::getTotalBoardings).reversed())
                                .limit(5)
                                .collect(Collectors.toList());
                    }

                    double utilization = calculateUtilization(agg.totalBoardings, agg.boardingsPerBus);
                    String peakHour = agg.boardingsByHour.entrySet().stream()
                            .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                            .map(entry -> formatHourLabel(entry.getKey()))
                            .findFirst()
                            .orElse(null);

                    return RouteSummaryDTO.builder()
                            .routeId(agg.routeId)
                            .routeName(agg.routeName)
                            .totalBoardings(agg.totalBoardings)
                            .totalAlightings(agg.totalAlightings)
                            .utilizationRate(utilization)
                            .peakHourLabel(peakHour)
                            .topStations(topStations)
                            .recommendation(buildRouteRecommendation(utilization, agg.totalBoardings))
                            .build();
                })
                .sorted(Comparator.comparingLong(RouteSummaryDTO::getTotalBoardings).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 시간대별 집계 (0-23시)
     */
    private List<TimeBasedStatsDTO> aggregateByHour(List<PassengerTripEvent> events) {
        Map<Integer, TimeAggregateData> hourlyData = new HashMap<>();

        for (PassengerTripEvent event : events) {
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(event.getTimestamp()), zoneId);
            int hour = dateTime.getHour();

            TimeAggregateData data = hourlyData.computeIfAbsent(hour, h -> new TimeAggregateData());
            if (event.getEventType() == PassengerTripEvent.EventType.BOARD) {
                data.totalBoardings++;
                BusKey key = buildBusKey(event.getBusNumber(), event.getOrganizationId());
                if (key != null) {
                    data.boardingsPerBus.merge(key, 1L, Long::sum);
                }
            } else if (event.getEventType() == PassengerTripEvent.EventType.ALIGHT) {
                data.totalAlightings++;
            }
        }

        return hourlyData.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    int hour = entry.getKey();
                    TimeAggregateData data = entry.getValue();
                    return TimeBasedStatsDTO.builder()
                            .label(formatHourLabel(hour))
                            .timestamp((long) hour)
                            .totalBoardings(data.totalBoardings)
                            .totalAlightings(data.totalAlightings)
                            .netPassengers(data.totalBoardings - data.totalAlightings)
                            .utilizationRate(calculateUtilization(data.totalBoardings, data.boardingsPerBus))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 일별 집계
     */
    private List<TimeBasedStatsDTO> aggregateByDay(List<PassengerTripEvent> events, long startTimestamp, long endTimestamp) {
        Map<LocalDate, TimeAggregateData> dailyData = new HashMap<>();

        for (PassengerTripEvent event : events) {
            LocalDate date = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(event.getTimestamp()), zoneId).toLocalDate();

            TimeAggregateData data = dailyData.computeIfAbsent(date, d -> new TimeAggregateData());
            if (event.getEventType() == PassengerTripEvent.EventType.BOARD) {
                data.totalBoardings++;
                BusKey key = buildBusKey(event.getBusNumber(), event.getOrganizationId());
                if (key != null) {
                    data.boardingsPerBus.merge(key, 1L, Long::sum);
                }
            } else if (event.getEventType() == PassengerTripEvent.EventType.ALIGHT) {
                data.totalAlightings++;
            }
        }

        return dailyData.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    TimeAggregateData data = entry.getValue();
                    return TimeBasedStatsDTO.builder()
                            .label(date.toString())
                            .timestamp(date.atStartOfDay(zoneId).toInstant().toEpochMilli())
                            .totalBoardings(data.totalBoardings)
                            .totalAlightings(data.totalAlightings)
                            .netPassengers(data.totalBoardings - data.totalAlightings)
                            .utilizationRate(calculateUtilization(data.totalBoardings, data.boardingsPerBus))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 주차별 집계
     */
    private List<TimeBasedStatsDTO> aggregateByWeek(List<PassengerTripEvent> events, long startTimestamp, long endTimestamp) {
        Map<String, TimeAggregateData> weeklyData = new HashMap<>();

        for (PassengerTripEvent event : events) {
            LocalDate date = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(event.getTimestamp()), zoneId).toLocalDate();

            // ISO 주차 계산 (년도-주차)
            int year = date.getYear();
            int weekOfYear = getWeekOfYear(date);
            String weekKey = String.format("%d-W%02d", year, weekOfYear);

            TimeAggregateData data = weeklyData.computeIfAbsent(weekKey, w -> new TimeAggregateData());
            if (event.getEventType() == PassengerTripEvent.EventType.BOARD) {
                data.totalBoardings++;
                BusKey key = buildBusKey(event.getBusNumber(), event.getOrganizationId());
                if (key != null) {
                    data.boardingsPerBus.merge(key, 1L, Long::sum);
                }
            } else if (event.getEventType() == PassengerTripEvent.EventType.ALIGHT) {
                data.totalAlightings++;
            }
        }

        return weeklyData.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String weekKey = entry.getKey();
                    TimeAggregateData data = entry.getValue();
                    return TimeBasedStatsDTO.builder()
                            .label(weekKey)
                            .timestamp(null)
                            .totalBoardings(data.totalBoardings)
                            .totalAlightings(data.totalAlightings)
                            .netPassengers(data.totalBoardings - data.totalAlightings)
                            .utilizationRate(calculateUtilization(data.totalBoardings, data.boardingsPerBus))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 월별 집계
     */
    private List<TimeBasedStatsDTO> aggregateByMonth(List<PassengerTripEvent> events, long startTimestamp, long endTimestamp) {
        Map<String, TimeAggregateData> monthlyData = new HashMap<>();

        for (PassengerTripEvent event : events) {
            LocalDate date = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(event.getTimestamp()), zoneId).toLocalDate();

            String monthKey = String.format("%d-%02d", date.getYear(), date.getMonthValue());

            TimeAggregateData data = monthlyData.computeIfAbsent(monthKey, m -> new TimeAggregateData());
            if (event.getEventType() == PassengerTripEvent.EventType.BOARD) {
                data.totalBoardings++;
                BusKey key = buildBusKey(event.getBusNumber(), event.getOrganizationId());
                if (key != null) {
                    data.boardingsPerBus.merge(key, 1L, Long::sum);
                }
            } else if (event.getEventType() == PassengerTripEvent.EventType.ALIGHT) {
                data.totalAlightings++;
            }
        }

        return monthlyData.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String monthKey = entry.getKey();
                    TimeAggregateData data = entry.getValue();
                    return TimeBasedStatsDTO.builder()
                            .label(monthKey)
                            .timestamp(null)
                            .totalBoardings(data.totalBoardings)
                            .totalAlightings(data.totalAlightings)
                            .netPassengers(data.totalBoardings - data.totalAlightings)
                            .utilizationRate(calculateUtilization(data.totalBoardings, data.boardingsPerBus))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 요일별 집계
     */
    private List<TimeBasedStatsDTO> aggregateByDayOfWeek(List<PassengerTripEvent> events) {
        Map<DayOfWeek, TimeAggregateData> dayOfWeekData = new HashMap<>();

        for (PassengerTripEvent event : events) {
            DayOfWeek dayOfWeek = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(event.getTimestamp()), zoneId).getDayOfWeek();

            TimeAggregateData data = dayOfWeekData.computeIfAbsent(dayOfWeek, d -> new TimeAggregateData());
            if (event.getEventType() == PassengerTripEvent.EventType.BOARD) {
                data.totalBoardings++;
                BusKey key = buildBusKey(event.getBusNumber(), event.getOrganizationId());
                if (key != null) {
                    data.boardingsPerBus.merge(key, 1L, Long::sum);
                }
            } else if (event.getEventType() == PassengerTripEvent.EventType.ALIGHT) {
                data.totalAlightings++;
            }
        }

        return dayOfWeekData.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    DayOfWeek dayOfWeek = entry.getKey();
                    TimeAggregateData data = entry.getValue();
                    return TimeBasedStatsDTO.builder()
                            .label(dayOfWeek.name())
                            .timestamp((long) dayOfWeek.getValue())
                            .totalBoardings(data.totalBoardings)
                            .totalAlightings(data.totalAlightings)
                            .netPassengers(data.totalBoardings - data.totalAlightings)
                            .utilizationRate(calculateUtilization(data.totalBoardings, data.boardingsPerBus))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 정류장이 속한 노선 ID 찾기 (캐시 사용)
     */
    private String findRouteIdForStation(String stationId, List<String> routeIds) {
        String cacheKey = stationId + ":" + String.join(",", routeIds);
        return stationToRouteCache.computeIfAbsent(cacheKey, key -> {
            for (String routeId : routeIds) {
                Optional<capston2024.bustracker.domain.Route> route = routeRepository.findById(routeId);
                if (route.isPresent() && route.get().getStations() != null) {
                    boolean hasStation = route.get().getStations().stream()
                            .anyMatch(rs -> rs.getStationId() != null && stationId.equals(rs.getStationId().getId()));
                    if (hasStation) {
                        return routeId;
                    }
                }
            }
            return null;
        });
    }

    /**
     * 노선에 대한 권장 사항 생성
     */
    private String buildRouteRecommendation(double utilizationRate, long totalBoardings) {
        if (totalBoardings == 0) {
            return "최근 탑승 데이터가 부족합니다.";
        }
        if (utilizationRate > 1.2) {
            return String.format("이용률 %.0f%%로 극심한 혼잡. 노선 증설 또는 배차 간격 단축 권장.", utilizationRate * 100);
        }
        if (utilizationRate > 1.0) {
            return String.format("이용률 %.0f%%로 좌석 여유가 부족합니다. 배차 간격 조정 검토.", utilizationRate * 100);
        }
        if (utilizationRate > 0.7) {
            return String.format("이용률 %.0f%%로 안정적입니다.", utilizationRate * 100);
        }
        return "혼잡도가 낮아 현재 용량으로 충분합니다.";
    }

    /**
     * 주차 계산 (ISO 8601 기준)
     */
    private int getWeekOfYear(LocalDate date) {
        java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.ISO;
        return date.get(weekFields.weekOfWeekBasedYear());
    }

    // 헬퍼 클래스들
    private static class DateRange {
        final String startDate;
        final String endDate;
        final long startTimestamp;
        final long endTimestamp;

        DateRange(String startDate, String endDate, long startTimestamp, long endTimestamp) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.startTimestamp = startTimestamp;
            this.endTimestamp = endTimestamp;
        }
    }

    private static class RouteAggregate {
        final String routeId;
        final String routeName;
        long totalBoardings;
        long totalAlightings;
        final Map<Integer, Long> boardingsByHour = new HashMap<>();
        final Map<BusKey, Long> boardingsPerBus = new HashMap<>();

        RouteAggregate(String routeId, String routeName) {
            this.routeId = routeId;
            this.routeName = routeName;
        }

        void accumulate(PassengerTripEvent event) {
            if (event.getEventType() == PassengerTripEvent.EventType.BOARD) {
                totalBoardings++;
                LocalDateTime dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(event.getTimestamp()), ZoneId.systemDefault());
                boardingsByHour.merge(dateTime.getHour(), 1L, Long::sum);
                String busNumber = event.getBusNumber();
                String orgId = event.getOrganizationId();
                if (busNumber != null && orgId != null) {
                    boardingsPerBus.merge(new BusKey(busNumber, orgId), 1L, Long::sum);
                }
            } else if (event.getEventType() == PassengerTripEvent.EventType.ALIGHT) {
                totalAlightings++;
            }
        }
    }

    private static class TimeAggregateData {
        long totalBoardings;
        long totalAlightings;
        final Map<BusKey, Long> boardingsPerBus = new HashMap<>();
    }
}
