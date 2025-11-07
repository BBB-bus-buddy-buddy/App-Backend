package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.*;
import capston2024.bustracker.domain.*;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.*;
import com.mongodb.DBRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 이벤트 서비스
 * CoShow 부스 이벤트와 같은 프로모션 이벤트 관리
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMissionRepository eventMissionRepository;
    private final EventRewardRepository eventRewardRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final UserRepository userRepository;

    /**
     * 현재 활성화된 이벤트 조회
     */
    public EventDTO getCurrentEvent(String organizationId) {
        Event event = eventRepository.findFirstByOrganizationIdAndIsActiveTrueOrderByCreatedAtDesc(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("현재 진행 중인 이벤트가 없습니다."));

        return convertToDTO(event);
    }

    /**
     * 이벤트 미션 목록 조회
     */
    public List<EventMissionDTO> getEventMissions(String eventId, String userId) {
        // 이벤트 존재 확인
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("이벤트를 찾을 수 없습니다."));

        // 미션 목록 조회
        List<EventMission> missions = eventMissionRepository.findByEventIdOrderByOrder(eventId);

        // 사용자 참여 기록 조회
        Optional<EventParticipation> participationOpt =
            eventParticipationRepository.findByEventIdAndUserId(eventId, userId);

        Set<String> completedMissionIds = participationOpt
                .map(EventParticipation::getCompletedMissions)
                .map(HashSet::new)
                .orElse(new HashSet<>());

        // DTO 변환
        return missions.stream()
                .map(mission -> {
                    EventMissionDTO dto = convertMissionToDTO(mission);
                    dto.setCompleted(completedMissionIds.contains(mission.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 이벤트 상품 목록 조회
     */
    public List<EventRewardDTO> getEventRewards(String eventId) {
        List<EventReward> rewards = eventRewardRepository.findByEventIdOrderByRewardGrade(eventId);
        return rewards.stream()
                .map(this::convertRewardToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 미션 완료 처리
     */
    @Transactional
    public EventParticipationDTO completeMission(String userId, MissionCompleteRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));

        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("이벤트를 찾을 수 없습니다."));

        EventMission mission = eventMissionRepository.findById(request.getMissionId())
                .orElseThrow(() -> new ResourceNotFoundException("미션을 찾을 수 없습니다."));

        // 미션 검증
        validateMission(mission, request.getTargetValue());

        // 참여 기록 조회 또는 생성
        EventParticipation participation = eventParticipationRepository
                .findByEventIdAndUserId(request.getEventId(), userId)
                .orElseGet(() -> {
                    EventParticipation newParticipation = EventParticipation.builder()
                            .eventId(new DBRef("events", event.getId()))
                            .userId(new DBRef("Auth", user.getId()))
                            .completedMissions(new ArrayList<>())
                            .isEligibleForDraw(false)
                            .hasDrawn(false)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return eventParticipationRepository.save(newParticipation);
                });

        // 미션 완료 추가
        participation.addCompletedMission(mission.getId());
        participation.setUpdatedAt(LocalDateTime.now());

        // 모든 필수 미션 완료 여부 확인
        List<EventMission> requiredMissions = eventMissionRepository.findRequiredMissionsByEventId(event.getId());
        EventParticipation finalParticipation = participation;
        boolean allRequiredCompleted = requiredMissions.stream()
                .allMatch(m -> finalParticipation.getCompletedMissions().contains(m.getId()));

        participation.setEligibleForDraw(allRequiredCompleted);

        participation = eventParticipationRepository.save(participation);

        log.info("미션 완료: userId={}, missionId={}, eligibleForDraw={}",
                userId, mission.getId(), participation.isEligibleForDraw());

        return convertParticipationToDTO(participation);
    }

    /**
     * 랜덤 뽑기 실행
     */
    @Transactional
    public RewardDrawResponseDTO drawReward(String userId, String eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("이벤트를 찾을 수 없습니다."));

        // 참여 기록 확인
        EventParticipation participation = eventParticipationRepository
                .findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BusinessException("이벤트에 참여하지 않았습니다."));

        // 뽑기 자격 확인
        if (!participation.isEligibleForDraw()) {
            throw new BusinessException("뽑기 자격이 없습니다. 모든 필수 미션을 완료해주세요.");
        }

        // 중복 뽑기 방지
        if (participation.hasDrawn()) {
            throw new BusinessException("이미 뽑기를 완료하였습니다.");
        }

        // 재고가 있는 상품 목록 조회
        List<EventReward> availableRewards = eventRewardRepository.findAvailableRewardsByEventId(eventId);

        if (availableRewards.isEmpty()) {
            throw new BusinessException("남은 상품이 없습니다.");
        }

        // 확률 기반 랜덤 추첨
        EventReward drawnReward = performRandomDraw(availableRewards);

        // 재고 감소
        drawnReward.setRemainingQuantity(drawnReward.getRemainingQuantity() - 1);
        drawnReward.setUpdatedAt(LocalDateTime.now());
        eventRewardRepository.save(drawnReward);

        // 참여 기록 업데이트
        participation.markAsDrawn(new DBRef("event_rewards", drawnReward.getId()));
        participation.setUpdatedAt(LocalDateTime.now());
        eventParticipationRepository.save(participation);

        log.info("뽑기 완료: userId={}, reward={} ({}등)",
                userId, drawnReward.getRewardName(), drawnReward.getRewardGrade());

        return RewardDrawResponseDTO.builder()
                .success(true)
                .reward(convertRewardToDTO(drawnReward))
                .message("축하합니다! " + drawnReward.getRewardGrade() + "등 당첨!")
                .build();
    }

    /**
     * 내 참여 현황 조회
     */
    public EventParticipationDTO getMyParticipation(String userId, String eventId) {
        EventParticipation participation = eventParticipationRepository
                .findByEventIdAndUserId(eventId, userId)
                .orElse(null);

        if (participation == null) {
            // 참여하지 않은 경우 빈 객체 반환
            return EventParticipationDTO.builder()
                    .eventId(eventId)
                    .userId(userId)
                    .completedMissions(new ArrayList<>())
                    .isEligibleForDraw(false)
                    .hasDrawn(false)
                    .build();
        }

        return convertParticipationToDTO(participation);
    }

    /**
     * 미션 검증
     */
    private void validateMission(EventMission mission, String targetValue) {
        // targetValue가 미션의 조건과 일치하는지 확인
        if (mission.getTargetValue() != null && !mission.getTargetValue().equals(targetValue)) {
            throw new BusinessException("미션 조건이 일치하지 않습니다.");
        }
        // 추가 검증 로직은 미션 타입에 따라 확장 가능
    }

    /**
     * 확률 기반 랜덤 추첨
     * 1등 5%, 2등 10%, 3등 15%, 4등 20%, 5등 50%
     */
    private EventReward performRandomDraw(List<EventReward> rewards) {
        // 확률 누적 합계 계산
        double totalProbability = rewards.stream()
                .mapToDouble(EventReward::getProbability)
                .sum();

        // 랜덤 값 생성 (0.0 ~ totalProbability)
        double randomValue = Math.random() * totalProbability;

        // 누적 확률로 상품 선택
        double cumulativeProbability = 0.0;
        for (EventReward reward : rewards) {
            cumulativeProbability += reward.getProbability();
            if (randomValue <= cumulativeProbability) {
                return reward;
            }
        }

        // 만약 여기까지 오면 마지막 상품 반환 (fallback)
        return rewards.get(rewards.size() - 1);
    }

    /**
     * Entity -> DTO 변환
     */
    private EventDTO convertToDTO(Event event) {
        List<EventMissionDTO> missions = eventMissionRepository
                .findByEventIdOrderByOrder(event.getId())
                .stream()
                .map(this::convertMissionToDTO)
                .collect(Collectors.toList());

        List<EventRewardDTO> rewards = eventRewardRepository
                .findByEventIdOrderByRewardGrade(event.getId())
                .stream()
                .map(this::convertRewardToDTO)
                .collect(Collectors.toList());

        return EventDTO.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .isActive(event.isActive())
                .organizationId(event.getOrganizationId())
                .missions(missions)
                .rewards(rewards)
                .createdAt(event.getCreatedAt())
                .build();
    }

    private EventMissionDTO convertMissionToDTO(EventMission mission) {
        return EventMissionDTO.builder()
                .id(mission.getId())
                .eventId(mission.getEventId() != null ? (String) mission.getEventId().getId() : null)
                .title(mission.getTitle())
                .description(mission.getDescription())
                .missionType(mission.getMissionType())
                .targetValue(mission.getTargetValue())
                .isRequired(mission.isRequired())
                .order(mission.getOrder())
                .isCompleted(false) // 기본값, 호출하는 쪽에서 설정
                .build();
    }

    private EventRewardDTO convertRewardToDTO(EventReward reward) {
        return EventRewardDTO.builder()
                .id(reward.getId())
                .eventId(reward.getEventId() != null ? (String) reward.getEventId().getId() : null)
                .rewardName(reward.getRewardName())
                .rewardGrade(reward.getRewardGrade())
                .probability(reward.getProbability())
                .totalQuantity(reward.getTotalQuantity())
                .remainingQuantity(reward.getRemainingQuantity())
                .imageUrl(reward.getImageUrl())
                .description(reward.getDescription())
                .build();
    }

    private EventParticipationDTO convertParticipationToDTO(EventParticipation participation) {
        EventRewardDTO rewardDTO = null;
        if (participation.getDrawnRewardId() != null) {
            EventReward reward = eventRewardRepository.findById((String) participation.getDrawnRewardId().getId())
                    .orElse(null);
            if (reward != null) {
                rewardDTO = convertRewardToDTO(reward);
            }
        }

        return EventParticipationDTO.builder()
                .id(participation.getId())
                .eventId(participation.getEventId() != null ? (String) participation.getEventId().getId() : null)
                .userId(participation.getUserId() != null ? (String) participation.getUserId().getId() : null)
                .completedMissions(participation.getCompletedMissions())
                .isEligibleForDraw(participation.isEligibleForDraw())
                .hasDrawn(participation.hasDrawn())
                .drawnReward(rewardDTO)
                .drawTimestamp(participation.getDrawTimestamp())
                .build();
    }
}
