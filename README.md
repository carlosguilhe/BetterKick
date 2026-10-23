# BetterKick

Cliente Android não oficial para a Kick, com player nativo (ExoPlayer) e foco em estabilidade de qualidade e latência.

- Player nativo com qualidade automática estável, latência ajustável e modo só áudio
- Mini player, Picture-in-Picture e áudio em segundo plano
- Login, canais seguidos, favoritos, histórico e avisos de live
- Categorias, busca, clipes e transmissões anteriores
- Tema escuro, claro ou do sistema

## Instalar

Baixe o APK mais recente em [Releases](../../releases/latest) e instale no celular (Android 8.0 ou superior). Permita a instalação de apps desconhecidos quando o Android pedir.

## Compilar

```
./gradlew assembleRelease
```

A assinatura de release lê `keystore.properties` (fora do repositório). Sem ele, o build usa a chave de debug.

Projeto não afiliado à Kick.
