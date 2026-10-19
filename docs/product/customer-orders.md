# Customer order operator workflow

## Access and purpose

Sign in as an operator and open **Comercial → Pedidos**. **Criar pedido** opens the draft editor.
Saving a draft does not reserve, withdraw or guarantee stock. Confirmation explicitly withdraws stock
and preserves the confirmed sale history.

## Capture and edit

1. Optionally search/select an active customer. **Deixar sem cliente** clears the association.
2. Search finished products and select the existing stocked presentation or bulk product. Only active
   finished products using **un** (`UNIT`) or **mL** (`MILLILITER`) can be selected. Search results are
   paginated; refine the search or use another page when no eligible results appear on the current page.
3. Enter a positive quantity in the displayed catalog unit and an explicit BRL unit price, including zero
   where intended. Use comma or point for decimals without grouping separators. Quantity permits up to
   13 integer/6 decimal digits; price permits up to 15 integer/4 decimal digits. No conversion is applied.
4. Each product appears at most once. Edit its existing line or use **Remover produto**; at least one
   line is required to save.
5. Review **Total provisório**, then **Salvar rascunho**. The displayed preview is provisional; the
   backend validates current references and returns the official line amounts and total.

A successful save opens **Detalhes do pedido**, with **Total oficial (BRL)**, exact line values and
**Editar rascunho**. Order and retained-product line identities stay stable across edits. Customer and
product labels are current catalog/contact values, not confirmed sale history.

## Query and recover

The list initially shows drafts. In **Situação**, choose **Vendas confirmadas** or **Cancelados**
to discover persisted history without knowing its ID. Optionally search/select a customer (including
inactive contacts); **Todos os clientes** clears that selection. Customer selection takes effect with
**Aplicar filtros**. Search accepts a complete or partial order ID. **Criado de/até (UTC)** always
filter creation. For confirmed sales, **Confirmada de/até (UTC)** independently filter confirmation.
Both date ranges are inclusive UTC days and combine with customer, ID and situation filters.
Changing away from confirmed sales clears confirmation dates. **Limpar** restores drafts and clears
all filters and the customer selection. Page controls retain applied filters; each application starts
at the first page. Results sort by newest creation, then stable ID for ties.

Use **Ver pedido** to inspect a result. Confirmed details show historical customer/product values,
exact official amounts and every batch quantity/movement ID, even after renaming or deactivation.
Cancelled details say **Pedido cancelado**, expose no edit/confirmation actions and have no stock
effect. The list and details display loading, empty and retryable error states. Missing IDs show a
not-found state. The [technical contract](../specs/0275-expose-paginated-sale-history.md) documents
additive API filters and validation.

If saving fails, entered values remain available. Review field feedback and the current customer/product
state; inactive/missing customers or products block saving. Replace or clear an invalid customer, or
remove/replace an ineligible product. Retry only when ready. Pending writes disable duplicate submission.
No success or stock change is displayed until the server succeeds.

## Confirm a reviewed draft

Open the draft detail, review customer, products, quantities, prices and the official total, then select
**Confirmar pedido e retirar estoque**. While the request is pending, confirmation and editing are
disabled. The server alone selects eligible batches using FEFO. No quantity is silently reduced.

On success, **Venda confirmada** displays the persisted customer/product snapshots, confirmation time
and each allocated batch, exact quantity and movement ID. Confirmed details remain available at the
same order URL and are discoverable by selecting **Vendas confirmadas** in the list. Confirmed sales cannot be edited,
cancelled or deleted through this workflow.

If eligible stock is insufficient or another request fails, the UI retains the draft and shows the error.
Select **Tentar confirmar novamente** after resolving the problem. A network failure can leave the
result unknown: retrying the same order returns an already persisted confirmation or explicitly
attempts the still-draft confirmation. It never withdraws a confirmed order twice.

The authenticated API also supports cancellation of an unconfirmed draft without stock effects;
there is no cancellation control in this confirmation screen. Cancellation never restores sold stock.
Physical returns and confirmed-data corrections require separate auditable operations; payment and
returns management remain outside this workflow.

## Record an inspected physical return

Open a **Venda confirmada** from history. Under **Devolução física ao estoque**, select the original
product/batch, enter a positive exact quantity in that product's original unit and a reason (up to
255 characters). Separately confirm physical receipt/inspection and acceptance in saleable condition,
then choose **Registrar devolução aceita**. Do not use this action for rejected or unsaleable goods.
The server rejects undated batches, batches expiring today or earlier, inactive/ineligible products,
and quantities exceeding the original allocation less all accepted returns. A fully consumed batch
may receive eligible stock. Multiple separate partial returns are allowed within that same limit.

While pending, controls are disabled. After an error, **Tentar registrar novamente** reuses the same
correction identity and exact submitted data; do not create another return to recover a lost response.
A definitive validation/eligibility/limit rejection offers **Revisar dados rejeitados**. A successful
response shows correction and movement IDs and clears the form for another separate physical return.
**Histórico de devoluções aceitas** shows persisted line/batch, quantity, reason, operator, decisions,
time and movement reference, including after reopening the sale. Use **Recarregar devoluções** after
a history-read failure. If leaving after an unknown result, inspect that history before recording more.

The confirmed sale's totals, snapshots, allocations and original withdrawal movements remain unchanged.
This is a stock operation only: no refund, payment reversal, sale cancellation or automatic stock
restoration. See [spec #276](../specs/0276-register-sale-linked-stock-corrections.md) for exact API,
retry, eligibility and concurrency contracts.
