package com.ceudelavanda.lavandaflow.shared.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface OperatorAccountRepository extends JpaRepository<OperatorAccount, UUID> {

    Optional<OperatorAccount> findByUsernameIgnoreCase(String username);
}
