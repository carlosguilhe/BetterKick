# Privacidade

Resumo: **o BetterKick não tem servidor próprio, não tem análise de uso, não tem anúncios e não envia seus dados para os mantenedores.**

## O que fica no seu aparelho

Tudo isto é guardado **só no seu celular** e some quando você desinstala o app ou limpa os dados dele:

- A **sessão de login** que a Kick devolve (usada apenas para falar com a Kick em seu nome). **O app não vê nem guarda a sua senha**: o login acontece na página da própria Kick, dentro do app.
- Favoritos, canais silenciados, histórico de lives e buscas recentes.
- Preferências (qualidade, latência, tema, idioma, avisos e o que você escolheu por canal).
- Cache das listas (até 24 h) e a última velocidade de internet medida.
- Um **relatório de falha** local, se o app fechar sozinho. Ele **não é enviado** a ninguém; só sai do aparelho se você tocar em "Compartilhar".

## Com quem o app conversa

- **Kick** (`kick.com`, `web.kick.com`, a rede de entrega de vídeo e imagens da Kick e, no login com Google, as páginas de login do Google exibidas pela Kick): para listar lives, tocar vídeo, mostrar o chat e as ações da sua conta. Esses serviços têm as próprias políticas de privacidade.
- **GitHub** (`api.github.com`): para saber se há versão nova e baixar a atualização. O GitHub recebe o endereço de IP da requisição, como em qualquer acesso.
- **Cloudflare** (`speed.cloudflare.com`): **somente** quando você toca em "Testar velocidade".
- **TVs da sua rede (DLNA)**: **somente** quando você toca no botão de transmitir. O app procura aparelhos na rede local do seu Wi-Fi e, se você escolher um, envia a ele o endereço da live para a TV baixar o vídeo. Nada disso sai da sua rede local.

Não há outros destinos. Não há SDK de rastreamento, de anúncios ou de análise.

## Permissões do Android

- **Internet**: falar com os serviços acima.
- **Notificações**: avisar quando um canal entra ao vivo e mostrar os controles de mídia.
- **Serviço em primeiro plano (mídia)**: manter o áudio tocando com a tela apagada.
- **Instalar pacotes**: só para instalar as atualizações do próprio app, depois que você confirma.
- **Estado do Wi-Fi e multicast**: só para achar TVs na rede ao transmitir. O app também aceita conexões sem criptografia (HTTP), porque as TVs da rede local falam assim; todo o resto usa HTTPS.

## Seus controles

Em **Configurações** você pode limpar o histórico, as buscas, os favoritos e o cache, e desligar os avisos. Para apagar tudo, desinstale o app. Para encerrar a sessão, use **Sair da conta** no Perfil.

## Mudanças

Se isso mudar, este arquivo será atualizado no repositório.
