package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.LatexFormulaRenderer

data class ReadyFormula(
    val title: String,
    val category: String,
    val formula: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatexManualDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val readyFormulas = remember {
        listOf(
            // Álgebra e Geometria
            ReadyFormula(
                title = "Fórmula de Bhaskara",
                category = "Álgebra",
                formula = "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}",
                description = "Raízes de equações de segundo grau ax² + bx + c = 0"
            ),
            ReadyFormula(
                title = "Teorema de Pitágoras",
                category = "Geometria",
                formula = "a^2 + b^2 = c^2",
                description = "Relação entre os lados de um triângulo retângulo"
            ),
            ReadyFormula(
                title = "Área do Círculo",
                category = "Geometria",
                formula = "A = \\pi r^2",
                description = "Cálculo da área interna do círculo"
            ),
            ReadyFormula(
                title = "Volume da Esfera",
                category = "Geometria",
                formula = "V = \\frac{4}{3}\\pi r^3",
                description = "Volume geométrico de uma esfera de raio r"
            ),
            ReadyFormula(
                title = "Identidade Fundamental Trigonométrica",
                category = "Trigonometria",
                formula = "\\sin^2(x) + \\cos^2(x) = 1",
                description = "Relação base da trigonometria plana"
            ),
            ReadyFormula(
                title = "Identidade de Euler",
                category = "Matemática",
                formula = "e^{i\\pi} + 1 = 0",
                description = "Conexão fundamental entre e, pi, i, 1 e 0"
            ),

            // Cálculo e Estatística
            ReadyFormula(
                title = "Derivada pela Definição",
                category = "Cálculo",
                formula = "f'(x) = \\lim_{h \\to 0} \\frac{f(x+h) - f(x)}{h}",
                description = "Limite que define a taxa de variação instantânea"
            ),
            ReadyFormula(
                title = "Regra da Cadeia",
                category = "Cálculo",
                formula = "\\frac{df}{dx} = \\frac{df}{du} \\cdot \\frac{du}{dx}",
                description = "Derivada de funções compostas"
            ),
            ReadyFormula(
                title = "Integral por Partes",
                category = "Cálculo",
                formula = "\\int u \\, dv = u \\cdot v - \\int v \\, du",
                description = "Técnica clássica de integração"
            ),
            ReadyFormula(
                title = "Média Aritmética",
                category = "Estatística",
                formula = "\\bar{x} = \\frac{1}{n} \\sum_{i=1}^{n} x_i",
                description = "Soma de todos os elementos dividida pela quantidade"
            ),
            ReadyFormula(
                title = "Desvio Padrão Amostral",
                category = "Estatística",
                formula = "s = \\sqrt{\\frac{1}{n-1} \\sum_{i=1}^{n} (x_i - \\bar{x})^2}",
                description = "Medida de dispersão dos dados em relação à média"
            ),
            ReadyFormula(
                title = "Distribuição Normal Gaussiana",
                category = "Estatística",
                formula = "f(x) = \\frac{1}{\\sigma \\sqrt{2\\pi}} e^{-\\frac{1}{2}\\left(\\frac{x-\\mu}{\\sigma}\\right)^2}",
                description = "Curva de sino para distribuições contínuas de probabilidade"
            ),

            // Física e Química
            ReadyFormula(
                title = "Equivalência Massa-Energia",
                category = "Física",
                formula = "E = mc^2",
                description = "Equação de relatividade restrita de Albert Einstein"
            ),
            ReadyFormula(
                title = "Segunda Lei de Newton",
                category = "Física",
                formula = "\\vec{F} = m \\cdot \\vec{a}",
                description = "Força resultante é o produto da massa pela aceleração"
            ),
            ReadyFormula(
                title = "Lei da Gravitação Universal",
                category = "Física",
                formula = "F = G \\frac{m_1 m_2}{r^2}",
                description = "Força de atração mútua entre duas massas"
            ),
            ReadyFormula(
                title = "Energia Cinética",
                category = "Física",
                formula = "E_c = \\frac{1}{2} m v^2",
                description = "Energia associada ao movimento de um corpo"
            ),
            ReadyFormula(
                title = "Equação dos Gases Ideais",
                category = "Química/Física",
                formula = "P \\cdot V = n \\cdot R \\cdot T",
                description = "Relação de Clapeyron para gases perfeitos"
            ),

            // Finanças
            ReadyFormula(
                title = "Juros Compostos",
                category = "Finanças",
                formula = "M = C(1 + i)^t",
                description = "Montante acumulado ao longo de t períodos à taxa i"
            ),
            ReadyFormula(
                title = "Valor Presente Líquido (VPL)",
                category = "Finanças",
                formula = "VPL = \\sum_{t=1}^{n} \\frac{FC_t}{(1 + r)^t} - I_0",
                description = "Fluxo de caixa descontado menos investimento inicial"
            )
        )
    }

    val filteredFormulas = remember(searchQuery, readyFormulas) {
        if (searchQuery.isBlank()) readyFormulas
        else readyFormulas.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true) ||
            it.formula.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    fun copyToClipboard(text: String, label: String = "Fórmula") {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copiado: $text", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 20.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Manual de Fórmulas LaTeX",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Text(
                    text = "Guia completo de sintaxe matemática e catálogo de fórmulas prontas para copiar e usar nas suas notas.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Switcher
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Fórmulas Prontas", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Sintaxe & Comandos", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // Ready formulas catalog with search & 1-tap copy
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar fórmula (ex: Bhaskara, Juros, Física...)") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredFormulas) { item ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = item.category,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Rendered preview & code box
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = LatexFormulaRenderer.formatAnnotatedBlock(item.formula, false),
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = FontFamily.Serif,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = item.formula,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    FilledTonalButton(
                                        onClick = { copyToClipboard(item.formula, item.title) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copiar Fórmula LaTeX", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Syntax and commands guide
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            SectionCard(title = "1. Como Inserir Fórmulas") {
                                Text(
                                    text = "• Em linha: use um cifrão no texto: \$E = mc^2\$\n" +
                                           "• Bloco centralizado: use bloco duplo:\n\n" +
                                           "$$\n" +
                                           "\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}\n" +
                                           "$$\n" +
                                           "• No editor visual do Citra: toque no botão 'Matemática' da barra de ferramentas.",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        item {
                            SectionCard(title = "2. Operações Aritméticas e Frações") {
                                CommandExample(cmd = "\\frac{a}{b}", desc = "Fração a sobre b", onCopy = { copyToClipboard("\\frac{a}{b}") })
                                CommandExample(cmd = "\\times", desc = "Multiplicação (×)", onCopy = { copyToClipboard("\\times") })
                                CommandExample(cmd = "\\cdot", desc = "Ponto de multiplicação (·)", onCopy = { copyToClipboard("\\cdot") })
                                CommandExample(cmd = "\\div", desc = "Divisão (÷)", onCopy = { copyToClipboard("\\div") })
                                CommandExample(cmd = "\\pm", desc = "Mais ou menos (±)", onCopy = { copyToClipboard("\\pm") })
                            }
                        }

                        item {
                            SectionCard(title = "3. Potências, Raízes e Índices") {
                                CommandExample(cmd = "x^2 ou x^{n+1}", desc = "Expoente (use chaves se tiver mais de 1 caractere)", onCopy = { copyToClipboard("x^{2}") })
                                CommandExample(cmd = "x_1 ou x_{i,j}", desc = "Subscrito / Índice", onCopy = { copyToClipboard("x_{1}") })
                                CommandExample(cmd = "\\sqrt{x}", desc = "Raiz quadrada de x", onCopy = { copyToClipboard("\\sqrt{x}") })
                                CommandExample(cmd = "\\sqrt[n]{x}", desc = "Raiz n-ésima", onCopy = { copyToClipboard("\\sqrt[n]{x}") })
                            }
                        }

                        item {
                            SectionCard(title = "4. Somatórios, Limites e Integrais") {
                                CommandExample(cmd = "\\sum_{i=1}^{n} x_i", desc = "Somatório de i=1 até n", onCopy = { copyToClipboard("\\sum_{i=1}^{n} x_i") })
                                CommandExample(cmd = "\\prod_{i=1}^{n} x_i", desc = "Produtório de i=1 até n", onCopy = { copyToClipboard("\\prod_{i=1}^{n} x_i") })
                                CommandExample(cmd = "\\lim_{x \\to 0} f(x)", desc = "Limite com x tendendo a 0", onCopy = { copyToClipboard("\\lim_{x \\to 0} f(x)") })
                                CommandExample(cmd = "\\int_{a}^{b} f(x) dx", desc = "Integral definida de a até b", onCopy = { copyToClipboard("\\int_{a}^{b} f(x) dx") })
                                CommandExample(cmd = "\\partial", desc = "Derivada parcial (∂)", onCopy = { copyToClipboard("\\partial") })
                            }
                        }

                        item {
                            SectionCard(title = "5. Letras Gregas Mais Usadas") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("\\alpha (α)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text("\\beta (β)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text("\\gamma (γ)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text("\\delta (δ)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("\\pi (π)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text("\\theta (θ)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text("\\lambda (λ)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text("\\sigma (σ)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("\\Delta (Δ)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text("\\Omega (Ω)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text("\\Sigma (Σ)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text("\\infty (∞)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }

                        item {
                            SectionCard(title = "6. Relações e Símbolos Lógicos") {
                                CommandExample(cmd = "\\le  e  \\ge", desc = "Menor/igual (≤) e Maior/igual (≥)", onCopy = { copyToClipboard("\\le") })
                                CommandExample(cmd = "\\ne", desc = "Diferente (≠)", onCopy = { copyToClipboard("\\ne") })
                                CommandExample(cmd = "\\approx", desc = "Aproximadamente (≈)", onCopy = { copyToClipboard("\\approx") })
                                CommandExample(cmd = "\\in", desc = "Pertence a (∈)", onCopy = { copyToClipboard("\\in") })
                                CommandExample(cmd = "\\forall", desc = "Para todo (∀)", onCopy = { copyToClipboard("\\forall") })
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fechar Manual")
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun CommandExample(cmd: String, desc: String, onCopy: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy() }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cmd, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}
