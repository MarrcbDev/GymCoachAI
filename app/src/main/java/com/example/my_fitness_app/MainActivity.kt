package com.example.my_fitness_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private val vm: GymViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val ctx = applicationContext
                return GymViewModel(ctx, GymDatabase.get(ctx), ApiKeyVault(ctx)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { GymTheme(dynamic = vm.dynamicColor) { GymApp(vm) } }
    }
}

private data class TabItem(val title: String, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    TabItem("Inicio", Icons.Rounded.Home, Icons.Outlined.Home),
    TabItem("Coach IA", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    TabItem("Rutinas", Icons.Rounded.FitnessCenter, Icons.Outlined.FitnessCenter),
    TabItem("Más", Icons.Rounded.GridView, Icons.Outlined.GridView),
)

@Composable
fun GymApp(vm: GymViewModel) {
    when {
        // Mientras se lee la base de datos no se muestra nada (así el registro no parpadea).
        vm.loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        // Sin perfil: el registro es lo primero que se ve al abrir la app.
        vm.user == null -> RegistrationScreen(vm)
        else -> MainShell(vm)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainShell(vm: GymViewModel) {
    var tab by remember { mutableStateOf("Inicio") }
    var showProfile by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible

    BackHandler(enabled = showProfile) { showProfile = false }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!imeVisible) {
                NavigationBar {
                    tabs.forEach { item ->
                        val selected = tab == item.title
                        NavigationBarItem(
                            selected = selected && !showProfile,
                            onClick = { tab = item.title; showProfile = false },
                            icon = { Icon(if (selected) item.selected else item.unselected, contentDescription = null) },
                            label = { Text(item.title) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal = 18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (showProfile) "Tu perfil" else tab,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                FilledTonalIconButton(onClick = { showProfile = !showProfile }) {
                    Icon(Icons.Rounded.Person, contentDescription = "Perfil")
                }
            }
            when {
                showProfile -> ProfileScreen(vm) { showProfile = false }
                tab == "Inicio" -> Dashboard(vm) { tab = it }
                tab == "Coach IA" -> ChatScreen(vm)
                tab == "Rutinas" -> WorkoutsScreen()
                else -> MoreScreen(vm)
            }
        }
    }
}
