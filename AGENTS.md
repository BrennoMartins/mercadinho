# Aroma Sabor OS — Agent Instructions

## 1. Objetivo do projeto

O Aroma Sabor OS é um sistema de ponto de venda (PDV) e gestão operacional para um pequeno mercadinho.

O sistema tem como objetivos principais:

- Registrar vendas de forma rápida e confiável.
- Gerenciar produtos e categorias.
- Controlar o estoque e suas movimentações.
- Gerenciar a abertura e o fechamento de turnos do caixa.
- Consultar indicadores operacionais.
- Disponibilizar relatórios de vendas e de desempenho.
- Evoluir gradualmente para uma solução estável, testável e de fácil manutenção.

Priorize a confiabilidade dos dados, a simplicidade operacional e a manutenção do sistema.

Não introduza complexidade arquitetural sem uma necessidade concreta.

---

## 2. Estrutura do workspace

Este workspace contém dois projetos independentes que compõem o Aroma Sabor OS.

### 2.1. Backend — `mercadinho`

Responsável por:

- Regras de negócio.
- Persistência dos dados.
- APIs REST.
- Validações.
- Controle de vendas.
- Gerenciamento de produtos e categorias.
- Movimentações de estoque.
- Abertura, consulta e fechamento de turnos.
- Cálculos e indicadores operacionais.

Tecnologias principais:

- Java 21.
- Spring Boot 3.
- Gradle.
- PostgreSQL.
- Flyway.
- Spring Data JPA.
- Testes automatizados.
- Swagger/OpenAPI.

### 2.2. Frontend — `aroma-sabor-web`

Responsável por:

- Interface do operador.
- Navegação entre funcionalidades.
- Caixa e registro de vendas.
- Cadastro e consulta de produtos.
- Gestão de estoque.
- Consulta de movimentações.
- Abertura e fechamento de turnos.
- Dashboard e relatórios.
- Experiência de uso em diferentes tamanhos de tela.

Tecnologias principais:

- React 19.
- TypeScript.
- Vite.
- React Router.
- TanStack Query.
- Axios.
- React Hook Form.
- Zod.
- CSS Modules.
- Lucide React.
- vite-plugin-pwa.

Use as versões e dependências já definidas nos arquivos de configuração. Não atualize versões sem necessidade explícita.

### 2.3. Independência entre os projetos

Os dois projetos têm responsabilidades distintas.

Antes de executar uma tarefa, identifique quais repositórios precisam ser alterados.

Regras:

- Uma tarefa exclusivamente de backend deve alterar somente `mercadinho`.
- Uma tarefa exclusivamente de frontend deve alterar somente `aroma-sabor-web`.
- Uma tarefa de integração pode alterar ambos, mas isso deve estar explicitamente definido no escopo.
- O outro projeto pode ser consultado para entender contratos e comportamentos existentes, sem modificá-lo.
- Não crie cópias de regras de negócio no frontend.
- Não altere contratos de API sem avaliar o impacto no consumidor.
- Não faça mudanças nos dois projetos apenas por conveniência.

Se o escopo estiver ambíguo, explique quais projetos considera necessário modificar antes de realizar mudanças.

---

## 3. Procedimento obrigatório antes de cada tarefa

Antes de modificar qualquer arquivo:

1. Leia este `AGENTS.md`.
2. Leia eventuais instruções adicionais existentes no projeto que será alterado, como outros arquivos `AGENTS.md`.
3. Identifique o diretório e o repositório corretos.
4. Execute `git status --short` no repositório correspondente.
5. Inspecione as alterações locais existentes.
6. Entenda a implementação atual e os padrões utilizados.
7. Identifique os contratos e as funcionalidades que podem ser afetados.
8. Defina o escopo mínimo necessário para concluir a tarefa.

Não presuma que o repositório está limpo.

Não presuma que uma funcionalidade não existe sem pesquisar o código.

Não crie classes, componentes, endpoints ou abstrações antes de verificar se já existe uma implementação adequada.

Para tarefas com impacto nos dois projetos, inspecione o estado do Git de ambos antes de começar.

Quando houver decisões arquiteturais relevantes, apresente brevemente a abordagem planejada antes de implementar.

---

## 4. Proteção de alterações existentes

Esta regra é obrigatória para todas as tarefas.

