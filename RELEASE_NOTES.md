Primeira versão do **Repeaters list 705**, para Android 8.0 ou superior.

- Base inicial offline com 589 repetidoras: 548 FM e 41 D-STAR, em 27 grupos.
- Cadastro, edição dos 17 campos do IC-705, exclusão e favoritos persistentes.
- Busca por texto, estado/grupo, banda, frequência, FM/D-STAR, CTCSS e proximidade.
- Localização do celular sob demanda ou coordenadas manuais; abertura no mapa.
- Importação de CSV com ponto e vírgula ou vírgula, UTF-8/BOM e Windows-1252.
- Revisão da mesclagem/substituição, proteção de edições e exclusões locais e opção de desfazer.
- Exportação completa ou dos resultados filtrados, preservando o esquema de 17 colunas, UTF-8 e CRLF.
- Consulta configurável do CSV no GitHub, manual ou diária ao abrir, com revisão antes de aplicar.
- Token opcional para repositórios privados, protegido pelo Android Keystore.

O repositório inicial é privado: configure em **Configurações** um token fine-grained limitado ao rpt-705 com permissão **Contents: Read-only**. A base embarcada independe desse acesso.

Copie o CSV exportado para `IC-705/Csv/RptList` no cartão do rádio e importe em `MENU → SET → SD Card → Import/Export → Import → Repeater List`.

A compatibilidade do arquivo é validada por testes de estrutura e preservação de todos os campos. A importação física no IC-705 ainda depende da validação do usuário. Os dados originais não foram verificados quanto à operação atual de cada repetidora.
