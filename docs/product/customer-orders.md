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

The draft list searches a complete or partial order ID and inclusive creation dates labeled UTC. Use
**Aplicar filtros**, **Limpar**, or the page controls. It displays loading, empty, and error states.
Detail and edit URLs show a not-found state for missing drafts.

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
same order URL; the draft list continues to list only drafts. Confirmed sales cannot be edited,
cancelled or deleted through this workflow.

If eligible stock is insufficient or another request fails, the UI retains the draft and shows the error.
Select **Tentar confirmar novamente** after resolving the problem. A network failure can leave the
result unknown: retrying the same order returns an already persisted confirmation or explicitly
attempts the still-draft confirmation. It never withdraws a confirmed order twice.

The authenticated API also supports cancellation of an unconfirmed draft without stock effects;
there is no cancellation control in this confirmation screen. Cancellation never restores sold stock.
Physical returns and confirmed-data corrections require separate auditable operations; payment and
returns management remain outside this workflow.
