package com.newkick.app

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class ChannelTarget(val slug: String, val username: String)

/** Ações de um canal (pressão longa em um card): assistir, seguir, silenciar, favoritar, compartilhar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelActionsSheet(
    target: ChannelTarget,
    loggedIn: Boolean,
    followedSlugs: Set<String>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onFollowChanged: () -> Unit,
    onOpenMulti: (String, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()
    var followed by remember { mutableStateOf<Boolean?>(if (followedSlugs.contains(target.slug)) true else null) }
    var busy by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(prefs.isMuted(target.slug)) }
    var favorite by remember { mutableStateOf(prefs.isFavorite(target.slug)) }
    var listsOpen by remember { mutableStateOf(false) }
    if (listsOpen) ListsDialog(prefs, target) { listsOpen = false }

    LaunchedEffect(target.slug) {
        if (loggedIn && followed == null) followed = runCatching { KickApi.isFollowing(target.slug) }.getOrNull()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 28.dp)) {
            Text(target.username, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SheetAction("Assistir") {
                onDismiss()
                onOpen(target.slug)
            }
            // Duas lives ao mesmo tempo: escolhe a primeira aqui, segura a segunda em outro card.
            val first = MultiViewState.pending
            if (first != null && first.slug != target.slug) {
                SheetAction(tr("Assistir lado a lado com %s").format(first.username)) {
                    MultiViewState.pending = null
                    onDismiss()
                    onOpenMulti(first.slug, target.slug)
                }
            } else {
                SheetAction(if (first != null) "Cancelar lado a lado" else "Assistir lado a lado (escolher a 2ª live)") {
                    MultiViewState.pending = if (first != null) null else target
                    if (first == null) Toast.makeText(context, "Agora segure o card da segunda live.", Toast.LENGTH_LONG).show()
                    onDismiss()
                }
            }
            SheetAction("Clipes e transmissões anteriores") {
                onDismiss()
                onOpenChannel(target.slug)
            }
            if (loggedIn) {
                SheetAction(
                    label = when (followed) {
                        true -> "Deixar de seguir"
                        false -> "Seguir"
                        null -> "Seguir (verificando...)"
                    },
                    enabled = followed != null && !busy,
                ) {
                    val want = followed != true
                    busy = true
                    scope.launch {
                        val status = ChatWeb.setFollowAnywhere(context, target.slug, want)
                        if (status in 200..299) {
                            followed = want
                            onFollowChanged()
                            Toast.makeText(context, tr(if (want) "Agora você segue %s." else "Você deixou de seguir %s.").format(target.username), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, tr("Não foi possível alterar (código %d).").format(status), Toast.LENGTH_LONG).show()
                        }
                        busy = false
                    }
                }
            }
            if (followed == true || favorite) {
                SheetAction(if (muted) "Reativar avisos de live" else "Silenciar avisos de live") {
                    muted = prefs.toggleMuted(target.slug)
                }
            }
            SheetAction(if (favorite) "Remover dos favoritos" else "Favoritar (estrela)") {
                favorite = prefs.toggleFavorite(target.slug)
            }
            SheetAction("Adicionar a uma lista") { listsOpen = true }
            SheetAction("Compartilhar") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${target.username} na Kick: https://kick.com/${target.slug}")
                }
                context.startActivity(Intent.createChooser(send, "Compartilhar canal"))
            }
        }
    }
}

/** Marca em quais das suas listas o canal está e cria listas novas. */
@Composable
private fun ListsDialog(prefs: Prefs, target: ChannelTarget, onDismiss: () -> Unit) {
    var lists by remember { mutableStateOf(prefs.channelLists()) }
    var newName by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Listas de %s").format(target.username)) },
        text = {
            Column {
                lists.forEach { (name, slugs) ->
                    androidx.compose.foundation.layout.Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                prefs.toggleInChannelList(name, target.slug)
                                lists = prefs.channelLists()
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = target.slug in slugs, onCheckedChange = null)
                        Text(name, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text("Nova lista") },
                    singleLine = true,
                    trailingIcon = {
                        if (newName.isNotBlank()) TextButton(onClick = {
                            val name = newName.trim().take(24)
                            prefs.createChannelList(name)
                            prefs.toggleInChannelList(name, target.slug)
                            newName = ""
                            lists = prefs.channelLists()
                        }) { Text("Criar") }
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Pronto") } },
    )
}

@Composable
private fun SheetAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    )
}
