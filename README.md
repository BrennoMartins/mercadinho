# Mercadinho (backend do Aroma Sabor OS)

API REST em Java 21 / Spring Boot. Este documento descreve as **regras financeiras** implementadas e os contratos HTTP
relacionados. A implementação é a fonte de verdade; qualquer divergência deve ser tratada como defeito de uma das
partes, não resolvida silenciosamente.

Principais classes: `SaleService`, `SaleDiscountAllocator`, `SaleGrossProfitCalculator`, `SaleRepository`
(`getGrossProfitSummary`), `SalesReportService`, `DashboardService` e a migration
`V11__add_historical_sale_item_financial_data.sql`. O consumo no frontend está descrito no README de `aroma-sabor-web`.

## 1. Dados históricos dos itens vendidos

### Regra de negócio

No momento da venda, cada item (`sale_items`) grava os valores vigentes naquele instante:

| Dado | Coluna | Origem na criação |
| --- | --- | --- |
| Preço unitário | `unit_price` | Preço negociado enviado no item ou, se omitido, preço atual do produto |
| Custo unitário | `unit_cost_price` | Custo atual do produto |
| Nome do produto | `product_name_snapshot` | Nome atual do produto |
| Categoria | `category_id_snapshot`, `category_name_snapshot` | Categoria atual do produto (ambos `null` se não houver categoria) |
| Subtotal do item | `subtotal` | `unitPrice × quantity` |
| Desconto alocado | `discount_allocated_amount` | Rateio do desconto da venda (seção 3) |

A venda grava `subtotal`, `discount_amount` e `total`.

As respostas de leitura (`GET /sales`, `GET /sales/{id}`) devolvem **os valores gravados**
(`SaleService.toItemResponse`). Eles **não** devem ser reconstruídos consultando o cadastro atual: preço, custo, nome e
categoria do produto podem mudar depois, e recalcular a partir do cadastro alteraria faturamento, lucro e relatórios de
vendas já concluídas.

### Registros legados

A V11 adiciona as colunas de snapshot e `discount_allocated_amount` como **anuláveis e sem backfill**; a V8 já havia
adicionado `unit_cost_price` anulável; a V9 definiu, para vendas antigas, `subtotal = total` e `discount_amount = 0`.
Consequências:

- Itens legados mantêm `productName`, `categoryId`, `categoryName`, `unitCostPrice` e/ou `discountAllocatedAmount`
  como `null`. O backend não os preenche.
- `categoryId`/`categoryName` `null` também ocorre em vendas novas de produtos sem categoria; o campo sozinho não
  distingue os dois casos.
- A V11 também amplia `sales.subtotal`, `sales.total` e `sale_items.subtotal` para `NUMERIC(30,2)` e impõe:
  snapshots de categoria em par (ambos nulos ou ambos preenchidos) e
  `0 <= discount_allocated_amount <= subtotal` do item.

## 2. Custo do produto

- **Obrigatório em novas vendas.** Se `product.costPrice` for `null`, a criação falha com
  `ProductCostNotDefinedException` (HTTP `409`), e nada é persistido (a operação é transacional).
- **Zero é um custo válido** (produto de custo nulo, p.ex. brinde) e é diferente de custo ausente. Custo negativo é
  inválido; o custo aceita até 8 dígitos inteiros e 2 decimais. O banco também impõe `unit_cost_price >= 0`.
- **Custo ausente (`null`) só existe em dados legados.** Ele significa "custo histórico não registrado", e não zero:
  o item é contado em `itemsWithoutHistoricalCost` e seu lucro não é inventado.
- **Preservação:** o custo é copiado do produto para `sale_items.unit_cost_price` na criação. Alterar depois o custo
  do produto não afeta vendas já registradas.
- O cliente **não** envia custo; apenas o backend o define.

## 3. Descontos e rateio

### Conceitos

- **Desconto da venda** (`discountAmount`): valor geral em reais informado na criação (opcional, assume `0.00`).
  Não pode ser negativo, ter mais de 2 casas decimais nem mais de 8 dígitos inteiros, e não pode superar o subtotal
  (`400`).
- **Desconto alocado do item** (`discountAllocatedAmount`): parcela do desconto da venda atribuída a cada item.
- `total = subtotal − discountAmount`.

### Algoritmo (`SaleDiscountAllocator`)

