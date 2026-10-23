package com.newkick.app

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/**
 * Idioma do app (português é o original; inglês e espanhol são traduzidos por este dicionário).
 * Todo `Text(...)` e `Toast` do app passa por [tr]: o que não está no dicionário aparece como está,
 * então nomes de canais, títulos de live e outros textos de terceiros nunca são mexidos.
 */
object Strings {
    /** "pt", "en" ou "es" (já resolvido a partir da escolha do usuário). */
    var lang by mutableStateOf("pt")

    /** Escolha do usuário: "system", "pt", "en" ou "es". */
    fun apply(choice: String) {
        lang = when (choice) {
            "pt", "en", "es" -> choice
            else -> when (Locale.getDefault().language) {
                "en" -> "en"
                "es" -> "es"
                else -> "pt"
            }
        }
    }

    fun init(context: Context) = apply(Prefs(context).uiLang)
}

/** Traduz um texto do app (português) para o idioma escolhido. */
fun tr(text: String): String {
    val lang = Strings.lang
    if (lang == "pt") return text
    val pair = DICTIONARY[text] ?: return text
    return if (lang == "en") pair.first else pair.second
}

/** Mesmo `Text` do Material 3, mas traduz textos fixos do app. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        tr(text), modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration,
        textAlign, lineHeight, overflow, softWrap, maxLines, minLines, onTextLayout, style,
    )
}

/** Mesmo `Toast` do Android, traduzindo o texto. */
object Toast {
    const val LENGTH_SHORT = android.widget.Toast.LENGTH_SHORT
    const val LENGTH_LONG = android.widget.Toast.LENGTH_LONG

    fun makeText(context: Context, text: CharSequence, duration: Int): android.widget.Toast =
        android.widget.Toast.makeText(context, tr(text.toString()), duration)
}

private fun e(pt: String, en: String, es: String) = pt to (en to es)

