# CatalogHub — Gestão Multicanal de Catálogo e Marketplaces

Solução acadêmica para a **Dor 2 — Gestão de Catálogo e Marketplaces**.
Pequenos e médios lojistas vendem em vários canais (Mercado Livre, Magalu, Shopee, TikTok Shop, loja própria) e sofrem com
cadastros duplicados, estoque dessincronizado e emissão fiscal manual. O CatalogHub centraliza tudo em um único lugar.

> As integrações com os marketplaces e a emissão de NF-e são **simuladas** (sem chamadas reais, sem valor fiscal).
> A arquitetura, porém, segue o padrão usado em soluções reais: cada canal é um *conector* intercambiável.

## Como executar

Requisito: Java 8 ou superior.

```
java -jar dist\CatalogHub.jar
```
ou dê duplo clique em `executar.bat` (ou no próprio `.jar`).

Na primeira execução são criados dados de exemplo (10 produtos, anúncios em 5 canais e pedidos).
Os dados ficam em `dados\cataloghub.dat`. O botão **Resetar demonstração** recria tudo.

Para recompilar: `build.bat` (precisa de um JDK 8+; ajuste `JDK_HOME` no script se necessário).

### Versão Android (celular)

O arquivo `dist\CatalogHub.apk` roda em Android 7.0 ou superior. Para instalar:

1. Envie o `CatalogHub.apk` para o celular (WhatsApp, Google Drive, e-mail ou cabo USB).
2. Toque no arquivo no celular. Se o Android pedir, permita **"Instalar apps desconhecidos"** para o app usado para abrir o arquivo.
3. Abra o **CatalogHub**. Os dados de exemplo são criados no primeiro uso.

Com o celular no cabo e a depuração USB ativa, também dá para instalar com
`adb install -r dist\CatalogHub.apk`. Para regerar o APK, use `build-android.bat`.

As versões desktop e Android usam **o mesmo código de regras de negócio** (pasta `core/`). Só a interface muda:
Swing no desktop e Android nativo no celular (abas Painel, Catálogo, Canais, Pedidos, Notas fiscais e Log).

## Como a solução resolve o problema

| Problema | Solução no CatalogHub |
|---|---|
| Cadastro repetido em cada canal | **Catálogo central**: cadastra uma vez e publica em todos os canais com um clique. Importação/exportação via CSV. |
| Estoque dessincronizado / venda sem estoque (*overselling*) | **Estoque central como fonte da verdade**: cada venda, ajuste ou cancelamento é propagado na hora para todos os canais. Pedidos sem saldo são recusados. |
| Emissão fiscal manual | **NF-e automática**: todo pedido recebido gera NF-e (chave de 44 dígitos com DV, protocolo, XML no layout 4.00 e DANFE). Cancelar o pedido cancela a nota. |
| Falhas de API dos marketplaces | **Fila de reprocessamento**: erros (429, 503, 504) ficam marcados e podem ser reprocessados; canal desconectado acumula pendências e sincroniza ao reconectar. |
| Retrabalho em títulos, preços e categorias | **Assistente de IA**: sugere categoria e NCM, gera descrição, cria título otimizado por canal (respeitando o limite de caracteres) e calcula o preço por canal considerando comissão, taxa fixa, imposto e margem alvo. |
| Falta de visão do negócio | **Dashboard**: faturamento e comissões por canal, gráfico, previsão de ruptura de estoque e insights automáticos. |

## O que cada aba faz (visão do usuário)

O lojista trabalha em **um lugar só**: cadastra uma vez, o estoque se ajusta sozinho em todos os marketplaces e a nota fiscal sai automaticamente.

