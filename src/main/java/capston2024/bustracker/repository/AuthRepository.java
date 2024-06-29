package capston2024.bustracker.repository;

import capston2024.bustracker.domain.AuthDomain;
import capston2024.bustracker.domain.BusStopCoordinateDomain;

import java.util.List;
import java.util.Optional;

public interface AuthRepository { //구글 로그인
    AuthDomain addMember(AuthDomain member);
    boolean isVaildInVerifyCode(AuthDomain member, String code);
    Optional<AuthDomain> findById(Long id);
    List<AuthDomain> findAll();
}