private val DICTIONARY: Map<String, Pair<String, String>> = listOf(
    // Abas e navegação
    e("Home", "Home", "Inicio"),
    e("Categorias", "Categories", "Categorías"),
    e("Seguindo", "Following", "Siguiendo"),
    e("Destaques", "Featured", "Destacados"),
    e("Perfil", "Profile", "Perfil"),
    e("Voltar", "Back", "Volver"),
    e("Todos", "All", "Todos"),
    e("Tudo", "All", "Todo"),
    e("Canais", "Channels", "Canales"),
    e("Ao vivo", "Live", "En vivo"),
    e("Offline", "Offline", "Sin conexión"),
    e("Ao vivo agora", "Live now", "En vivo ahora"),
    // Avisos gerais
    e("Falha ao carregar.", "Failed to load.", "Error al cargar."),
    e("Sem conexão: mostrando o que já foi carregado", "No connection: showing what was already loaded", "Sin conexión: mostrando lo que ya se cargó"),
    e("A Kick pode ter mudado algo no servidor", "Kick may have changed something on its servers", "Kick puede haber cambiado algo en su servidor"),
    e("Tentar de novo", "Try again", "Intentar de nuevo"),
    e("Carregando...", "Loading...", "Cargando..."),
    e("Carregando", "Loading", "Cargando"),
    e("Carregar mais", "Load more", "Cargar más"),
    e("Atualizar", "Update", "Actualizar"),
    e("Entrar", "Sign in", "Iniciar sesión"),
    e("Cancelar", "Cancel", "Cancelar"),
    e("Limpar", "Clear", "Borrar"),
    e("Apagar", "Delete", "Borrar"),
    e("Compartilhar", "Share", "Compartir"),
    e("Compartilhar canal", "Share channel", "Compartir canal"),
    e("Compartilhar relatório", "Share report", "Compartir informe"),
    e("Descartar", "Dismiss", "Descartar"),
    // Início e busca
    e("Buscar canal ou categoria", "Search channel or category", "Buscar canal o categoría"),
    e("Abrir", "Open", "Abrir"),
    e("Buscas recentes", "Recent searches", "Búsquedas recientes"),
    e("Nada encontrado.", "Nothing found.", "No se encontró nada."),
    e("Em alta agora", "Trending now", "En auge ahora"),
    e("Seguindo ao vivo", "Following live", "Siguiendo en vivo"),
    e("Recomendado para você", "Recommended for you", "Recomendado para ti"),
    e("Favoritos e recentes", "Favorites and recent", "Favoritos y recientes"),
    e("Categorias populares", "Popular categories", "Categorías populares"),
    e("Mais lives", "More lives", "Más directos"),
    e("Ver todos os destaques", "See all featured", "Ver todos los destacados"),
    e("Nenhuma live agora.", "No lives right now.", "Ningún directo ahora."),
    e("● AO VIVO", "● LIVE", "● EN VIVO"),
    e("Medindo...", "Measuring...", "Midiendo..."),
    e("Testar velocidade", "Test speed", "Probar velocidad"),
    // Seguindo
    e("Entre na sua conta da Kick para ver os canais que você segue e quem está ao vivo.", "Sign in to your Kick account to see the channels you follow and who is live.", "Inicia sesión en tu cuenta de Kick para ver los canales que sigues y quién está en vivo."),
    e("Sua sessão expirou. Entre de novo.", "Your session expired. Sign in again.", "Tu sesión expiró. Inicia sesión de nuevo."),
    e("Filtrar quem eu sigo", "Filter who I follow", "Filtrar a quién sigo"),
    e("Você ainda não segue ninguém.", "You don't follow anyone yet.", "Aún no sigues a nadie."),
    e("Ninguém encontrado.", "Nobody found.", "No se encontró a nadie."),
    // Player
    e("Pausar", "Pause", "Pausar"),
    e("Tocar", "Play", "Reproducir"),
    e("Baixa (4 s)", "Low (4 s)", "Baja (4 s)"),
    e("Equilibrada (10 s)", "Balanced (10 s)", "Equilibrada (10 s)"),
    e("Estável (20 s)", "Stable (20 s)", "Estable (20 s)"),
    e("Falha ao carregar o canal.", "Failed to load the channel.", "Error al cargar el canal."),
    e("Timer encerrado: live pausada.", "Timer finished: live paused.", "Temporizador terminado: directo en pausa."),
    e("Só áudio", "Audio only", "Solo audio"),
    e("Conexão lenta para essa qualidade.", "Slow connection for this quality.", "Conexión lenta para esta calidad."),
    e("Ignorar", "Dismiss", "Ignorar"),
    e("Favorito", "Favorite", "Favorito"),
    e("Configurações", "Settings", "Ajustes"),
    e("QUALIDADE", "QUALITY", "CALIDAD"),
    e("Automática (estável)", "Automatic (stable)", "Automática (estable)"),
    e("Máxima (travada)", "Maximum (locked)", "Máxima (fija)"),
    e("LATÊNCIA", "LATENCY", "LATENCIA"),
    e("TIMER PARA DORMIR", "SLEEP TIMER", "TEMPORIZADOR PARA DORMIR"),
    e("Desligado", "Off", "Apagado"),
    e("EXIBIÇÃO", "DISPLAY", "PANTALLA"),
    e("Chat sobre o vídeo (paisagem)", "Chat over video (landscape)", "Chat sobre el vídeo (horizontal)"),
    e("Estatísticas", "Statistics", "Estadísticas"),
    e("VOLTAR AO VIVO", "BACK TO LIVE", "VOLVER AL DIRECTO"),
    e("AO VIVO", "LIVE", "EN VIVO"),
    e("Ocultar chat", "Hide chat", "Ocultar chat"),
    e("Mostrar chat", "Show chat", "Mostrar chat"),
    e("Sair da tela cheia", "Exit full screen", "Salir de pantalla completa"),
    e("Tela cheia", "Full screen", "Pantalla completa"),
    e("Seguir", "Follow", "Seguir"),
    e("Aguarde o chat carregar e tente de novo.", "Wait for the chat to load and try again.", "Espera a que cargue el chat e inténtalo de nuevo."),
    e("Entre na sua conta primeiro.", "Sign in first.", "Inicia sesión primero."),
    e("A live terminou.", "The live ended.", "El directo terminó."),
    e("Lado a lado", "Side by side", "En paralelo"),
    // Menu de segurar
    e("Assistir", "Watch", "Ver"),
    e("Clipes e transmissões anteriores", "Clips and past broadcasts", "Clips y emisiones anteriores"),
    e("Deixar de seguir", "Unfollow", "Dejar de seguir"),
    e("Seguir (verificando...)", "Follow (checking...)", "Seguir (verificando...)"),
    e("Reativar avisos de live", "Turn live alerts back on", "Reactivar avisos de directo"),
    e("Silenciar avisos de live", "Mute live alerts", "Silenciar avisos de directo"),
    e("Remover dos favoritos", "Remove from favorites", "Quitar de favoritos"),
    e("Favoritar (estrela)", "Favorite (star)", "Marcar favorito (estrella)"),
    e("Assistir lado a lado (escolher a 2ª live)", "Watch side by side (pick the 2nd live)", "Ver en paralelo (elegir el 2.º directo)"),
    e("Cancelar lado a lado", "Cancel side by side", "Cancelar en paralelo"),
    e("Agora segure o card da segunda live.", "Now long-press the second live's card.", "Ahora mantén pulsada la segunda tarjeta."),
    // Configurações
    e("REPRODUÇÃO", "PLAYBACK", "REPRODUCCIÓN"),
    e("Qualidade padrão", "Default quality", "Calidad predeterminada"),
    e("Automática", "Automatic", "Automática"),
    e("Máxima", "Maximum", "Máxima"),
    e("Distância do ao vivo", "Distance from live", "Distancia del directo"),
    e("Economia de dados", "Data saver", "Ahorro de datos"),
    e("No 4G/5G, o modo automático não passa de 480p", "On 4G/5G, automatic mode stays at 480p or below", "En 4G/5G, el modo automático no pasa de 480p"),
    e("Gestos de brilho e volume", "Brightness and volume gestures", "Gestos de brillo y volumen"),
    e("Arraste na tela cheia", "Swipe in full screen", "Desliza en pantalla completa"),
    e("Chat sobre o vídeo", "Chat over video", "Chat sobre el vídeo"),
    e("Em paisagem, translúcido em vez de ao lado", "In landscape, translucent instead of side by side", "En horizontal, translúcido en vez de al lado"),
    e("Estatísticas de reprodução", "Playback statistics", "Estadísticas de reproducción"),
    e("Mostra buffer, rede e quadros perdidos", "Shows buffer, network and dropped frames", "Muestra búfer, red y fotogramas perdidos"),
    e("APARÊNCIA", "APPEARANCE", "APARIENCIA"),
    e("Tema", "Theme", "Tema"),
    e("Escuro", "Dark", "Oscuro"),
    e("Claro", "Light", "Claro"),
    e("Preto", "Black", "Negro"),
    e("Sistema", "System", "Sistema"),
    e("Cores do sistema", "System colors", "Colores del sistema"),
    e("Fundos com as cores do papel de parede (Material You)", "Backgrounds with wallpaper colors (Material You)", "Fondos con los colores del fondo de pantalla (Material You)"),
    e("Vibração", "Vibration", "Vibración"),
    e("Toque leve ao segurar, trocar de aba e confirmar", "Light tap on long-press, tab change and confirmations", "Toque suave al mantener, cambiar de pestaña y confirmar"),
    e("Idioma do app", "App language", "Idioma de la app"),
    e("Idioma das listas", "List language", "Idioma de las listas"),
    e("AVISOS", "ALERTS", "AVISOS"),
    e("Favoritos (estrela) ao vivo", "Favorites (star) live", "Favoritos (estrella) en vivo"),
    e("Avisa quando um favorito entrar ao vivo", "Alerts when a favorite goes live", "Avisa cuando un favorito entra en vivo"),
    e("Quem eu sigo ao vivo", "People I follow live", "A quién sigo en vivo"),
    e("Canais que você segue na Kick", "Channels you follow on Kick", "Canales que sigues en Kick"),
    e("Entre na conta para usar", "Sign in to use", "Inicia sesión para usar"),
    e("DADOS", "DATA", "DATOS"),
    e("Salvar backup dos meus dados", "Save a backup of my data", "Guardar copia de mis datos"),
    e("Restaurar backup", "Restore backup", "Restaurar copia"),
    e("Limpar histórico de lives", "Clear live history", "Borrar historial de directos"),
    e("Histórico limpo.", "History cleared.", "Historial borrado."),
    e("Limpar buscas recentes", "Clear recent searches", "Borrar búsquedas recientes"),
    e("Buscas limpas.", "Searches cleared.", "Búsquedas borradas."),
    e("Remover todos os favoritos", "Remove all favorites", "Quitar todos los favoritos"),
    e("Favoritos removidos.", "Favorites removed.", "Favoritos quitados."),
    e("Limpar cache de listas", "Clear list cache", "Borrar caché de listas"),
    e("Cache limpo.", "Cache cleared.", "Caché borrada."),
    e("Backup salvo.", "Backup saved.", "Copia guardada."),
    e("Não consegui salvar o backup.", "Couldn't save the backup.", "No pude guardar la copia."),
    e("Dados restaurados.", "Data restored.", "Datos restaurados."),
    e("Esse arquivo não é um backup do BetterKick.", "That file isn't a BetterKick backup.", "Ese archivo no es una copia de BetterKick."),
    e("ATUALIZAÇÕES", "UPDATES", "ACTUALIZACIONES"),
    e("Procurando atualização…", "Checking for updates…", "Buscando actualizaciones…"),
    e("Procurar atualização", "Check for updates", "Buscar actualizaciones"),
    e("Não consegui consultar o GitHub.", "Couldn't reach GitHub.", "No pude consultar GitHub."),
    e("Você está na versão mais recente.", "You're on the latest version.", "Tienes la versión más reciente."),
    e("SUPORTE", "SUPPORT", "SOPORTE"),
    e("Relatório da última falha", "Last crash report", "Informe del último fallo"),
    e("Nenhuma falha registrada.", "No crashes recorded.", "Ningún fallo registrado."),
    e("Última falha", "Last crash", "Último fallo"),
    // Atualização do app
    e("Melhorias e correções.", "Improvements and fixes.", "Mejoras y correcciones."),
    e("Baixando…", "Downloading…", "Descargando…"),
    e("Depois", "Later", "Después"),
    e("Permita instalar apps do BetterKick e toque em Atualizar de novo.", "Allow BetterKick to install apps and tap Update again.", "Permite que BetterKick instale apps y toca Actualizar de nuevo."),
    e("Não consegui baixar a atualização. Tente de novo.", "Couldn't download the update. Try again.", "No pude descargar la actualización. Inténtalo de nuevo."),
    // Histórico e boas-vindas
    e("Histórico de lives", "Live history", "Historial de directos"),
    e("agora há pouco", "just now", "hace un momento"),
    e("Nada por aqui ainda. As lives que você assistir aparecem nesta lista.", "Nothing here yet. Lives you watch show up in this list.", "Nada por aquí todavía. Los directos que veas aparecerán en esta lista."),
    e("Bem-vindo ao BetterKick", "Welcome to BetterKick", "Te damos la bienvenida a BetterKick"),
    e("Um jeito melhor de assistir às lives da Kick no celular: qualidade estável, mini player e áudio em segundo plano.", "A better way to watch Kick lives on your phone: stable quality, mini player and background audio.", "Una mejor forma de ver los directos de Kick en el móvil: calidad estable, mini reproductor y audio en segundo plano."),
    e("Assista do seu jeito", "Watch your way", "Mira a tu manera"),
    e("Voltar vira mini player. Segure e arraste o vídeo para baixo, arraste para fora para fechar, e use picture-in-picture ou só áudio.", "Going back turns it into a mini player. Long-press and drag the video down, drag it off-screen to close, and use picture-in-picture or audio only.", "Volver lo convierte en mini reproductor. Mantén y arrastra el vídeo hacia abajo, arrástralo fuera para cerrar y usa imagen en imagen o solo audio."),
    e("Entre e acompanhe quem você segue", "Sign in and keep up with who you follow", "Inicia sesión y sigue a quien sigues"),
    e("Faça login pela página oficial da Kick para ver quem está ao vivo e receber avisos. O app nunca vê sua senha.", "Sign in through Kick's official page to see who is live and get alerts. The app never sees your password.", "Inicia sesión desde la página oficial de Kick para ver quién está en vivo y recibir avisos. La app nunca ve tu contraseña."),
    e("Pular", "Skip", "Omitir"),
    e("Começar", "Get started", "Empezar"),
    e("Continuar", "Continue", "Continuar"),
    // Canal, clipes e transmissões
    e("Assistir ao vivo agora", "Watch live now", "Ver en vivo ahora"),
    e("Offline no momento", "Offline right now", "Sin conexión ahora"),
    e("Sem clipes.", "No clips.", "Sin clips."),
    e("Clipe", "Clip", "Clip"),
    e("Carregar mais clipes", "Load more clips", "Cargar más clips"),
    e("Sem transmissões anteriores salvas.", "No saved past broadcasts.", "Sin emisiones anteriores guardadas."),
    // Conta
    e("Ainda não encontrei sua sessão. Termine o login na página e tente de novo.", "I haven't found your session yet. Finish signing in on the page and try again.", "Aún no encuentro tu sesión. Termina de iniciar sesión en la página e inténtalo de nuevo."),
    e("Entrar na Kick", "Sign in to Kick", "Iniciar sesión en Kick"),
    e("Verificando...", "Checking...", "Verificando..."),
    e("Já entrei", "I'm signed in", "Ya entré"),
    e("Toque no ícone de pessoa (canto superior direito) para entrar com e-mail e senha. Eles são digitados na página oficial da Kick; o app não os vê.", "Tap the person icon (top right) to sign in with email and password. They are typed on Kick's official page; the app doesn't see them.", "Toca el icono de persona (arriba a la derecha) para entrar con correo y contraseña. Se escriben en la página oficial de Kick; la app no los ve."),
    e("Google e Apple não permitem login dentro de outros apps. Use e-mail e senha: se sua conta foi criada com o Google, toque em 'Esqueceu sua senha?' e crie uma senha com o mesmo e-mail.", "Google and Apple don't allow sign-in inside other apps. Use email and password: if your account was created with Google, tap 'Forgot your password?' and create a password with the same email.", "Google y Apple no permiten iniciar sesión dentro de otras apps. Usa correo y contraseña: si tu cuenta se creó con Google, toca '¿Olvidaste tu contraseña?' y crea una con el mismo correo."),
    e("Segurança e senha", "Security and password", "Seguridad y contraseña"),
    e("Notificações", "Notifications", "Notificaciones"),
    e("Preferências", "Preferences", "Preferencias"),
    e("Conexões", "Connections", "Conexiones"),
    e("Falha ao carregar a conta.", "Failed to load the account.", "Error al cargar la cuenta."),
    e("Entre na sua conta da Kick para ver quem você segue, seguir canais e conversar no chat.", "Sign in to your Kick account to see who you follow, follow channels and chat.", "Inicia sesión en tu cuenta de Kick para ver a quién sigues, seguir canales y charlar en el chat."),
    e("Sua sessão expirou.", "Your session expired.", "Tu sesión expiró."),
    e("Entrar de novo", "Sign in again", "Iniciar sesión de nuevo"),
    e("CONFIGURAÇÕES DA CONTA", "ACCOUNT SETTINGS", "AJUSTES DE LA CUENTA"),
    e("NO APP", "IN THE APP", "EN LA APP"),
    e("Configurações do app", "App settings", "Ajustes de la app"),
    e("Avisar quando quem eu sigo entrar ao vivo", "Alert when people I follow go live", "Avisar cuando alguien a quien sigo entre en vivo"),
    e("Checa a cada ~15 minutos", "Checks about every 15 minutes", "Comprueba cada ~15 minutos"),
    e("Sair da conta", "Sign out", "Cerrar sesión"),
    e("Sair da conta?", "Sign out?", "¿Cerrar sesión?"),
    e("Você vai precisar entrar de novo para ver quem você segue.", "You'll need to sign in again to see who you follow.", "Tendrás que iniciar sesión de nuevo para ver a quién sigues."),
    e("Sair", "Sign out", "Salir"),
    // Falhas
    e("O app fechou inesperadamente", "The app closed unexpectedly", "La app se cerró inesperadamente"),
    e("Da última vez algo deu errado. Quer compartilhar o relatório para eu corrigir?", "Something went wrong last time. Want to share the report so it can be fixed?", "La última vez algo salió mal. ¿Quieres compartir el informe para corregirlo?"),
    e("Canal não encontrado.", "Channel not found.", "Canal no encontrado."),
    e("A Kick bloqueou a requisição (403).", "Kick blocked the request (403).", "Kick bloqueó la solicitud (403)."),
    e("Resposta inesperada da Kick.", "Unexpected response from Kick.", "Respuesta inesperada de Kick."),
    e("Perfil inválido.", "Invalid profile.", "Perfil no válido."),
    e("Sessão expirada. Entre de novo.", "Session expired. Sign in again.", "Sesión caducada. Inicia sesión de nuevo."),
    // Avisos de live (notificações)
    e("Favoritos ao vivo", "Favorites live", "Favoritos en vivo"),
    e("Silenciar", "Mute", "Silenciar"),
    e("Lives ao vivo", "Live now", "En vivo ahora"),
    // Textos com valores no meio (usados com .format, para nunca mexer em texto de terceiros)
    e("Mais ações", "More actions", "Más acciones"),
    e("Filtrar categorias", "Filter categories", "Filtrar categorías"),
    e("Horário silencioso", "Quiet hours", "Horario silencioso"),
    e("Sem avisos de live no período abaixo", "No live alerts during the period below", "Sin avisos de directo en el periodo de abajo"),
    e("Começa às", "Starts at", "Empieza a las"),
    e("Termina às", "Ends at", "Termina a las"),
    e("SOBRE", "ABOUT", "ACERCA DE"),
    e("Código-fonte no GitHub", "Source code on GitHub", "Código fuente en GitHub"),
    e("Aviso legal", "Legal notice", "Aviso legal"),
    e("Política de privacidade", "Privacy policy", "Política de privacidad"),
    e("Reportar um problema ou sugerir algo", "Report a problem or suggest something", "Informar de un problema o sugerir algo"),
    e("Remover", "Remove", "Quitar"),
    e("Novidades da versão %s", "What's new in version %s", "Novedades de la versión %s"),
    e("Entendi", "Got it", "Entendido"),
    e("Novidades desta versão", "What's new in this version", "Novedades de esta versión"),
    e("Economia de bateria", "Battery saver", "Ahorro de batería"),
    e("Com menos de 20% de bateria (sem carregar), não passa de 480p", "Below 20% battery (not charging), it stays at 480p or below", "Con menos del 20% de batería (sin cargar), no pasa de 480p"),
    e("Tamanho do texto do chat", "Chat text size", "Tamaño del texto del chat"),
    e("Pequeno", "Small", "Pequeño"),
    e("Normal", "Normal", "Normal"),
    e("Grande", "Large", "Grande"),
    e("Maior", "Larger", "Más grande"),
    e("Ver mais", "See more", "Ver más"),
    e("Ver menos", "See less", "Ver menos"),
    e("TELA INICIAL", "HOME SCREEN", "PANTALLA DE INICIO"),
    e("Continuar assistindo", "Continue watching", "Seguir viendo"),
    e("Buscar nos vídeos deste canal", "Search this channel's videos", "Buscar en los vídeos de este canal"),
    e("Excluir lista", "Delete list", "Eliminar lista"),
    e("Adicionar a uma lista", "Add to a list", "Añadir a una lista"),
    e("Listas de %s", "Lists for %s", "Listas de %s"),
    e("Nova lista", "New list", "Nueva lista"),
    e("Criar", "Create", "Crear"),
    e("Pronto", "Done", "Listo"),
    e("Tempo assistido", "Watch time", "Tiempo visto"),
    e("Últimos 7 dias", "Last 7 days", "Últimos 7 días"),
    e("Últimos 30 dias", "Last 30 days", "Últimos 30 días"),
    e("de lives assistidas neste período", "of lives watched in this period", "de directos vistos en este periodo"),
    e("Nada por aqui ainda. O tempo que você passa assistindo lives aparece nesta lista.", "Nothing here yet. The time you spend watching lives shows up in this list.", "Nada por aquí todavía. El tiempo que pasas viendo directos aparece en esta lista."),
    e("Bloquear rotação", "Lock rotation", "Bloquear rotación"),
    e("Ativar som", "Unmute", "Activar sonido"),
    e("TRANSMITIR", "CAST", "TRANSMITIR"),
    e("Transmitir para a TV (DLNA)", "Cast to TV (DLNA)", "Transmitir a la TV (DLNA)"),
    e("Parar transmissão para %s", "Stop casting to %s", "Detener transmisión a %s"),
    e("Transmitir para a TV", "Cast to TV", "Transmitir a la TV"),
    e("Procurando TVs na rede…", "Looking for TVs on the network…", "Buscando TV en la red…"),
    e("Nenhuma TV encontrada. A TV precisa estar ligada, no mesmo Wi-Fi do celular e aceitar DLNA.", "No TV found. The TV must be on, on the same Wi-Fi as your phone and support DLNA.", "No se encontró ninguna TV. La TV debe estar encendida, en la misma Wi-Fi que el móvil y admitir DLNA."),
    e("Procurar de novo", "Search again", "Buscar de nuevo"),
    e("A TV não aceitou a transmissão.", "The TV didn't accept the stream.", "La TV no aceptó la transmisión."),
    e("Categoria: ", "Category: ", "Categoría: "),
    e("ao vivo", "live", "en vivo"),
    e("Usar %s", "Use %s", "Usar %s"),
    e("ao vivo há %s", "live for %s", "en vivo desde hace %s"),
    e("há %d min", "%d min ago", "hace %d min"),
    e("há %d h", "%d h ago", "hace %d h"),
    e("há %d d", "%d d ago", "hace %d d"),
    e("Clipes (%s)", "Clips (%s)", "Clips (%s)"),
    e("Transmissões (%d)", "Broadcasts (%d)", "Emisiones (%d)"),
    e("por %s", "by %s", "por %s"),
    e("Clipe de %s", "Clip by %s", "Clip de %s"),
    e("Continuando de %s", "Resuming from %s", "Continuando desde %s"),
    e("%s seguidores", "%s followers", "%s seguidores"),
    e("%s assistindo", "%s watching", "%s viendo"),
    e("Brilho %d%%", "Brightness %d%%", "Brillo %d%%"),
    e("Volume %d%%", "Volume %d%%", "Volumen %d%%"),
    e("%s está ao vivo", "%s is live", "%s está en vivo"),
    e("%s está ao vivo na Kick: %s", "%s is live on Kick: %s", "%s está en vivo en Kick: %s"),
    e("%s está offline", "%s is offline", "%s está sin conexión"),
    e("Não consegui abrir %s", "Couldn't open %s", "No pude abrir %s"),
    e("Agora você segue %s.", "You now follow %s.", "Ahora sigues a %s."),
    e("Você deixou de seguir %s.", "You unfollowed %s.", "Dejaste de seguir a %s."),
    e("Não foi possível alterar (código %d).", "Couldn't change (code %d).", "No se pudo cambiar (código %d)."),
    e("Não foi possível seguir (código %d).", "Couldn't follow (code %d).", "No se pudo seguir (código %d)."),
    e("Não foi possível deixar de seguir (código %d).", "Couldn't unfollow (code %d).", "No se pudo dejar de seguir (código %d)."),
    e("Assistir lado a lado com %s", "Watch side by side with %s", "Ver en paralelo con %s"),
    e("%.0f Mbps • ideal para %s", "%.0f Mbps • best for %s", "%.0f Mbps • ideal para %s"),
    e("Falha no teste: %s", "Test failed: %s", "Falló la prueba: %s"),
    e("Erro de reprodução: %s", "Playback error: %s", "Error de reproducción: %s"),
    e("Erro HTTP %d.", "HTTP error %d.", "Error HTTP %d."),
    e("Nova versão %s", "New version %s", "Nueva versión %s"),
    e(
        "BetterKick %s • cliente não oficial da Kick\nUsa endereços não oficiais do site, que podem mudar sem aviso.",
        "BetterKick %s • unofficial Kick client\nIt uses unofficial site addresses that may change without notice.",
        "BetterKick %s • cliente no oficial de Kick\nUsa direcciones no oficiales del sitio que pueden cambiar sin aviso.",
    ),
    e("BetterKick %s • cliente não oficial da Kick", "BetterKick %s • unofficial Kick client", "BetterKick %s • cliente no oficial de Kick"),
).toMap()
