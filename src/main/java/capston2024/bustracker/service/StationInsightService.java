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
    private final BusService busService;
    private final AiInsightService aiInsightService;
    private final ZoneId zoneId = ZoneId.systemDefault();
    private final Map<String, Integer> busSeatCache = new ConcurrentHashMap<>();

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
}
