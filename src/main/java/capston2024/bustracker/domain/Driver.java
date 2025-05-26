package capston2024.bustracker.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "Auth")
public class Driver {
    @Id
    private String id;

    private String name;
    private String email;
    private String role = "DRIVER";
    private String organizationId;

    // Personal information
    private String identity; // 주민등록번호
    private String birthDate;
    private String phoneNumber;

    // License information
    private String licenseNumber;
    private String licenseSerial;
    private String licenseType;
    private String licenseExpiryDate;
}