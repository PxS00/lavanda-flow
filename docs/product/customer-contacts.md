# Customer contact operator workflow

Issue #260 delivers the customer contact slice of the approved v0.8.0 scope. Orders and sales are planned;
this workflow has no inventory effect.

1. Sign in and open **Clientes** under **Cadastros**. The list initially shows active contacts.
2. Use **Nome, telefone ou e-mail** for partial search. Choose **Todos**, **Ativo**, or **Inativo** in
   **Status**, then **Aplicar filtros**. Filters restart on page zero. Paging offers 20, 50 or 100 records.
   **Redefinir filtros** restores the active-only default.
3. Choose **Cadastrar cliente**. Enter a name (required, maximum 160 characters). Phone and email are
   independently optional, so a name-only contact is valid. Phone accepts a leading `+`, 7–15 digits and
   display separators (spaces, parentheses, periods and hyphens). Email must be valid and at most 254
   characters. Surrounding whitespace is trimmed; blank optional values are omitted.
4. Choose **Salvar cliente**. While saving, fields are read-only and repeat submission is blocked.
   Validation focuses the first invalid field. Recoverable failures preserve input and display pt-BR
   feedback; correct the value or retry. On success, the detail view displays the server's stored values.
5. Open a contact by name, then **Editar cliente** to replace its name/phone/email. This preserves its
   UUID, creation time and active state. Clearing an optional field removes that contact value.
6. In the detail view choose **Desativar cliente** or **Ativar cliente**. The displayed status changes
   only after server success. Failures retain the previous status and permit retry. Deactivation retains
   the contact; use the inactive filter to find it and reactivate it later.

Loading, empty, error and retry states are explicit. Duplicate names/phones/emails are allowed; contacts
are identified by UUID rather than a contact method. There is no delete action, CRM profile, document
storage, government identifier, address, note, order, payment or fiscal workflow in this slice.

The planned sales module must check current customer active state for new activity and capture its own
historical contact snapshots. Existing inventory, production, genealogy and authentication contracts
are unchanged.
