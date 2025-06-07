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

    // Location information
    private Location location;

    // Personal information (RSA 암호화 필요)
    private String identity; // 주민등록번호
    private String birthDate;
    private String phoneNumber;

    // License information (RSA 암호화 필요)
    private String licenseNumber;
    private String licenseSerial;
    private String licenseType;
    private String licenseExpiryDate;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private double[] coordinates; // [경도, 위도]

        public double getLongitude() {
            return coordinates != null && coordinates.length > 0 ? coordinates[0] : 0.0;
        }

        public double getLatitude() {
            return coordinates != null && coordinates.length > 1 ? coordinates[1] : 0.0;
        }

        public void setCoordinates(double longitude, double latitude) {
            this.coordinates = new double[]{longitude, latitude};
        }
    }
}