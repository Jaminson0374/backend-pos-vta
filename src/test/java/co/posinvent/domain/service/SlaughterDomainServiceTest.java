package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.Animal;
import co.posinvent.domain.model.Animal.AnimalStatus;
import co.posinvent.domain.model.Animal.Species;
import co.posinvent.domain.model.Slaughter.SlaughterSourceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlaughterDomainServiceTest {

    private final SlaughterDomainService service = new SlaughterDomainService();

    @Test
    void validate_rejectsAlreadySlaughteredAnimal() {
        var animal = animal(AnimalStatus.SLAUGHTERED, "100");

        assertThatThrownBy(() -> service.validate(
                animal, new BigDecimal("60"), new BigDecimal("100"),
                SlaughterSourceType.MANUAL, "Justificación válida"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ANIMAL_ALREADY_SLAUGHTERED");
    }

    @Test
    void validate_rejectsAutomaticSourceType() {
        var animal = animal(AnimalStatus.RECEIVED, "100");

        assertThatThrownBy(() -> service.validate(
                animal, new BigDecimal("60"), new BigDecimal("100"),
                SlaughterSourceType.AUTOMATIC, "Justificación válida"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AUTOMATIC_SLAUGHTER_NOT_SUPPORTED");
    }

    @Test
    void validate_rejectsNullJustificationForManual() {
        var animal = animal(AnimalStatus.RECEIVED, "100");

        assertThatThrownBy(() -> service.validate(
                animal, new BigDecimal("60"), new BigDecimal("100"),
                SlaughterSourceType.MANUAL, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "MANUAL_JUSTIFICATION_REQUIRED");
    }

    @Test
    void validate_rejectsBlankJustificationForManual() {
        var animal = animal(AnimalStatus.RECEIVED, "100");

        assertThatThrownBy(() -> service.validate(
                animal, new BigDecimal("60"), new BigDecimal("100"),
                SlaughterSourceType.MANUAL, "   "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "MANUAL_JUSTIFICATION_REQUIRED");
    }

    @Test
    void validate_rejectsCarcassHeavierThanLiveWeight() {
        var animal = animal(AnimalStatus.RECEIVED, "100");

        assertThatThrownBy(() -> service.validate(
                animal, new BigDecimal("120"), new BigDecimal("100"),
                SlaughterSourceType.MANUAL, "Justificación válida"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CARCASS_EXCEEDS_LIVE_WEIGHT");
    }

    @Test
    void validate_rejectsNonPositiveCarcassWeight() {
        var animal = animal(AnimalStatus.RECEIVED, "100");

        assertThatThrownBy(() -> service.validate(
                animal, BigDecimal.ZERO, new BigDecimal("100"),
                SlaughterSourceType.MANUAL, "Justificación válida"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_SLAUGHTER_VALUE");
    }

    @Test
    void validate_rejectsNonPositivePurchaseCost() {
        var animal = animal(AnimalStatus.RECEIVED, "100");

        assertThatThrownBy(() -> service.validate(
                animal, new BigDecimal("60"), BigDecimal.ZERO,
                SlaughterSourceType.MANUAL, "Justificación válida"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_SLAUGHTER_VALUE");
    }

    @Test
    void validate_returnsYieldRoundedToTwoDecimals() {
        var animal = animal(AnimalStatus.RECEIVED, "80");

        var yield = service.validate(
                animal, new BigDecimal("65"), new BigDecimal("1000"),
                SlaughterSourceType.MANUAL, "Justificación válida");

        // 65 / 80 * 100 = 81.25
        assertThat(yield).isEqualByComparingTo("81.25");
        assertThat(yield.scale()).isEqualTo(2);
    }

    @Test
    void validate_computesExactYieldForFullCarcass() {
        var animal = animal(AnimalStatus.RECEIVED, "100");

        var yield = service.validate(
                animal, new BigDecimal("78"), new BigDecimal("1000"),
                SlaughterSourceType.MANUAL, "Justificación válida");

        assertThat(yield).isEqualByComparingTo("78.00");
    }

    private Animal animal(AnimalStatus status, String liveWeight) {
        return new Animal(
                UUID.randomUUID(),
                "ICA-" + UUID.randomUUID(),
                UUID.randomUUID(),
                Species.PORCINO,
                new BigDecimal(liveWeight),
                LocalDate.now().minusDays(1),
                status,
                null,
                UUID.randomUUID(),
                OffsetDateTime.now().minusDays(1),
                null
        );
    }
}