- Nunca descarte alterações locais preexistentes.
- Nunca reverta arquivos apenas para deixar o diff limpo.
- Nunca execute `git reset --hard`.
- Nunca execute `git clean -fd`.
- Nunca sobrescreva alterações de outras tarefas sem autorização explícita.
- Não remova arquivos simplesmente porque parecem não utilizados.
- Não altere arquivos fora do escopo sem justificativa.
- Não faça commit automaticamente.
- Não faça push automaticamente.
- Não altere branches automaticamente.

Antes de editar um arquivo já modificado, inspecione o diff e preserve as alterações preexistentes.

Ao finalizar, compare o estado inicial com o final e identifique claramente as alterações realizadas pela tarefa atual.

Se encontrar um problema preexistente, reporte-o separadamente em vez de tentar corrigi-lo sem autorização.

---

## 5. Princípios gerais de desenvolvimento

Priorize:

1. Correção funcional.
2. Integridade dos dados.
3. Segurança.
4. Clareza do código.
5. Testabilidade.
6. Consistência com os padrões existentes.
7. Simplicidade.
8. Desempenho adequado ao volume esperado.

Regras:

- Prefira mudanças pequenas e focadas.
- Evite duplicação de lógica.
- Evite abstrações desnecessárias.
- Não introduza dependências sem necessidade justificada.
- Não altere comportamentos existentes sem necessidade funcional.
- Não esconda erros com tratamentos genéricos.
- Não use dados fictícios em funcionalidades reais.
- Não ignore exceções para fazer testes passarem.
- Não fragilize testes existentes.
- Não declare testes aprovados sem executá-los.
- Não implemente funcionalidades futuras que não façam parte da tarefa atual.

Não refatore grandes partes do sistema durante uma tarefa pequena.

Se identificar uma refatoração recomendável fora do escopo, registre-a como sugestão.

---

# 6. Regras específicas do backend

Projeto: `mercadinho`

## 6.1. Arquitetura

O backend segue uma organização inspirada em Clean Architecture.

Respeite a estrutura real dos pacotes, incluindo as camadas existentes de:

- Domain.
- Application.
- Infrastructure.
- Interface.

Não reorganize os pacotes sem necessidade explícita.

Responsabilidades gerais:

### Domain

Contém conceitos e regras centrais do negócio, conforme os padrões existentes.

Evite dependências de frameworks de infraestrutura quando a arquitetura atual permitir mantê-los isolados.

### Application

Coordena os casos de uso e as operações da aplicação.

Não coloque lógica complexa de negócio diretamente nos controllers.

### Infrastructure

Responsável por persistência, integrações e detalhes técnicos.

### Interface

Responsável pelos endpoints, DTOs de entrada e saída e integração HTTP, conforme a estrutura atual.

Consulte as classes existentes antes de decidir onde adicionar uma nova responsabilidade.

## 6.2. Regras de negócio

As seguintes regras devem ser preservadas:

### Vendas

- Uma venda exige um turno aberto.
- O backend identifica o turno aberto; o frontend não deve inventar ou enviar um identificador de turno quando o contrato não exige isso.
- A venda deve respeitar as validações de produtos e estoque existentes.
- Os valores históricos da venda devem permanecer consistentes mesmo quando o cadastro ou o preço de um produto mudar posteriormente.
- Uma falha durante a operação não deve deixar a venda e suas movimentações parcialmente persistidas.
- Preserve as transações e os mecanismos de concorrência existentes.

### Turnos

- Respeite o ciclo de vida atual dos turnos.
- Preserve as regras de abertura, consulta e fechamento.
- Não permita que uma alteração prejudique a consistência entre vendas e fechamento.
- Não introduza transições de estado que não estejam previstas no modelo atual.

### Estoque

- Reposição, ajuste e baixa por venda devem respeitar os casos de uso existentes.
- A reposição deve registrar a movimentação correspondente.
- O ajuste manual representa a quantidade física final informada, e não necessariamente uma variação enviada pelo usuário.
- A movimentação de ajuste deve preservar a diferença assinada e o motivo conforme o contrato existente.
- Alterações cadastrais de produtos não devem sobrescrever o estoque.
- Desativação e reativação de produtos devem preservar o histórico.
- Respeite as transações e os bloqueios necessários para evitar inconsistências concorrentes.

