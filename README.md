# Repeaters list 705

Aplicativo Android offline para gerenciar listas de repetidoras do Icom IC-705. Projeto de Clézio da Cunha Costa (PP5CI).

## Instalação

Baixe `Repeaters-list-705-v1.0.0.apk` em **Releases**. Android 8.0 (API 26) ou superior, incluindo Galaxy S24 Ultra. Não requer conexão com o rádio. O arquivo é assinado com certificado próprio do projeto, sem bibliotecas nativas dependentes de arquitetura.

## Base inicial e formato CSV

O arquivo fornecido `rpt_updated.csv` contém 589 registros, 548 FM e 41 DV, distribuídos por 27 grupos. O separador original é `;`, a codificação é UTF-8 sem BOM e as linhas usam CRLF. A última linha original não tem terminador; a exportação acrescenta o CRLF final. Nenhum campo original é alterado na exportação de uma base sem edições.

| Coluna | Conteúdo |
| --- | --- |
| Group No / Group Name | Número 1–50 e nome do grupo (até 16 caracteres) |
| Name / Sub Name | Nome e subnome (até 16 caracteres) |
| Repeater Call Sign / Gateway Call Sign | Indicativos, incluindo espaços e módulo D-STAR (até 8 caracteres) |
| Frequency / Dup / Offset | MHz, OFF/DUP-/DUP+, desvio em MHz |
| Mode | FM ou DV (D-STAR) |
| TONE / Repeater Tone | OFF/TONE/TSQL e tom CTCSS em Hz |
| RPT1USE | YES ou NO |
| Position / Latitude / Longitude | None/Approximate/Exact, graus decimais |
| UTC Offset | Fuso, por exemplo -3:00 |

A importação aceita `;` ou `,`, cabeçalhos reordenados, aspas CSV, BOM UTF-8 e Windows-1252. Rejeita linhas inválidas, duplicidades de identidade, inconsistências de nomes de grupos e bases acima de 2500 registros. Importações inválidas não alteram a base.

A chave de correspondência é **indicativo + frequência RX + modo**; quando não há indicativo, usa **grupo + cidade + frequência RX + modo**. É possível ter o mesmo indicativo em bandas/modos diferentes. A identidade original é mantida durante edições locais para evitar duplicação na sincronização.

A mesclagem preserva registros ausentes do arquivo e, por padrão, edições e exclusões locais. O usuário revisa contagens e pode optar por atualizar campos locais ou substituir toda a lista. A substituição descarta as exclusões locais e mantém favoritos correspondentes. Apenas a última alteração pode ser desfeita.

## Atualizações pelo GitHub

Fonte padrão: `cleziotc/rpt-705`, branch `main`, arquivo `data/rpt_updated.csv`. Substitua esse arquivo com uma versão válida para distribuir novas bases; mantenha o asset embarcado se desejar atualizar também o conteúdo inicial de futuras instalações.

O repositório é privado. O usuário configura um token fine-grained com permissão `Contents: Read-only` apenas para este repositório. Não há credencial embutida no APK. Para repositórios públicos, deixe o token vazio. Repositório, branch e caminho são configuráveis. O token é criptografado com AES-GCM no Android Keystore; backup Android está desabilitado. Dados e tokens são removidos ao limpar dados/desinstalar o aplicativo. Use exportação como backup portátil.

A consulta automática é opcional e ocorre ao abrir se a última aplicação de uma consulta tiver mais de 24 horas. A atualização nunca é aplicada sem revisão. Não há publicação automática das edições do celular no GitHub.

## Exportação para o IC-705

Exporte a base completa ou o resultado filtrado. O padrão é ponto e vírgula, igual ao anexo, com opção de vírgula. São mantidas exatamente as 17 colunas; favoritos, referências de localização do celular e informações internas não entram no CSV.

Copie para `IC-705/Csv/RptList` em cartão formatado pelo rádio e importe em `MENU → SET → SD Card → Import/Export → Import → Repeater List`. Consulte o manual do equipamento para as opções apresentadas e faça backup da lista existente.

Não foi realizado teste físico de importação no rádio. A presença de um registro não comprova que a repetidora esteja ativa. Os dados da planilha original foram preservados.

## Implementação e build

Activity Java com armazenamento JSON atômico, codec CSV nativo e interface HTML/CSS/JS embarcada em origem HTTPS local interceptada. Sem serviços externos obrigatórios, analytics, bibliotecas de terceiros no APK ou acesso amplo ao armazenamento. Importar/exportar usa o seletor de arquivos Android. Localização só é solicitada ao usar o botão correspondente; cálculo de distância local.

Build padrão com Java 17, Android SDK 35 e Gradle 8.11.1:

```sh
gradle assembleDebug assembleDebugAndroidTest lintDebug
javac -d /tmp/rpt-tests app/src/main/java/br/com/pp5ci/repeaters705/Csv.java tests/CsvTest.java
java -cp /tmp/rpt-tests CsvTest data/rpt_updated.csv
node tests/core.test.cjs data/rpt_updated.csv
```

Para uma release futura com a mesma assinatura, use o backup privado da chave do projeto, nunca o adicione ao Git. Variáveis aceitas pelo Gradle: `RPT_KEYSTORE` (caminho absoluto) e `RPT_STORE_PASSWORD`; alias `rpt705`. Incremente versionCode/versionName, execute os testes e atualize o workflow/release. O APK inicial em `dist` é a distribuição assinada; o workflow verifica assinatura, SHA-256 e assets, compila o código-fonte e executa o teste no emulador antes de publicar.

## Verificações

`CsvTest` verifica os 589 registros, roundtrip dos dois separadores, BOM, precisão, limites, duplicidades, nomes de grupos e preservação byte a byte dos campos originais. `core.test.cjs` cobre pesquisa, acentos, distância, mesclagem, edição local, exclusões, substituição e favoritos. O teste de instrumentação `SmokeTest` exercita a Activity real, WebView, bridge, cadastro, edição, exclusão, persistência em disco, desfazer e criptografia no Keystore. `tests/ui.cjs` testa a interface em viewports de celular e tablet com bridge simulada.
