package org.kore.myapp.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.kore.myapp.core.valueobject.PaymentMethod;
import org.kore.myapp.core.valueobject.ServiceType;

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
    public static final String MY_RIDE = "MyRide";

    public static final String MY_CAR = "MyCar";

    public ServiceType serviceTypeId() {
        return switch (serviceType) {
            case MY_RIDE -> ServiceType.MY_RIDE;
            case MY_CAR -> ServiceType.MY_CAR;
            default -> ServiceType.UNKNOWN;
        };
    }
}
