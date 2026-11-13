package org.kore.myapp.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.kore.myapp.core.valueobject.PaymentMethod;

public record CreateTripRequest(
        @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = {"MyRide", "MyCar"}, example = "MyRide",
                description = "Must be exactly `MyRide` or `MyCar`.")
        @NotBlank(message = "serviceType is required")
        String serviceType,

        @Schema(requiredMode = RequiredMode.REQUIRED)
        @NotNull @Valid
        LocationRequest pickup,

        @Schema(requiredMode = RequiredMode.REQUIRED)
        @NotNull @Valid
        LocationRequest destination,

        @Schema(requiredMode = RequiredMode.REQUIRED, example = "QRIS")
        @NotNull
        PaymentMethod paymentMethod
) {
}