### Produtos

- Preserve a distinção entre dados cadastrais e dados de estoque.
- Não reintroduza atualizações de estoque em endpoints cadastrais.
- Preserve os contratos existentes de ativação, desativação e reativação.

Se encontrar divergência entre estas instruções e o código atual, investigue antes de alterar o comportamento.

Não modifique regras de negócio apenas para simplificar a implementação.

## 6.3. Banco de dados

- Utilize PostgreSQL conforme a configuração existente.
- Respeite o uso atual de Flyway.
- Não altere migrations antigas que já possam ter sido aplicadas.
- Prefira criar uma nova migration quando uma mudança de esquema for realmente necessária.
- Não crie migrations para mudanças que possam ser implementadas corretamente sem alterar o esquema.
- Avalie chaves estrangeiras, índices, restrições e integridade referencial.
- Não armazene valores monetários em tipos de ponto flutuante binário.
- Preserve a semântica dos dados históricos.
- Considere transações e concorrência nas operações de escrita.
- Evite carregar grandes conjuntos de registros em memória quando o banco puder filtrar, ordenar ou agregar os dados.

Qualquer mudança de esquema deve ser justificada no relatório final.

## 6.4. APIs

- Siga os padrões existentes de endpoints.
- Preserve a compatibilidade dos contratos.
- Utilize DTOs de entrada e saída apropriados.
- Não exponha entidades JPA diretamente.
- Respeite o tratamento de erros existente.
- Valide os dados recebidos.
- Utilize códigos HTTP semanticamente apropriados.
- Atualize a documentação OpenAPI quando necessário.
- Evite endpoints duplicados com responsabilidades equivalentes.

Antes de alterar um contrato, procure todos os consumidores conhecidos, especialmente o frontend `aroma-sabor-web`.

## 6.5. Testes do backend

Adicione testes compatíveis com o risco da alteração.

Considere:

- Testes unitários para regras de negócio.
- Testes de integração para endpoints.
- Testes de persistência para consultas e restrições.
- Testes de transação e rollback quando relevantes.
- Testes de concorrência quando houver risco de atualizações simultâneas.
- Casos de sucesso, erro e limites.

Não substitua testes de integração por mocks quando o comportamento real do PostgreSQL for importante para a validação.

---

# 7. Regras específicas do frontend

Projeto: `aroma-sabor-web`

## 7.1. Tecnologias e organização

Utilize a stack e a estrutura já existentes.

- React.
- TypeScript.
- Vite.
- React Router.
- TanStack Query.
- Axios.
- React Hook Form.
- Zod.
- CSS Modules.
- Lucide React.
- vite-plugin-pwa.

### Proibição de Tailwind

Não introduza Tailwind CSS.

Não migre componentes para Tailwind.

Não adicione classes ou configurações de Tailwind.

Utilize CSS Modules e os padrões de estilização já presentes no projeto.

## 7.2. Componentes e organização

- Reutilize componentes existentes quando forem adequados.
- Siga os padrões atuais de páginas, hooks, serviços, contextos e componentes.
- Evite componentes excessivamente grandes.
- Evite criar abstrações que sejam utilizadas apenas uma vez sem benefício claro.
- Mantenha separação entre apresentação, acesso à API e lógica de interface.
- Preserve a acessibilidade e a semântica HTML.
- Utilize os componentes e ícones existentes.
- Mantenha o layout responsivo.

Não crie uma segunda implementação de uma funcionalidade já existente sem uma justificativa concreta.

## 7.3. Identidade visual

Respeite a identidade visual já utilizada pelo Aroma Sabor OS.

Referências de cores existentes:

- Primária: `#8B0D12`
- Primária hover: `#A51218`
- Primária escura: `#6D090D`
- Sucesso: `#16A34A`
- Atenção: `#F59E0B`
- Erro: `#DC2626`
- Fundo: `#111827`
- Superfície: `#1F2937`
- Superfície secundária: `#374151`
- Texto principal: `#F8FAFC`
- Texto secundário: `#CBD5E1`
- Bordas: `#374151`

Consulte os tokens e estilos existentes antes de utilizar esses valores diretamente.

Se já houver variáveis CSS para essas cores, reutilize-as.

