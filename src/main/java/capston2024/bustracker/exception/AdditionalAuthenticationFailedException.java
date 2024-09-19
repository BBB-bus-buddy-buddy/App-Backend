package capston2024.bustracker.exception;

import jakarta.validation.constraints.NotNull;

public class AdditionalAuthenticationFailedException extends Throwable {
    public AdditionalAuthenticationFailedException(@NotNull String s) {
    }
}