1. Valores convertidos para **centavos inteiros** (`BigInteger`), sem ponto flutuante.
2. Cota proporcional de cada item: `piso(descontoCentavos × subtotalItemCentavos / subtotalVendaCentavos)`.
3. Os centavos restantes (`desconto − soma das cotas`) são distribuídos **um a um** pelo método do **maior resto**:
   os itens são ordenados pelo resto da divisão (decrescente).
4. **Desempate** pela ordem original dos itens na requisição (menor índice primeiro).
5. Garantias: a soma dos descontos alocados é exatamente o desconto da venda, e nenhum item recebe mais que seu
   subtotal (o algoritmo lança `IllegalArgumentException` se isso fosse violado). Subtotal total zero resulta em
   alocação zero para todos.

O banco valida apenas `0 <= alocado <= subtotal` por item; a igualdade entre soma e desconto total é garantida pelo
alocador na criação.

### Exemplos

Desconto `10.00` em três itens de `10.00`: cota de cada um = 3,33 com resto igual; sobra 1 centavo, e o empate vai ao
primeiro item → `3.34 / 3.33 / 3.33`.

Desconto `5.00` em itens de `10.00` e `20.00`: cotas `1.66` (resto 2000) e `3.33` (resto 1000); sobra 1 centavo, que
vai ao maior resto (primeiro item) → `1.67 / 3.33` (soma `5.00`).

## 4. Cálculo do lucro bruto

Três conceitos distintos nunca devem ser confundidos:

- **Lucro total conhecido:** todos os itens têm custo; o valor é o lucro da venda.
- **Contribuição conhecida:** apenas parte dos itens tem custo; o valor soma só esses itens. **Não é o lucro da
  venda.**
- **Indeterminado:** `grossProfitKnownAmount = null`; não há como calcular com segurança.

`grossProfitComplete` é `true` se, e somente se, `itemsWithoutHistoricalCost == 0`. Por isso, `null` sempre vem com
`grossProfitComplete = false`.

Lucro do item conhecido = `(unitPrice − unitCostPrice) × quantity`. O valor pode ser negativo.

| Cenário (venda `COMPLETED`) | `grossProfitKnownAmount` | `grossProfitComplete` |
| --- | --- | --- |
| Todos os custos conhecidos, com alocação em todos os itens e desconto > 0 | Σ lucro dos itens − Σ descontos alocados | `true` |
| Todos os custos conhecidos, alocação ausente em algum item (legado) | Σ lucro dos itens − desconto total da venda | `true` |
| Todos os custos conhecidos, sem desconto | Σ lucro dos itens | `true` |
| Todos os custos conhecidos e lucro exatamente zero | `0.00` (valor conhecido, não `null`) | `true` |
| Parte dos custos ausente, desconto `0` | Σ lucro dos itens com custo (contribuição conhecida) | `false` |
| Parte dos custos ausente, desconto > 0 e todo item **com custo** tem alocação | Σ lucro dos itens com custo − Σ descontos alocados a esses itens (contribuição conhecida) | `false` |
| Parte dos custos ausente, desconto > 0 e algum item **com custo** sem alocação (legado) | `null` | `false` |

Notas:

- **Desconto zero com custos ausentes:** como não há desconto a distribuir, a falta de alocação não impede o cálculo;
  o resultado é a contribuição conhecida, e não `null`.
- **Vendas legadas sem rateio:** se o desconto é maior que zero e há custo ausente, não é possível saber quanto do
  desconto pertence aos itens com custo, então o resultado é indeterminado (`null`). Se todos os custos são
  conhecidos, o desconto inteiro é subtraído e o lucro é total.
- **Exemplo (lucro parcial):** item A `10.00` com custo `4.00` (lucro 6,00) e item B `20.00` sem custo; desconto
  `3.00` alocado `1.00` a A e `2.00` a B. Contribuição conhecida = `6.00 − 1.00 = 5.00`, com
  `grossProfitComplete = false` e `itemsWithoutHistoricalCost = 1`. O lucro real da venda é desconhecido.
- **Exemplo (indeterminado):** mesma venda, mas legada, com desconto `3.00` e sem alocação em A →
  `grossProfitKnownAmount = null`.
- **Exemplo (zero conhecido):** item vendido a `4.00` com custo `4.00` → `0.00` / `true`.
- **Vendas não concluídas** (`status != COMPLETED`): a resposta devolve `grossProfitKnownAmount = null`,
  `grossProfitComplete = false` e `itemsWithoutHistoricalCost = 0`. Aqui `null` significa "não aplicável", não
  "indeterminado"; relatórios e dashboard ignoram essas vendas.

