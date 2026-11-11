package org.kore.myapp.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.kore.myapp.core.valueobject.PaymentMethod;

public record CreateTripRequest(
        @NotBlank(message = "serviceType is required")
        String serviceType,

        @NotNull @Valid
        LocationRequest pickup,

        @NotNull @Valid
        LocationRequest destination,

        @NotNull
        PaymentMethod paymentMethod
) {
}