| Aba | Pergunta que responde | Para que serve | Papel na solução |
|---|---|---|---|
| **Dashboard** (Painel) | "Como está minha loja hoje?" | Resumo de produtos, estoque, pedidos, faturamento e notas; vendas e comissões por marketplace; gráfico; alertas do assistente (o que vai acabar, preço ruim, produto fora de algum site). | Visão e controle do negócio inteiro em segundos. |
| **Catálogo central** | "Meus produtos, cadastrados uma vez só" | Cadastrar e editar produtos; "Preencher com IA" completa categoria, código fiscal, descrição e preço ideal por site; ajustar estoque; importar planilha. | Acaba com o cadastro repetido: um cadastro alimenta todos os canais. |
| **Canais & Anúncios** | "Meus produtos estão certos em cada site?" | Ligar/desligar cada marketplace; ver preço e estoque que o cliente vê em cada site; situação de cada anúncio (Sincronizado, Pendente, Erro, Pausado); tentar de novo o que falhou. | Todos os sites mostram a informação certa, mesmo quando um marketplace cai. |
| **Pedidos** | "Todas as minhas vendas em uma fila só" | Vendas de todos os sites juntas; a cada venda o app confere o estoque, dá baixa, atualiza todos os sites e emite a nota; marcar enviado; cancelar (estoque volta). | Mostra tudo funcionando junto, sem ninguém digitar nada. |
| **Notas fiscais** | "Minhas notas, sem trabalho manual" | Notas geradas automaticamente; DANFE (versão de papel) e XML (para o contador); compartilhar no celular. | Acaba com a emissão manual de notas. |
| **Log** | "O que aconteceu e quando?" | Histórico com data e hora de cadastros, vendas, recusas, atualizações, notas e falhas. | Segurança e transparência. |

**Como tudo se junta:** o Catálogo concentra os produtos, os Canais levam esses produtos para todos os marketplaces, os Pedidos trazem as vendas de volta para um lugar só, as Notas fiscais saem sozinhas, o Dashboard mostra o resultado e o Log guarda o histórico. Tudo para que o lojista venda em vários lugares com o esforço de vender em um só.

A explicação completa, aba por aba, está no capítulo 2 do [Relatorio-CatalogHub.pdf](Relatorio-CatalogHub.pdf).

## Arquitetura

```
src/br/com/cataloghub              interface DESKTOP (Swing)
├── Main.java                      ponto de entrada
└── ui/                            telas (Dashboard, Catálogo, Canais, Pedidos, Notas, Log)

android/                           interface ANDROID (MainActivity) - projeto Gradle

core/src/br/com/cataloghub         regras de negócio COMPARTILHADAS
├── model/                         entidades (Produto, Anuncio, Pedido, NotaFiscal, Canal, Evento)
├── service/
│   ├── MarketplaceConnector       interface de integração (padrão Adapter)
│   ├── MockMarketplaceConnector   simula a API de cada marketplace (validações + falhas)
│   ├── SyncService                publicação e sincronização catálogo → canais
│   ├── PedidoService              recebimento de pedidos, baixa de estoque, cancelamento
│   ├── FiscalService              NF-e simulada (chave, XML, DANFE)
│   ├── IAService                  categorização, títulos, preços, previsão e insights
│   ├── Repositorio                estado + persistência local
│   └── DadosDemo                  dados de exemplo
```

Fluxo de um pedido:
`Marketplace (webhook) → PedidoService → baixa no estoque central → SyncService atualiza TODOS os canais → FiscalService emite NF-e`

**Evolução para produção (nuvem):** trocar `MockMarketplaceConnector` por conectores com as APIs oficiais (OAuth2/REST),
`Repositorio` por um banco gerenciado (PostgreSQL), receber pedidos via webhooks em uma API (Spring Boot/Ktor) hospedada na nuvem,
usar uma fila (SQS/RabbitMQ) para o reprocessamento, um emissor de NF-e homologado, e um modelo de linguagem (LLM) no `IAService`.

## Roteiro sugerido para a apresentação (5 min)

1. **Dashboard**: visão consolidada dos 5 canais e insights da IA.
2. **Catálogo central → + Novo produto**: digite só o nome (ex.: "Fone de ouvido gamer"), custo e estoque e clique em **Preencher com IA**: aparecem categoria, NCM, descrição e preço sugerido por canal. Salve e o produto é publicado em todos os canais.
3. Selecione o produto: a tabela de baixo mostra o título adaptado a cada marketplace, a margem real e o preço sugerido.
4. **Pedidos → Simular pedido** (ou ligue a **Simulação automática**): veja o estoque cair no catálogo **e em todos os canais**, e a NF-e ser gerada.
5. **Notas fiscais → Ver DANFE / Ver XML**.
6. **Canais & Anúncios**: desmarque "Conectado" em um canal, ajuste um estoque, reconecte e veja a fila sincronizar. Aumente a "falha simulada da API" para mostrar erros e o botão **Reprocessar erros**.
7. Ajuste o estoque de um produto para 0: todos os anúncios dele vão a 0 na hora e ele para de receber pedidos (overselling evitado). Se um canal estiver com estoque desatualizado, o pedido é **recusado** pelo hub. Veja tudo no **Log de integrações**.
8. **Importar CSV** com `exemplo-importacao.csv` para mostrar um cadastro em massa.
