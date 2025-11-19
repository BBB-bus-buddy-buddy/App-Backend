package capston2024.bustracker.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Apple Sign In으로부터 받은 인증 정보를 담는 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Apple 로그인 요청 데이터")
public class AppleLoginRequestDTO {

    @Schema(description = "Apple에서 발급한 Identity Token", example = "eyJraWQiOiJXNldjT0tC...")
    private String identityToken;

    @Schema(description = "Apple 사용자 고유 ID", example = "001234.abcdef123456789.1234")
    private String userId;

    @Schema(description = "사용자 이름 (선택사항)", example = "홍길동")
    private String name;

    @Schema(description = "사용자 이메일 (선택사항)", example = "user@privaterelay.appleid.com")
    private String email;
}
