package org.kore.myapp.core.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kore.myapp.core.exception.InvalidTripException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateTripCommandTest {

    private static final Location MONAS = new Location(new Coordinate(-6.1754, 106.8272), "Monas");
    private static final Location SENAYAN = new Location(new Coordinate(-6.2183, 106.8022), "Senayan");

    @Test
    void keepsItsComponentsWhenValid() {
        CreateTripCommand command = new CreateTripCommand(42, "MyRide", MONAS, SENAYAN, PaymentMethod.CASH);

        assertThat(command.customerId()).isEqualTo(42);
        assertThat(command.serviceTypeName()).isEqualTo("MyRide");
        assertThat(command.pickup()).isEqualTo(MONAS);
        assertThat(command.destination()).isEqualTo(SENAYAN);
        assertThat(command.paymentMethod()).isEqualTo(PaymentMethod.CASH);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveCustomerId(long customerId) {
        assertThatThrownBy(() -> new CreateTripCommand(customerId, "MyRide", MONAS, SENAYAN, PaymentMethod.CASH))
                .isInstanceOf(InvalidTripException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsMissingServiceTypeName(String name) {
        assertThatThrownBy(() -> new CreateTripCommand(42, name, MONAS, SENAYAN, PaymentMethod.CASH))
                .isInstanceOf(InvalidTripException.class);
    }

    @Test
    void rejectsMissingPickup() {
        assertThatThrownBy(() -> new CreateTripCommand(42, "MyRide", null, SENAYAN, PaymentMethod.CASH))
                .isInstanceOf(InvalidTripException.class);
    }

    @Test
    void rejectsMissingDestination() {
        assertThatThrownBy(() -> new CreateTripCommand(42, "MyRide", MONAS, null, PaymentMethod.CASH))
                .isInstanceOf(InvalidTripException.class);
    }

    @Test
    void rejectsMissingPaymentMethod() {
        assertThatThrownBy(() -> new CreateTripCommand(42, "MyRide", MONAS, SENAYAN, null))
                .isInstanceOf(InvalidTripException.class);
    }
}
