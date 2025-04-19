package capston2024.bustracker.domain.auth;

import capston2024.bustracker.config.status.Role;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.domain.User;
import com.mongodb.DBRef;
import org.checkerframework.checker.units.qual.A;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.KeyGenerator;

/**
 *  처음 가입 시 엔티티 부여
 */
public class UserCreator {

    public static User createUserFrom(OAuthAttributes attributes) {
        List<DBRef> list = new ArrayList<>();
        return User.builder()
                .name(attributes.getName())
                .email(attributes.getEmail())
                .myStations(list)
                .role(Role.GUEST)
                .build();
    }
}