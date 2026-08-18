# Escopo da V1

## Objetivo

A primeira versão do Lavanda Flow deve resolver o problema atual de controle de estoque da Céu de Lavanda de forma simples, confiável e rastreável.

A V1 será focada em estoque geral, não apenas perfumes ou essências. O modelo deve permitir cadastrar e controlar matérias-primas, insumos químicos, bases, embalagens e demais itens utilizados na operação.

## Problemas que a V1 resolve

- falta de visibilidade sobre o que já existe em estoque;
- compras duplicadas por esquecimento;
- perdas por vencimento;
- ausência de histórico de entradas e saídas;
- dificuldade para saber quanto ainda existe de cada item;
- dificuldade para identificar lotes e fornecedores associados ao estoque.

## Funcionalidades incluídas

### Catálogo de itens

Permitir cadastrar itens de estoque com, no mínimo:

- nome;
- categoria;
- unidade de medida;
- status ativo/inativo;
- observações opcionais.

Categorias iniciais previstas:

- essência;
- insumo químico;
- base;
- álcool;
- corante;
- fixador;
- frasco;
- válvula;
- tampa;
- rótulo;
- embalagem;
- outros.

### Fornecedores

Permitir associar fornecedores aos lotes recebidos.

Dados mínimos previstos:

- nome;
- identificação opcional;
- contato opcional;
- observações opcionais.

### Lotes

Cada item pode possuir vários lotes independentes.

Cada lote deve permitir registrar:

- item;
- fornecedor;
- número/código do lote;
- quantidade inicial;
- quantidade disponível;
- unidade de medida;
- data de entrada;
- data de validade, quando aplicável;
- observações.

A existência de múltiplos lotes para o mesmo item é obrigatória no modelo, pois compras diferentes podem possuir datas de validade e fornecedores distintos.

### Movimentações de estoque

Toda alteração de quantidade deve gerar uma movimentação.

Tipos iniciais:

- entrada;
- consumo/saída;
- ajuste positivo;
- ajuste negativo;
- perda;
- descarte por vencimento.

Cada movimentação deve registrar:

- lote afetado;
- tipo;
- quantidade;
- data e hora;
- motivo ou observação, quando aplicável.

Não deve existir alteração silenciosa de estoque sem histórico correspondente.

### Saldo de estoque

O sistema deve permitir consultar:

- saldo por item;
- saldo por lote;
- estoque zerado;
- estoque abaixo do mínimo definido, quando configurado.

### Validade

O sistema deve identificar:

- lotes vencidos;
- lotes próximos do vencimento;
- lotes válidos.

A interface deve destacar itens que precisam ser utilizados com prioridade.

### FEFO

Quando houver múltiplos lotes disponíveis do mesmo item, o sistema deverá priorizar o lote com validade mais próxima quando essa regra for aplicável.

FEFO significa *First Expired, First Out*.

### Pesquisa e consulta

A V1 deve permitir pesquisar itens pelo nome e visualizar rapidamente:

- quantidade total disponível;
- lotes existentes;
- validade;
- fornecedor;
- histórico de movimentações.

### Dashboard simples

A tela inicial deverá apresentar apenas informações operacionais úteis, como:

- quantidade de itens ativos;
- itens com estoque baixo;
- itens sem estoque;
- lotes próximos do vencimento;
- lotes vencidos.

## Importação inicial

O estoque atual existente em CSV será usado como fonte de migração inicial.

Os dados deverão ser normalizados antes da importação, especialmente:

- nomes;
- gênero/referência quando aplicável;
- quantidades;
- datas de validade;
- valores ausentes ou inconsistentes.

O CSV não será considerado fonte de verdade após a migração.

## Fora do escopo da V1

Não fazem parte da primeira versão:

- fórmulas e receitas;
- versionamento de fórmulas;
- produção automatizada;
- consumo automático de matérias-primas por produção;
- criação de lotes de produto acabado;
- rastreabilidade completa entre matéria-prima, base e produto final;
- custos de produção;
- cálculo de margem;
- vendas;
- emissão fiscal;
- código de barras;
- QR Code;
- integração com fornecedores;
- previsão automática de compras.

Essas funcionalidades devem ser suportadas futuramente pela arquitetura, mas não devem aumentar o escopo da V1.

## Critérios de sucesso

A V1 será considerada útil quando a usuária conseguir, sem recorrer à planilha:

1. pesquisar um item e saber se existe em estoque;
2. consultar quantidade e validade;
3. registrar uma nova compra ou entrada;
4. registrar quanto foi utilizado;
5. consultar o histórico de movimentações;
6. identificar rapidamente itens vencidos ou próximos do vencimento.

## Princípios de produto

- simplicidade de uso acima de quantidade de funcionalidades;
- interface mobile-first;
- histórico em vez de alterações destrutivas;
- rastreabilidade desde o modelo de dados;
- consistência de estoque acima de conveniência técnica;
- evolução incremental sem transformar a V1 em um ERP completo.
