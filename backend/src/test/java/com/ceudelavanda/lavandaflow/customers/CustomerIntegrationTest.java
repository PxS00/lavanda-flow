package com.ceudelavanda.lavandaflow.customers;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.customers.application.*;
import com.ceudelavanda.lavandaflow.customers.domain.Customer;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerContact;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"springdoc.api-docs.enabled=true"})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = "OPERATOR")
class CustomerIntegrationTest {
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private CustomerManagement customers;
    @Autowired private CustomerRepository repository;
    @Autowired private CustomerLookup lookup;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;

    @Test void createsRetrievesUpdatesDeactivatesAndReactivatesTheSamePersistedIdentity() throws Exception {
        var created = mvc.perform(post("/api/v1/customers").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"  Ana  \"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Ana"))
            .andExpect(jsonPath("$.phone").isEmpty()).andExpect(jsonPath("$.email").isEmpty())
            .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.createdAt").exists()).andReturn();
        var location = created.getResponse().getHeader("Location");
        var id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        var original = customers.getById(id);
        assertThat(lookup.findById(id)).contains(new CustomerSnapshot(id, "Ana", null, null, true));
        mvc.perform(put(location).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"  Ana Maria \",\"phone\":\"+55 (11) 99999-1234\",\"email\":\" ANA@example.test \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.phone").value("+5511999991234")).andExpect(jsonPath("$.email").value("ANA@example.test"));
        mvc.perform(post(location + "/deactivate").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        var inactive = lookup.findById(id).orElseThrow();
        assertThat(inactive.active()).isFalse();
        assertThat(inactive.name()).isEqualTo("Ana Maria");
        assertThat(jdbc.queryForObject("select count(*) from customer where id = ?", Long.class, id)).isEqualTo(1);
        assertThat(customers.getById(id).createdAt()).isEqualTo(original.createdAt());
        mvc.perform(post(location + "/activate").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        assertThat(lookup.findById(id).orElseThrow().active()).isTrue();
        mvc.perform(put(location).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\",\"phone\":\"  \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.phone").isEmpty()).andExpect(jsonPath("$.email").isEmpty());
    }

    @Test void duplicateContactsAreAllowedAndPublicLookupReturnsEmptyForMissingRecords() {
        var input = new CustomerContact("Same", "+1234567", "same@example.test");
        var first = customers.register(input);
        var second = customers.register(input);
        entityManager.flush();
        entityManager.clear();
        assertThat(customers.getById(first.id()).phone()).isEqualTo(input.phone());
        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(jdbc.queryForObject("select count(*) from customer where email = ?", Long.class, input.email())).isEqualTo(2);
        assertThat(lookup.findById(UUID.randomUUID())).isEmpty();
    }

    @Test void searchesAllContactsCaseInsensitivelyAndNormalizesPhoneQueries() {
        var contact = customers.register(new CustomerContact("Search Ana", "+5511999991234", "MixedCase@example.test"));
        for (var q : new String[]{"search ANA", "mixedcase@EXAMPLE", "+55 (11) 99999-", " ", ""}) {
            assertThat(customers.search(new CustomerSearchQuery(q, null, 0, 20)).content())
                .extracting(CustomerResult::id).contains(contact.id());
        }
        customers.changeActiveState(contact.id(), false);
        assertThat(customers.search(new CustomerSearchQuery("Search Ana", true, 0, 20)).content()).isEmpty();
        assertThat(customers.search(new CustomerSearchQuery("Search Ana", false, 0, 20)).content())
            .extracting(CustomerResult::id).containsExactly(contact.id());
        assertThat(customers.search(new CustomerSearchQuery("no match", null, 0, 20)).totalElements()).isZero();
    }

    @Test void escapesLikeMetacharactersAndDoesNotTurnSeparatorOnlyPhoneSearchIntoMatchAll() {
        var special = customers.register(new CustomerContact("Special %_!", "1234567", "a@example.test"));
        customers.register(new CustomerContact("Regular", "7654321", "b@example.test"));
        assertThat(customers.search(new CustomerSearchQuery("%_!", null, 0, 20)).content())
            .extracting(CustomerResult::id).containsExactly(special.id());
        assertThat(customers.search(new CustomerSearchQuery("()--", null, 0, 20)).content()).isEmpty();
    }

    @Test void pagesDeterministicallyByNameAndIdWithBoundedDefaults() throws Exception {
        var first = new UUID(0, 1); var second = new UUID(0, 2); var now = Instant.now();
        repository.save(new Customer(second, new CustomerContact("Paging same", null, null), true, now, now));
        repository.save(new Customer(first, new CustomerContact("Paging same", null, null), true, now, now));
        customers.register(new CustomerContact("Paging z", null, null));
        var page = customers.search(new CustomerSearchQuery("Paging", true, 0, 1));
        assertThat(page.content()).extracting(CustomerResult::id).containsExactly(first);
        assertThat(page.totalElements()).isEqualTo(3); assertThat(page.totalPages()).isEqualTo(3);
        assertThat(customers.search(new CustomerSearchQuery("Paging", true, 1, 1)).content()).extracting(CustomerResult::id).containsExactly(second);
        assertThat(customers.search(new CustomerSearchQuery("Paging", null, 3, 1)).content()).isEmpty();
        mvc.perform(get("/api/v1/customers")).andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/v1/customers").param("q", "Paging").param("active", "true").param("size", "100"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "0", "101"})
    void rejectsPageSizesOutsideTheContract(String size) throws Exception {
        mvc.perform(get("/api/v1/customers").param("size", size)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CUSTOMER_SEARCH_QUERY")).andExpect(jsonPath("$.details.size").exists());
    }

    @Test void rejectsInvalidPagingAndBindingWithExistingErrorShape() throws Exception {
        mvc.perform(get("/api/v1/customers").param("page", "-1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.page").exists());
        for (var parameter : new String[]{"page", "size", "active"}) {
            mvc.perform(get("/api/v1/customers").param(parameter, "invalid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
        }
    }

    @Test void missingRecordsReturnStandardErrorsForEveryDetailAndMaintenanceAction() throws Exception {
        var path = "/api/v1/customers/" + UUID.randomUUID();
        mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        mvc.perform(put(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\"}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        for (var action : new String[]{"activate", "deactivate"}) {
            mvc.perform(post(path + "/" + action).with(csrf())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        }
    }

    @Test void invalidContactCreateAndUpdateReturnFieldErrorsWithoutChangingTheRow() throws Exception {
        var saved = customers.register(new CustomerContact("Original", null, null));
        var body = "{\"name\":\" \",\"phone\":\"abc1234567\",\"email\":\"invalid\"}";
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{post("/api/v1/customers"), put("/api/v1/customers/" + saved.id())}) {
            mvc.perform(request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.name").exists()).andExpect(jsonPath("$.details.phone").exists())
                .andExpect(jsonPath("$.details.email").exists());
        }
        assertThat(customers.getById(saved.id())).isEqualTo(saved);
    }

    @Test void allEndpointsRequireAuthenticationAndEveryWriteRequiresValidCsrf() throws Exception {
        var path = "/api/v1/customers/" + UUID.randomUUID();
        for (var url : new String[]{"/api/v1/customers", path}) {
            mvc.perform(get(url).with(anonymous())).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{post("/api/v1/customers"), put(path), post(path + "/activate"), post(path + "/deactivate")}) {
            mvc.perform(request.with(anonymous()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            mvc.perform(request.with(user("operator").authorities(() -> "OPERATOR")).with(csrf().useInvalidToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_VALIDATION_FAILED"));
        }
        mvc.perform(post(path + "/deactivate")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_VALIDATION_FAILED"));
    }

    @ParameterizedTest @ValueSource(strings = {
        "name = NULL", "name = ''", "name = '   '", "name = ' padded '", "name = repeat('a', 161)",
        "phone = ''", "phone = '+123456'", "phone = '+1234567890123456'", "phone = '123 4567'", "phone = 'abc1234567'",
        "email = ''", "email = ' padded@example.test '", "email = repeat('a', 255)",
        "active = NULL", "created_at = NULL", "updated_at = NULL"
    })
    void postgresEnforcesImportantStorageConstraints(String assignment) {
        var saved = customers.register(new CustomerContact("Constraint", "+123456789012345", "valid@example.test"));
        entityManager.flush();
        // Each invocation has its own rolled-back transaction, including the PostgreSQL failed statement.
        assertThatThrownBy(() -> jdbc.update("update customer set " + assignment + " where id = ?", saved.id()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void openApiDocumentsTheDeliveredCustomerRoutesAndDtoWithoutDelete() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/customers'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/customers'].post.responses['201']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/customers/{customerId}'].put").exists())
            .andExpect(jsonPath("$.paths['/api/v1/customers/{customerId}'].delete").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/v1/customers/{customerId}/activate'].post").exists())
            .andExpect(jsonPath("$.paths['/api/v1/customers/{customerId}/deactivate'].post").exists())
            .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.name.maxLength").value(160));
    }
}