Não crie uma identidade visual paralela.

Referências de layout existentes:

- Sidebar desktop de aproximadamente 220 px.
- Topbar de aproximadamente 64 px.

Respeite as dimensões e os padrões atuais da aplicação.

## 7.4. Acesso à API

- Utilize os serviços e a configuração Axios existentes.
- Respeite os contratos reais do backend.
- Não presuma nomes de propriedades.
- Não duplique regras de negócio do backend.
- Não use valores fixos para simular dados reais.
- Trate erros HTTP de maneira consistente.
- Considere estados de carregamento, erro, ausência de dados e sucesso.
- Utilize TanStack Query conforme os padrões existentes.
- Invalide somente as consultas afetadas por uma mutação.
- Evite atualizações otimistas quando puderem comprometer a consistência do estoque ou das vendas.

Quando um contrato não estiver claro, consulte o backend antes de implementar.

## 7.5. Formulários

- Reutilize React Hook Form e Zod conforme os padrões atuais.
- Valide os dados antes de enviá-los.
- Evite validações contraditórias com o backend.
- Exiba mensagens úteis para o operador.
- Evite permitir múltiplos envios acidentais.
- Preserve os dados informados pelo usuário quando uma operação falhar, sempre que apropriado.

## 7.6. Caixa e scanner

O sistema possui integração com leitor de código de barras USB do tipo keyboard wedge.

Respeite a implementação existente de:

- BarcodeScannerProvider.
- ScannerContext.
- useBarcodeScanner.
- scannerDetector.

Antes de alterar o comportamento do scanner:

- Entenda como os eventos de teclado são detectados.
- Preserve a compatibilidade com digitação manual.
- Evite conflitos com campos de formulário.
- Preserve a limpeza de listeners.
- Não crie listeners globais redundantes.
- Não prejudique o fluxo operacional do caixa.

O fluxo de vendas é uma funcionalidade crítica e deve receber testes apropriados.

## 7.7. PWA

A aplicação utiliza vite-plugin-pwa.

- Preserve a configuração existente.
- Não altere o comportamento de atualização do service worker sem necessidade.
- Não trate arquivos gerados como arquivos-fonte.
- Inspecione a configuração antes de modificar a estratégia de cache.
- Considere os impactos de mudanças sobre a experiência offline, quando aplicável.

## 7.8. Testes do frontend

Adicione ou atualize testes para as funcionalidades alteradas.

Priorize:

- Regras de negócio presentes nos hooks.
- Comportamento dos formulários.
- Chamadas à API.
- Tratamento de erros.
- Estados de carregamento e ausência de dados.
- Interações relevantes.
- Fluxos críticos de caixa, vendas e estoque.

Não remova testes para fazer a suíte passar.

Não altere snapshots ou mocks sem entender a razão da divergência.

---

# 8. Contratos entre frontend e backend

O frontend e o backend devem permanecer consistentes.

Quando uma tarefa afetar um contrato:

1. Identifique o endpoint e o DTO reais do backend.
2. Identifique os serviços e os tipos utilizados pelo frontend.
3. Compare os campos, tipos, valores permitidos e formatos de resposta.
4. Verifique regras de paginação, filtros, datas e tratamento de erros.
5. Atualize somente os projetos necessários.
6. Adicione testes para validar a integração ou o contrato relevante.

Não invente contratos com base apenas no nome de uma funcionalidade.

Não altere silenciosamente nomes ou tipos de propriedades.

Considere especialmente:

- Identificadores numéricos.
- Valores monetários.
- Datas e horários.
- Paginação.
- Estados de produtos.
- Tipos de movimentação de estoque.
- Estados de turno.
- Campos opcionais e valores nulos.
- Formatos de erros HTTP.

Quando houver incompatibilidade entre frontend e backend, identifique a origem e proponha a menor correção segura.

---

# 9. Segurança e configuração

- Não inclua credenciais em código-fonte.
- Não registre senhas, tokens ou dados sensíveis em logs.
- Não exponha configurações locais na aplicação pública.
- Respeite as variáveis de ambiente existentes.
- Não altere arquivos de configuração local sem necessidade.
- Não substitua configurações de desenvolvimento, teste e produção indiscriminadamente.
- Não desative validações ou controles de segurança para facilitar testes.
- Não introduza dependências com permissões ou comportamentos desnecessários.

