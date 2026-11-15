package com.example.gestortareas

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.gestortareas.data.*
import com.example.gestortareas.network.*
import com.example.gestortareas.preferences.Preferencias
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.obtener(this)
        val prefs = Preferencias(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppGestor(db.tareaDao(), prefs)
                }
            }
        }
    }
}

@Composable
fun AppGestor(dao: TareaDao, prefs: Preferencias) {
    val scope = rememberCoroutineScope()
    var pantallaActual by rememberSaveable { mutableStateOf("lista") }
    var tareaAEditar by remember { mutableStateOf<Tarea?>(null) }

    val soloPendientes by prefs.soloPendientes.collectAsState(initial = false)

    when (pantallaActual) {
        "lista" -> PantallaLista(
            dao = dao,
            soloPendientes = soloPendientes,
            alCambiarSoloPendientes = { nuevoValor ->
                scope.launch { prefs.guardarSoloPendientes(nuevoValor) }
            },
            alCrear = { pantallaActual = "formulario"; tareaAEditar = null },
            alEditar = { pantallaActual = "formulario"; tareaAEditar = it },
            alIrApi = { pantallaActual = "api" }
        )
        "formulario" -> PantallaFormulario(
            dao = dao,
            tareaExistente = tareaAEditar,
            alVolver = { pantallaActual = "lista" }
        )
        "api" -> PantallaApi(alVolver = { pantallaActual = "lista" })
    }
}

@Composable
fun PantallaLista(
    dao: TareaDao,
    soloPendientes: Boolean,
    alCambiarSoloPendientes: (Boolean) -> Unit,
    alCrear: () -> Unit,
    alEditar: (Tarea) -> Unit,
    alIrApi: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val tareas by dao.obtenerTodas().collectAsState(initial = emptyList())
    var tareaParaBorrar by remember { mutableStateOf<Tarea?>(null) }
    val context = LocalContext.current

    val tareasFiltradas = if (soloPendientes) tareas.filter { !it.completada } else tareas

    Column(modifier = Modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Mis Tareas", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = alIrApi) { Text("API") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = soloPendientes, onCheckedChange = alCambiarSoloPendientes)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Mostrar solo pendientes")
        }

        Button(onClick = alCrear, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Añadir Nueva Tarea")
        }

        if (tareasFiltradas.isEmpty()) {
            Text("No hay tareas para mostrar.", modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(tareasFiltradas, key = { it.id }) { tarea ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = tarea.completada, onCheckedChange = {
                                    scope.launch { dao.actualizar(tarea.copy(completada = it)) }
                                })
                                Text(tarea.titulo, style = MaterialTheme.typography.titleMedium)
                            }
                            Text("Prioridad: ${tarea.prioridad}")
                            if (tarea.descripcion.isNotBlank()) {
                                Text(tarea.descripcion, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    val texto = "Tarea: ${tarea.titulo}\nPrioridad: ${tarea.prioridad}"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, texto)
                                    }
                                    ContextCompat.startActivity(context, Intent.createChooser(intent, "Compartir"), null)
                                }) { Text("Compartir") }
                                TextButton(onClick = { alEditar(tarea) }) { Text("Editar") }
                                TextButton(onClick = { tareaParaBorrar = tarea }) { Text("Borrar") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (tareaParaBorrar != null) {
        val tareaSegura = tareaParaBorrar!!
        AlertDialog(
            onDismissRequest = { tareaParaBorrar = null },
            title = { Text("Eliminar Tarea") },
            text = { Text("¿Seguro que quieres eliminar '${tareaSegura.titulo}'?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.eliminar(tareaSegura) }
                    tareaParaBorrar = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { tareaParaBorrar = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun PantallaFormulario(dao: TareaDao, tareaExistente: Tarea?, alVolver: () -> Unit) {
    val scope = rememberCoroutineScope()
    var titulo by rememberSaveable { mutableStateOf(tareaExistente?.titulo ?: "") }
    var descripcion by rememberSaveable { mutableStateOf(tareaExistente?.descripcion ?: "") }
    var prioridad by rememberSaveable { mutableStateOf(tareaExistente?.prioridad ?: "Media") }
    var intentoGuardar by remember { mutableStateOf(false) }

    val tituloError = if (intentoGuardar && titulo.trim().length < 3) "El título debe tener al menos 3 caracteres" else null

    Column(modifier = Modifier.padding(16.dp)) {
        Text(if (tareaExistente == null) "Nueva Tarea" else "Editar Tarea", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = titulo,
            onValueChange = { titulo = it },
            label = { Text("Título") },
            isError = tituloError != null,
            supportingText = { if (tituloError != null) Text(tituloError) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        OutlinedTextField(
            value = descripcion,
            onValueChange = { descripcion = it },
            label = { Text("Descripción (opcional)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Alta", "Media", "Baja").forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = prioridad == p, onClick = { prioridad = p })
                    Text(p)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = alVolver) { Text("Cancelar") }
            Button(onClick = {
                intentoGuardar = true
                if (titulo.trim().length >= 3) {
                    scope.launch {
                        if (tareaExistente == null) {
                            dao.insertar(Tarea(titulo = titulo.trim(), descripcion = descripcion.trim(), prioridad = prioridad))
                        } else {
                            dao.actualizar(tareaExistente.copy(titulo = titulo.trim(), descripcion = descripcion.trim(), prioridad = prioridad))
                        }
                        alVolver()
                    }
                }
            }) { Text("Guardar") }
        }
    }
}

@Composable
fun PantallaApi(alVolver: () -> Unit) {
    val scope = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(false) }
    var tareasRemotas by remember { mutableStateOf<List<TareaRemota>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun cargarDatos() {
        cargando = true
        error = null
        scope.launch {
            try {
                tareasRemotas = ServicioRed.api.listarTareas()
            } catch (e: Exception) {
                error = "Error de conexión. Revisa tu Internet."
            } finally {
                cargando = false
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Tareas API", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = alVolver) { Text("Volver") }
        }

        Button(onClick = { cargarDatos() }, enabled = !cargando, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(if (cargando) "Cargando..." else "Cargar Tareas de API")
        }

        if (cargando) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        if (error != null) {
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
            Button(onClick = { cargarDatos() }) { Text("Reintentar") }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tareasRemotas) { t ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("ID: ${t.id} - ${t.title}")
                        Text("Completada: ${t.completed}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}