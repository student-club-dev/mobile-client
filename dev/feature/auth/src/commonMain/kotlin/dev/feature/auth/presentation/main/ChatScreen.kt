package dev.feature.auth.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.domain.model.Conversation
import dev.core.domain.model.Message
import dev.feature.auth.presentation.components.AuthFontFamily
import dev.feature.auth.presentation.components.AuthIcons
import dev.feature.auth.presentation.components.GlassTextField
import dev.feature.auth.presentation.theme.AuthPalette
import dev.feature.auth.presentation.theme.authPalette
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatScreen(onBack: () -> Unit, vm: ChatViewModel = koinViewModel()) {
    val palette = authPalette
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.selected == null) {
        ConversationList(state.conversations, palette, onBack = onBack, onOpen = vm::open)
    } else {
        ChatThread(state.selected!!, state.messages, state.draft, palette, onBack = vm::close, onDraft = vm::onDraft, onSend = vm::send)
    }
}

// 1x — suhbatlar ro'yxati
@Composable
private fun ConversationList(conversations: List<Conversation>, palette: AuthPalette, onBack: () -> Unit, onOpen: (Conversation) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            IconBox(AuthIcons.ArrowLeft, palette, onBack)
            Text("Xabarlar", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Black, color = palette.ink))
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(conversations, key = { it.id }) { c -> ConversationRow(c, palette) { onOpen(c) } }
        }
    }
}

@Composable
private fun ConversationRow(c: Conversation, palette: AuthPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(999.dp)).background(palette.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Text(c.peerInitial, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = palette.primary))
            }
            if (c.online) {
                Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(RoundedCornerShape(999.dp)).background(Color.White).padding(2.dp)) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(999.dp)).background(palette.successDeep))
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(c.peerName, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(c.lastMessage, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 11.5f.sp, color = palette.inkFaint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.lastTime, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 10.5f.sp, color = palette.inkFaint))
            if (c.unreadCount > 0) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(999.dp)).background(palette.primary), contentAlignment = Alignment.Center) {
                    Text("${c.unreadCount}", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White))
                }
            }
        }
    }
}

// 1y — suhbat oynasi
@Composable
private fun ChatThread(
    conversation: Conversation,
    messages: List<Message>,
    draft: String,
    palette: AuthPalette,
    onBack: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 54.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            IconBox(AuthIcons.ArrowLeft, palette, onBack)
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).background(palette.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Text(conversation.peerInitial, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = palette.primary))
            }
            Column(Modifier.weight(1f)) {
                Text(conversation.peerName, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink))
                Text(if (conversation.online) "online" else "oflayn", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 11.sp, color = if (conversation.online) palette.successDeep else palette.inkFaint))
            }
        }

        // Xabarlar
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { m -> MessageBubble(m, palette) }
        }

        // Kiritish paneli
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassTextField(draft, onDraft, "Xabar yozing...", modifier = Modifier.weight(1f), height = 48)
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(palette.primaryBrush).clickable(onClick = onSend),
                contentAlignment = Alignment.Center,
            ) { Icon(AuthIcons.Send, "Yuborish", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, palette: AuthPalette) {
    val align = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        val shape = if (message.outgoing) {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
        } else {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
        }
        Column(
            Modifier.widthIn(max = 280.dp).clip(shape)
                .background(if (message.outgoing) palette.primary else palette.glassStrong)
                .then(if (message.outgoing) Modifier else Modifier.border(1.dp, palette.border, shape))
                .padding(horizontal = 13.dp, vertical = 9.dp),
        ) {
            Text(message.text, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 13.sp, color = if (message.outgoing) Color.White else palette.ink))
            Text(
                message.time,
                style = TextStyle(fontFamily = AuthFontFamily, fontSize = 9.sp, color = if (message.outgoing) Color.White.copy(alpha = 0.7f) else palette.inkFaint),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector, palette: AuthPalette, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, "Orqaga", tint = palette.ink, modifier = Modifier.size(18.dp)) }
}
