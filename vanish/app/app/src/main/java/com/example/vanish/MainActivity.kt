
package com.example.vanish

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.vanish.data.MessageEntity
import com.example.vanish.data.OutboxEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Config.BLOCK_SCREENSHOTS) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        val repo = (application as VanishApp).repo

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    App(repo)
                }
            }
        }
    }
}

@Composable
fun App(repo: ChatRepository) {
    var joined by rememberSaveable {
        mutableStateOf(repo.hasName())
    }

    if (!joined) {
        JoinScreen {
            repo.join(it)
            joined = true
        }
    } else {
        ChatScreen(repo)
    }
}

@Composable
fun JoinScreen(onJoin: (String) -> Unit) {
    var name by rememberSaveable {
        mutableStateOf("")
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Vanish",
            style = MaterialTheme.typography.headlineLarge
        )

        Text("Messages vanish 30 seconds after delivery.")

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { onJoin(name) },
            enabled = name.isNotBlank()
        ) {
            Text("Join class room")
        }
    }
}

@Composable
fun ChatScreen(repo: ChatRepository) {

    val messages by repo.messages.collectAsState(emptyList())
    val outbox by repo.outbox.collectAsState(emptyList())
    val conn by repo.connection.collectAsState()

    var input by rememberSaveable {
        mutableStateOf("")
    }

    var notice by remember {
        mutableStateOf("")
    }

    // Countdown uses relay time.
    val now by produceState(ClockSync.now()) {
        while (true) {
            value = ClockSync.now()
            delay(250)
        }
    }

    LaunchedEffect(Unit) {
        repo.notices.collect {
            notice = it
        }
    }

    // Reconnect and refresh when app resumes.
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                repo.kick()
            }
        }

        owner.lifecycle.addObserver(obs)

        onDispose {
            owner.lifecycle.removeObserver(obs)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(12.dp)
    ) {
        Text(
            "${repo.name} · " + when (conn) {
                Conn.ONLINE -> "online"
                Conn.CONNECTING -> "connecting…"
                Conn.OFFLINE -> "offline"
            },
            style = MaterialTheme.typography.titleMedium
        )

        if (notice.isNotEmpty()) {
            Text(
                notice,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                messages.filter { it.expiresAt > now },
                key = { it.id }
            ) {
                MessageRow(it, now)
            }

            items(
                outbox,
                key = { "o" + it.id }
            ) {
                OutboxRow(it, conn)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") }
            )

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    repo.send(input)
                    input = ""
                },
                enabled = input.isNotBlank()
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun MessageRow(m: MessageEntity, now: Long) {

    val secs = ((m.expiresAt - now + 999) / 1000)
        .coerceAtLeast(0)

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (m.mine) "You" else m.sender,
                    style = MaterialTheme.typography.labelMedium
                )

                Text(m.text)
            }

            Text(
                "${secs}s",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun OutboxRow(o: OutboxEntity, conn: Conn) {

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "You · " + if (conn == Conn.ONLINE) {
                    "sending…"
                } else {
                    "waiting for network"
                },
                style = MaterialTheme.typography.labelMedium
            )

            Text(o.text)
        }
    }
}