A lógica existe em duas implementações equivalentes: `SaleGrossProfitCalculator` (Java, por venda) e a consulta SQL
`SaleRepository.getGrossProfitSummary` (agregada). Qualquer alteração deve ser feita nas duas.

## 5. Contratos HTTP

### Requisição (`POST /sales`)

`items[]` (`productId`, `quantity`, `unitPrice` opcional) e `discountAmount` opcional. Não há campo de custo,
turno, subtotal ou desconto por item.

### Campos financeiros das respostas

| Campo | Onde | Significado |
| --- | --- | --- |
| `subtotal` | venda | Soma dos subtotais dos itens, antes do desconto |
| `discountAmount` | venda | Desconto total aplicado à venda |
| `total` | venda | `subtotal − discountAmount`; base do faturamento |
| `unitCostPrice` | item | Custo unitário gravado na venda; `null` = não registrado (legado); `0` = custo zero |
| `discountAllocatedAmount` | item | Parcela do desconto atribuída ao item; `0.00` = item sem desconto; `null` = rateio não registrado (legado) |
| `grossProfitKnownAmount` | venda, relatório, dashboard | Lucro bruto conhecido (seção 4); `null` = indeterminado; `0` = lucro conhecido igual a zero |
| `grossProfitComplete` | venda, relatório, dashboard | `true` se nenhum item concluído está sem custo histórico |
| `itemsWithoutHistoricalCost` | venda, relatório, dashboard | Quantidade de **itens** (não unidades) sem custo histórico |

Zero, `null` e ausência têm significados diferentes: **zero é um valor real**; **`null` é informação desconhecida
ou não aplicável**; um campo **ausente** não faz parte do JSON.

O backend atual sempre envia `subtotal` e `discountAmount` da venda e todos os campos de item acima (com `null`
quando legado). A tolerância a campos ausentes de versões anteriores da API é uma característica do **consumidor**
(ver README do frontend) e vale apenas para os campos ali listados.

## 6. Relatórios e dashboard

### Agregação

`GET /reports/sales?startDate&endDate` (período inclusivo) e `GET /dashboard/summary` (dia atual, fuso do sistema)
consideram somente vendas `COMPLETED`:

- `totalRevenue` / `revenue` = Σ `total`; `salesCount` = quantidade de vendas;
  `averageTicket` = faturamento ÷ vendas, arredondado a 2 casas (`HALF_UP`), `0.00` sem vendas.
- `grossProfitKnownAmount` = Σ do valor conhecido **de cada venda** (inclusive contribuições parciais), calculado no
  banco. Se **qualquer** venda do período for indeterminada, o agregado inteiro é `null`: não se soma uma parte do
  período como se fosse o todo.
- `itemsWithoutHistoricalCost` = Σ dos itens sem custo; `grossProfitComplete` = esse total é zero.
- Período/dia sem vendas: `0.00` e `grossProfitComplete = true`.

Combinações possíveis no agregado: valor + `true` (lucro total conhecido, incluindo `0`); valor + `false`
(contribuição conhecida — **não** é o lucro do período); `null` + `false` (indeterminado). `null` + `true` não ocorre.

A série `dailySales` do relatório contém apenas `date`, `revenue` e `salesCount` (todos os dias do período, em ordem,
com zeros nos dias sem venda); não traz lucro.

### Consistência transacional

`SalesReportService.getReport` e `DashboardService.getSummary` usam
`@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`, de modo que as consultas de cada
chamada (faturamento, lucro, vendas recentes, turno atual no dashboard) enxergam o mesmo snapshot. Uma venda
confirmada entre duas dessas consultas não deixa o resumo incoerente (coberto por `DashboardConsistencyIT`, que
executa o serviço sem transação externa).

Limitações:

- O nível de isolamento só é aplicado quando o método **inicia** a transação. Se for chamado dentro de uma transação
  já existente, o Spring a reutiliza (propagação `REQUIRED` padrão) e o isolamento declarado é ignorado. O projeto
  não habilita `validateExistingTransaction`, e não há teste para esse cenário; hoje os controllers chamam os
  serviços diretamente, sem transação externa.
- O snapshot vale por chamada. `/dashboard/top-products` e `/dashboard/low-stock`
  (`DashboardInsightsService`, `@Transactional(readOnly = true)` sem isolamento explícito) e `GET /sales` são
  requisições e transações separadas e podem refletir momentos diferentes do resumo.