Antes de alterar arquivos de configuração, identifique quais ambientes são afetados.

---

# 10. Comandos de validação

Execute os comandos a partir do diretório correto de cada projeto.

## 10.1. Backend

Diretório: `mercadinho`

Testes:

```bash
./gradlew test
```

Compilação:

```bash
./gradlew compileJava
```

Verificação do diff:

```bash
git diff --check
```

Execute os testes relevantes e, ao final, a suíte completa quando viável.

Não afirme que os testes passaram sem observar o resultado real.

Se a execução depender de PostgreSQL, Docker, variáveis de ambiente ou outro serviço indisponível, informe a limitação.

## 10.2. Frontend

Diretório: `aroma-sabor-web`

Testes:

```bash
npm test
```

Build:

```bash
npm run build
```

Lint:

```bash
npm run lint
```

Verificação do diff:

```bash
git diff --check
```

Consulte `package.json` antes de executar os comandos.

Se algum script não existir ou tiver outra finalidade, identifique o comando correto definido pelo projeto.

Diferencie falhas introduzidas pela tarefa de falhas preexistentes.

Não altere código fora do escopo apenas para corrigir problemas preexistentes encontrados durante a validação.

## 10.3. Validação dos dois projetos

Quando uma tarefa alterar ambos os projetos:

- Execute os testes relevantes em cada projeto.
- Execute os builds aplicáveis.
- Verifique a compatibilidade entre os contratos.
- Inspecione o diff de ambos.
- Informe os resultados separadamente.

Não considere uma integração validada apenas porque o backend compila e o frontend compila individualmente.

---

# 11. Estratégia de implementação por etapas

O projeto é desenvolvido de forma incremental.

Cada tarefa deve:

1. Ter um escopo claro.
2. Reutilizar as implementações existentes.
3. Fazer a menor alteração segura possível.
4. Incluir testes proporcionais ao risco.
5. Preservar funcionalidades concluídas.
6. Ser validada antes do início da próxima etapa.

Não implemente automaticamente fases futuras.

Não antecipe funcionalidades fora do escopo para evitar retrabalho hipotético.

Se identificar uma dependência entre fases, documente-a e aguarde a definição do escopo quando necessário.

Se uma tarefa revelar um problema estrutural relevante, explique o problema e proponha uma solução antes de ampliar significativamente a implementação.

---

# 12. Relatório obrigatório ao finalizar

Ao terminar cada tarefa, apresente um relatório objetivo contendo:

## Resumo

- O que foi implementado.
- Qual era o objetivo da mudança.
- Quais comportamentos foram alterados.

## Escopo

- Projeto ou projetos modificados.
- Arquivos criados.
- Arquivos alterados.
- Migrations criadas, se houver.

## Decisões técnicas

- Abordagem adotada.
- Regras de negócio afetadas.
- Contratos de API afetados.
- Impactos sobre compatibilidade e persistência.

## Validação

- Testes executados.
- Quantidade de testes aprovados, quando disponível.
- Testes que falharam.
- Resultado da compilação ou build.
- Resultado do lint, quando aplicável.
- Resultado do `git diff --check`.

Diferencie claramente:
- Testes executados e aprovados.
- Testes executados e reprovados.
- Testes não executados.

## Pendências

- Problemas encontrados.
- Limitações conhecidas.
- Falhas preexistentes relevantes.
- Recomendações para a próxima etapa.

## Git

- Informe o estado final dos arquivos modificados.
- Identifique alterações inesperadas.
- Não faça commit ou push automaticamente.
- Não descarte alterações locais.

Aguarde a revisão antes de iniciar a próxima fase.

---

# 13. Regra final

O código existente, os testes, os contratos reais e as instruções específicas da tarefa são as principais fontes de verdade.

Este arquivo orienta o desenvolvimento, mas não substitui a análise do código.

Em caso de conflito entre estas instruções e uma decisão arquitetural ou regra de negócio já implementada, investigue o contexto e explique o conflito antes de modificar comportamentos importantes.

O objetivo não é apenas fazer a funcionalidade funcionar, mas manter o Aroma Sabor OS consistente, confiável, testável e sustentável ao longo de sua evolução.