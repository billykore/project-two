package org.kore.myapp.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.kore.myapp.core.valueobject.Coordinate;
import org.kore.myapp.core.valueobject.Location;

public record LocationRequest(
        @Schema(example = "-6.2088")
        @DecimalMin("-90.0") @DecimalMax("90.0")
        double latitude,

        @Schema(example = "106.8456")
        @DecimalMin("-180.0") @DecimalMax("180.0")
        double longitude,

        @Schema(example = "Monas")
        @Size(max = 255)
        String address
) {
    public Location toLocation() {
        return new Location(new Coordinate(latitude, longitude), address);
    }
}
