package capston2024.bustracker.repository;

import capston2024.bustracker.domain.AuthDomain;
import capston2024.bustracker.domain.BusStopCoordinateDomain;

import java.util.*;

public class MemoryAuthRepository implements AuthRepository{

    private static Map<Long, AuthDomain> store = new HashMap<>();
    private static long sequence = 0L;

    @Override
    public AuthDomain addMember(AuthDomain member) {
        member.setId(++sequence);
        store.put(member.getId(), member);
        return member;
    }

    @Override
    public boolean isVaildInVerifyCode(AuthDomain member, String code) {
        // 이 자리에 코드 유효성 검사 관련 로직
        // 만약 유효성 검사에 인증되면
        if(store.containsKey(member.getId())){
            store.get(member.getId()).setValid(true);
            store.get(member.getId()).setSchoolCode(code);
            return true;
        }
        return false;
    }

    @Override
    public Optional<AuthDomain> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<AuthDomain> findAll() {
        return new ArrayList<>(store.values());
    }
}
