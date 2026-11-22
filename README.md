# Treino do Prédio

App de treino com gamificação, feito para a academia do prédio (rack de halteres coloridos, estação de polias/crossover, cadeira extensora, caneleiras, bola suíça, esteiras e elíptico).

## O que ele faz

- **Cadastro rápido**: idade, peso, altura, objetivo (emagrecer, ganhar massa, ficar mais forte, saúde), dias por semana e tempo por treino.
- **Cálculos**: IMC, metabolismo basal (Mifflin-St Jeor), gasto diário, meta de calorias, proteína, água, zona de frequência cardíaca do cardio, gasto por treino e previsão até o peso meta.
- **Plano automático**: divisão de treino de acordo com os dias (corpo inteiro A/B/C, superiores/inferiores ou empurrar/puxar/pernas), séries, repetições e descanso de acordo com o objetivo. As semanas 1 e 2 são de adaptação.
- **Carga sugerida e progressão**: sugere a carga inicial e, quando você completa todas as séries no máximo de repetições, sugere subir o peso.
- **26 exercícios animados** com passo a passo, dicas e erros comuns. O músculo que mais trabalha aparece em laranja.
- **Modo treino**: registro de séries, cronômetro de descanso em LED com aviso sonoro e combo de séries seguidas.
- **Gamificação**: XP e níveis com títulos, sequência de semanas batendo a meta (+10% de XP por semana), missões semanais, 22 conquistas, recordes pessoais, gráficos e calendário.

## Samsung Health (opcional)

Apps web não têm acesso direto ao Samsung Health (a Samsung só libera os dados para apps Android nativos). Por isso o app importa a exportação oficial:

1. Samsung Health › menu › Configurações › **Baixar dados pessoais**.
2. No app, Perfil › **Conectar Samsung Health** › escolha os arquivos `.csv` da pasta `Download/Samsung Health` (ou um `.zip` dela).

O app lê passos, batimentos, sono, peso e exercícios, guarda só um resumo por dia e usa isso para:
- calcular a zona do cardio pela fórmula de Karvonen, com a sua FC de repouso real;
- dar +15 XP por dia em que você bate a meta de passos, além de uma missão semanal de passos e 3 conquistas novas;
- sugerir um treino mais leve quando você dormiu pouco;
- mostrar gráficos de passos e sono, e as atividades feitas com o relógio ou o celular.

Para importar de novo e atualizar, basta repetir os passos.

## Arquivos

- `src/app.html`: código-fonte do app (página publicada como Artifact).
- `index.html`: versão completa para abrir direto no navegador ou hospedar (GitHub Pages). Gerada com `./build.sh`.

Os dados ficam salvos na conta Claude quando o app é aberto pelo link do Artifact; abrindo o `index.html`, ficam salvos no navegador (use "Copiar backup" no Perfil).

> Os cálculos são estimativas e não substituem avaliação médica ou de um profissional de educação física.
