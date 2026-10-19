package com.ceudelavanda.lavandaflow.customers.application;

import com.ceudelavanda.lavandaflow.customers.domain.Customer;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerContact;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerManagementTest {
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private CustomerRepository repository;
    private CustomerManagement customers;

    @AfterAll static void closeValidators() { VALIDATORS.close(); }

    @BeforeEach void setUp() {
        repository = mock(CustomerRepository.class);
        customers = new CustomerManagement(repository, mock(CustomerQuery.class), VALIDATORS.getValidator(),
            Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void acceptsNameOnlyAndNormalizesWhitespaceAndIndependentOptionalValues() {
        var result = customers.register(new CustomerContact("  Ana  ", "  ", ""));
        assertThat(result.name()).isEqualTo("Ana");
        assertThat(result.phone()).isNull();
        assertThat(result.email()).isNull();
        assertThat(result.active()).isTrue();
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(customers.register(new CustomerContact("Ana", null, " ana@example.test ")).email())
            .isEqualTo("ana@example.test");
        assertThat(customers.register(new CustomerContact("Ana", "1234567", null)).phone()).isEqualTo("1234567");
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingAndBlankNames(String name) { rejects(new CustomerContact(name, null, null), "name"); }

    @Test void enforcesNameLengthAfterTrimming() {
        assertThat(customers.register(new CustomerContact(" " + "a".repeat(160) + " ", null, null)).name()).hasSize(160);
        rejects(new CustomerContact("a".repeat(161), null, null), "name");
    }

    @Test void normalizesDisplayPhoneSeparatorsAndRetainsOptionalLeadingPlus() {
        assertThat(customers.register(new CustomerContact("Ana", " +55 (11) 99999-1234. ", null)).phone())
            .isEqualTo("+5511999991234");
    }

    @ParameterizedTest @ValueSource(strings = {"1234567", "+1234567", "123456789012345", "+123456789012345"})
    void acceptsPhoneDigitBoundaries(String phone) {
        assertThat(customers.register(new CustomerContact("Ana", phone, null)).phone()).isEqualTo(phone);
    }

    @ParameterizedTest @ValueSource(strings = {"123456", "1234567890123456", "+1234567890123456", "++1234567", "123+4567", "123/4567", "123abc4567", "１２３４５６７", "123\t4567", "---", "+"})
    void rejectsInvalidPhoneCharactersAndDigitCounts(String phone) { rejects(new CustomerContact("Ana", phone, null), "phone"); }

    @ParameterizedTest @ValueSource(strings = {"invalid", "a@", "@example.test", "a b@example.test", "a@@example.test"})
    void rejectsInvalidEmail(String email) { rejects(new CustomerContact("Ana", null, email), "email"); }

    @Test void accepts254CharacterStandardAddressAndRejects255() {
        var address = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(57) + ".com";
        assertThat(address).hasSize(254);
        assertThat(customers.register(new CustomerContact("Ana", null, address)).email()).isEqualTo(address);
        rejects(new CustomerContact("Ana", null, "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(58) + ".com"), "email");
    }

    @Test void updatesRetainIdentityCreationTimeAndStateAndUseTheApplicationClock() {
        var old = new Customer(UUID.randomUUID(), new CustomerContact("Old", null, null), false, NOW.minusSeconds(60), NOW.minusSeconds(30));
        when(repository.findByIdForUpdate(old.id())).thenReturn(Optional.of(old));
        var result = customers.update(old.id(), new CustomerContact("New", null, null));
        assertThat(result.id()).isEqualTo(old.id());
        assertThat(result.createdAt()).isEqualTo(old.createdAt());
        assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(result.active()).isFalse();
    }

    @Test void activationIsIdempotentAndPreservesAuditTimeOnRepeatedAction() {
        var customer = Customer.create(new CustomerContact("Ana", null, null), NOW.minusSeconds(60));
        when(repository.findByIdForUpdate(customer.id())).thenReturn(Optional.of(customer));
        assertThat(customers.changeActiveState(customer.id(), true).updatedAt()).isEqualTo(customer.updatedAt());
        verify(repository, never()).save(any());
        assertThat(customers.changeActiveState(customer.id(), false).updatedAt()).isEqualTo(NOW);
    }

    @Test void allMissingRecordOperationsUseTheStandardNotFoundError() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> customers.getById(id)).isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> customers.update(id, new CustomerContact("Ana", null, null))).isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> customers.changeActiveState(id, false)).isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> customers.changeActiveState(id, true)).isInstanceOf(CustomerNotFoundException.class);
    }

    @Test void boundsPaginationAndTreatsEmptySearchAsNormalListing() {
        assertThat(new CustomerSearchQuery("  ", null, 0, 20).q()).isNull();
        assertThat(new CustomerSearchQuery(" Ana ", false, 0, 100).q()).isEqualTo("Ana");
        assertThatThrownBy(() -> new CustomerSearchQuery(null, null, -1, 20)).isInstanceOf(InvalidCustomerSearchQueryException.class);
        for (var size : new int[]{-1, 0, 101}) {
            assertThatThrownBy(() -> new CustomerSearchQuery(null, null, 0, size)).isInstanceOf(InvalidCustomerSearchQueryException.class);
        }
    }

    private void rejects(CustomerContact contact, String field) {
        clearInvocations(repository);
        assertThatThrownBy(() -> customers.register(contact)).isInstanceOfSatisfying(InvalidCustomerContactException.class,
            error -> assertThat(error.getDetails()).containsKey(field));
        verify(repository, never()).save(any());
    }
}
