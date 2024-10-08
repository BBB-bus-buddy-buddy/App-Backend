package capston2024.bustracker.exception;

import lombok.Getter;

@Getter
public class TokenException extends RuntimeException {
    private final ErrorCode errorCode;

    public TokenException(ErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

}