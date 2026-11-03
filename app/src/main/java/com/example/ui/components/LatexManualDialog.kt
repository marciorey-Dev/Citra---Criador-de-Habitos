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
            // Álgebra
            ReadyFormula(
                title = "Fórmula de Bhaskara",
                category = "Álgebra",
                formula = "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}",
                description = "Raízes de equações de segundo grau ax² + bx + c = 0"
            ),
            ReadyFormula(
                title = "Vértice da Parábola",
                category = "Álgebra",
                formula = "x_v = -\\frac{b}{2a}, \\quad y_v = -\\frac{\\Delta}{4a}",
                description = "Ponto de máximo ou mínimo de uma parábola quadrática"
            ),
            ReadyFormula(
                title = "Diferença de Quadrados",
                category = "Álgebra",
                formula = "a^2 - b^2 = (a - b)(a + b)",
                description = "Fatoração de produtos notáveis"
            ),
            ReadyFormula(
                title = "Quadrado da Soma",
                category = "Álgebra",
                formula = "(a + b)^2 = a^2 + 2ab + b^2",
                description = "Trinômio quadrado perfeito clássico"
            ),
            ReadyFormula(
                title = "Relações de Viète (Soma e Produto)",
                category = "Álgebra",
                formula = "S = x_1 + x_2 = -\\frac{b}{a}, \\quad P = x_1 \\cdot x_2 = \\frac{c}{a}",
                description = "Soma e produto das raízes de uma equação quadrática"
            ),
            ReadyFormula(
                title = "Termo Geral da PA",
                category = "Álgebra",
                formula = "a_n = a_1 + (n - 1)r",
                description = "Progressão Aritmética de razão r"
            ),
            ReadyFormula(
                title = "Soma dos Termos da PA",
                category = "Álgebra",
                formula = "S_n = \\frac{(a_1 + a_n)n}{2}",
                description = "Soma finita de uma progressão aritmética"
            ),
            ReadyFormula(
                title = "Termo Geral da PG",
                category = "Álgebra",
                formula = "a_n = a_1 \\cdot q^{n - 1}",
                description = "Progressão Geométrica de razão q"
            ),
            ReadyFormula(
                title = "Soma da PG Finita",
                category = "Álgebra",
                formula = "S_n = \\frac{a_1(q^n - 1)}{q - 1}",
                description = "Soma dos n primeiros termos de uma progressão geométrica"
            ),
            ReadyFormula(
                title = "Logaritmo do Produto e Quociente",
                category = "Álgebra",
                formula = "\\log_b(xy) = \\log_b(x) + \\log_b(y), \\quad \\log_b\\left(\\frac{x}{y}\\right) = \\log_b(x) - \\log_b(y)",
                description = "Propriedades operatórias de logaritmos"
            ),
            ReadyFormula(
                title = "Mudança de Base Logarítmica",
                category = "Álgebra",
                formula = "\\log_b(a) = \\frac{\\log_c(a)}{\\log_c(b)} = \\frac{\\ln(a)}{\\ln(b)}",
                description = "Conversão entre bases de logaritmos"
            ),

            // Geometria e Trigonometria
            ReadyFormula(
                title = "Teorema de Pitágoras",
                category = "Geometria",
                formula = "a^2 + b^2 = c^2",
                description = "Relação entre os lados de um triângulo retângulo"
            ),
            ReadyFormula(
                title = "Área e Perímetro do Círculo",
                category = "Geometria",
                formula = "A = \\pi r^2, \\quad C = 2\\pi r",
                description = "Área da superfície e perímetro da circunferência de raio r"
            ),
            ReadyFormula(
                title = "Volume da Esfera",
                category = "Geometria",
                formula = "V = \\frac{4}{3}\\pi r^3",
                description = "Volume geométrico de uma esfera tridimensional"
            ),
            ReadyFormula(
                title = "Volume do Cilindro",
                category = "Geometria",
                formula = "V = \\pi r^2 h",
                description = "Volume do cilindro reto de raio r e altura h"
            ),
            ReadyFormula(
                title = "Distância entre Dois Pontos",
                category = "Geometria",
                formula = "d = \\sqrt{(x_2 - x_1)^2 + (y_2 - y_1)^2}",
                description = "Distância euclidiana no plano cartesiano"
            ),
            ReadyFormula(
                title = "Identidade Fundamental Trigonométrica",
                category = "Trigonometria",
                formula = "\\sin^2(x) + \\cos^2(x) = 1",
                description = "Relação básica da trigonometria plana"
            ),
            ReadyFormula(
                title = "Lei dos Senos",
                category = "Trigonometria",
                formula = "\\frac{a}{\\sin(A)} = \\frac{b}{\\sin(B)} = \\frac{c}{\\sin(C)} = 2R",
                description = "Relação métrica em qualquer triângulo inscrito"
            ),
            ReadyFormula(
                title = "Lei dos Cossenos",
                category = "Trigonometria",
                formula = "a^2 = b^2 + c^2 - 2bc \\cos(A)",
                description = "Generalização de Pitágoras para qualquer ângulo"
            ),

            // Cálculo e Funções
            ReadyFormula(
                title = "Derivada pela Definição",
                category = "Cálculo",
                formula = "f'(x) = \\lim_{h \\to 0} \\frac{f(x+h) - f(x)}{h}",
                description = "Limite que define a taxa de variação instantânea"
            ),
            ReadyFormula(
                title = "Regra do Produto",
                category = "Cálculo",
                formula = "(u \\cdot v)' = u'v + uv'",
                description = "Derivada da multiplicação de duas funções"
            ),
            ReadyFormula(
                title = "Regra do Quociente",
                category = "Cálculo",
                formula = "\\left(\\frac{u}{v}\\right)' = \\frac{u'v - uv'}{v^2}",
                description = "Derivada da divisão de duas funções"
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
                description = "Técnica clássica de integração de produtos"
            ),
            ReadyFormula(
                title = "Teorema Fundamental do Cálculo",
                category = "Cálculo",
                formula = "\\int_a^b f(x) \\, dx = F(b) - F(a)",
                description = "Avaliação da integral definida pela primitiva F(x)"
            ),
            ReadyFormula(
                title = "Integral da Potência",
                category = "Cálculo",
                formula = "\\int x^n \\, dx = \\frac{x^{n+1}}{n+1} + C \\quad (n \\neq -1)",
                description = "Integral indefinida da regra da potência"
            ),
            ReadyFormula(
                title = "Identidade de Euler",
                category = "Cálculo",
                formula = "e^{i\\pi} + 1 = 0",
                description = "Conexão fundamental entre e, pi, i, 1 e 0"
            ),

            // Finanças e Economia
            ReadyFormula(
                title = "Juros Simples",
                category = "Finanças",
                formula = "J = C \\cdot i \\cdot t, \\quad M = C + J = C(1 + it)",
                description = "Cálculo de rendimento linear sobre o capital C"
            ),
            ReadyFormula(
                title = "Juros Compostos",
                category = "Finanças",
                formula = "M = C(1 + i)^t",
                description = "Montante acumulado ao longo de t períodos à taxa i"
            ),
            ReadyFormula(
                title = "Variação Percentual",
                category = "Finanças",
                formula = "\\Delta\\% = \\frac{V_f - V_i}{V_i} \\times 100\\%",
                description = "Crescimento ou queda percentual entre dois valores"
            ),
            ReadyFormula(
                title = "Valor Presente Líquido (VPL)",
                category = "Finanças",
                formula = "VPL = \\sum_{t=1}^{n} \\frac{FC_t}{(1 + r)^t} - I_0",
                description = "Fluxo de caixa descontado menos investimento inicial"
            ),
            ReadyFormula(
                title = "Prestação Fixa (Tabela Price)",
                category = "Finanças",
                formula = "PMT = PV \\cdot \\frac{i(1 + i)^n}{(1 + i)^n - 1}",
                description = "Cálculo da parcela constante de financiamentos"
            ),

            // Física e Química
            ReadyFormula(
                title = "Segunda Lei de Newton",
                category = "Física",
                formula = "\\vec{F} = m \\cdot \\vec{a}",
                description = "Força resultante é o produto da massa pela aceleração"
            ),
            ReadyFormula(
                title = "Equação de Torricelli",
                category = "Física",
                formula = "v^2 = v_0^2 + 2a\\Delta s",
                description = "Movimento retilíneo uniformemente variado sem tempo"
            ),
            ReadyFormula(
                title = "Energia Cinética",
                category = "Física",
                formula = "E_c = \\frac{1}{2} m v^2",
                description = "Energia associada à velocidade de um corpo de massa m"
            ),
            ReadyFormula(
                title = "Energia Potencial Gravitacional",
                category = "Física",
                formula = "E_p = m \\cdot g \\cdot h",
                description = "Energia armazenada em função da altura h"
            ),
            ReadyFormula(
                title = "Equivalência Massa-Energia",
                category = "Física",
                formula = "E = mc^2",
                description = "Equação de relatividade restrita de Albert Einstein"
            ),
            ReadyFormula(
                title = "Primeira Lei de Ohm",
                category = "Física",
                formula = "V = R \\cdot I, \\quad P = V \\cdot I = R \\cdot I^2",
                description = "Relação de tensão, resistência, corrente e potência elétrica"
            ),
            ReadyFormula(
                title = "Equação dos Gases Ideais",
                category = "Química/Física",
                formula = "P \\cdot V = n \\cdot R \\cdot T",
                description = "Equação de Clapeyron para gases perfeitos"
            ),
            ReadyFormula(
                title = "Calorimetria Sensível",
                category = "Física",
                formula = "Q = m \\cdot c \\cdot \\Delta T",
                description = "Quantidade de calor necessária para variar a temperatura"
            ),

            // Estatística
            ReadyFormula(
                title = "Média Aritmética",
                category = "Estatística",
                formula = "\\bar{x} = \\frac{1}{n} \\sum_{i=1}^{n} x_i",
                description = "Soma de todos os elementos dividida pela quantidade n"
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
                description = "Curva de Gauss para probabilidade contínua"
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
                .padding(horizontal = 12.dp, vertical = 18.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Functions,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Manual de Fórmulas e Cálculos LaTeX",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Guia didático de sintaxe, regras de cálculo e catálogo para copiar",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

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
                        text = { Text("Regras de Cálculo", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Sintaxe & Comandos", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (selectedTab) {
                    0 -> {
                        // 1. Ready formulas catalog with search & 1-tap copy
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar fórmula (ex: Bhaskara, Juros, Força, VPL...)") },
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
                    }

                    1 -> {
                        // 2. Rules for Calculations in LaTeX
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                SectionCard(title = "Regra 1: Como o Citra Renderiza Fórmulas") {
                                    Text(
                                        text = "No editor de notas, você pode inserir expressões matemáticas de duas formas:\n\n" +
                                               "• Em linha com o texto: use um único cifrão: \$x + y = 10\$\n" +
                                               "• Em bloco centralizado: use cifrão duplo:\n\n" +
                                               "$$\n" +
                                               "M = C(1 + i)^t\n" +
                                               "$$\n\n" +
                                               "O Citra possui um motor de renderização matemática nativo ultrarrápido que converte automaticamente símbolos, frações e raízes em tipografia elegante.",
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            item {
                                SectionCard(title = "Regra 2: Agrupamento com Chaves { }") {
                                    Text(
                                        text = "Em LaTeX, comandos operam apenas sobre o próximo caractere a menos que você use chaves { } para agrupar:\n\n" +
                                               "• Errado: x^10  → o LaTeX lê x¹ seguido de 0\n" +
                                               "• Correto: x^{10} → potência de dois dígitos x¹⁰\n\n" +
                                               "• Errado: \\frac 12+x → apenas 1 sobre 2\n" +
                                               "• Correto: \\frac{1}{2 + x} → 1 sobre (2 + x)\n\n" +
                                               "Dica de ouro: sempre envolva expoentes, índices e numeradores/denominadores compostos entre chaves { }.",
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                    CommandExample(cmd = "x^{n+1} + y_{k-1}", desc = "Agrupamento de expoente e índice composto", onCopy = { copyToClipboard("x^{n+1} + y_{k-1}") })
                                }
                            }

                            item {
                                SectionCard(title = "Regra 3: Parênteses e Colchetes Auto-Ajustáveis") {
                                    Text(
                                        text = "Para frações e termos altos, parênteses normais ( ) ficam pequenos. Use \\left( e \\right) para que eles cresçam automaticamente até a altura da fórmula:",
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                    CommandExample(cmd = "\\left( \\frac{a + b}{c - d} \\right)", desc = "Parênteses proporcionais à fração", onCopy = { copyToClipboard("\\left( \\frac{a + b}{c - d} \\right)") })
                                    CommandExample(cmd = "\\left[ \\sum_{i=1}^n x_i \\right]^2", desc = "Colchetes proporcionais ao somatório", onCopy = { copyToClipboard("\\left[ \\sum_{i=1}^n x_i \\right]^2") })
                                }
                            }

                            item {
                                SectionCard(title = "Regra 4: Cálculos Passo a Passo Alinhados") {
                                    Text(
                                        text = "Para resolver equações mostrando passo a passo, use o ambiente aligned com & antes do sinal de igual (=) para que fiquem perfeitamente alinhados na vertical:",
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                    val alignedEx = "\\begin{aligned}\n2x + 5 &= 15 \\\\\n2x &= 15 - 5 \\\\\n2x &= 10 \\\\\nx &= \\frac{10}{2} = 5\n\\end{aligned}"
                                    CommandExample(cmd = alignedEx, desc = "Resolução de equação passo a passo alinhada no =", onCopy = { copyToClipboard(alignedEx) })
                                }
                            }

                            item {
                                SectionCard(title = "Regra 5: Multiplicação Clara e Espaçamento") {
                                    Text(
                                        text = "Nunca use a letra 'x' para multiplicação, pois o leitor entenderá como a variável x. Use:\n\n" +
                                               "• \\cdot para ponto de multiplicação: 3 \\cdot 4 = 12\n" +
                                               "• \\times para multiplicação explícita ou notação científica: 2.5 \\times 10^8\n" +
                                               "• \\quad ou \\, para espaçamento estético entre fórmulas",
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                    CommandExample(cmd = "3 \\times 10^8 \\text{ m/s}", desc = "Notação científica com unidade de medida", onCopy = { copyToClipboard("3 \\times 10^8 \\text{ m/s}") })
                                }
                            }
                        }
                    }

                    2 -> {
                        // 3. Complete syntax and commands guide
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                SectionCard(title = "Operações Básicas & Aritmética") {
                                    CommandExample(cmd = "\\frac{a}{b}", desc = "Fração a sobre b", onCopy = { copyToClipboard("\\frac{a}{b}") })
                                    CommandExample(cmd = "\\times", desc = "Multiplicação em cruz (×)", onCopy = { copyToClipboard("\\times") })
                                    CommandExample(cmd = "\\cdot", desc = "Multiplicação por ponto (·)", onCopy = { copyToClipboard("\\cdot") })
                                    CommandExample(cmd = "\\div", desc = "Divisão aritmética (÷)", onCopy = { copyToClipboard("\\div") })
                                    CommandExample(cmd = "\\pm", desc = "Mais ou menos (±)", onCopy = { copyToClipboard("\\pm") })
                                    CommandExample(cmd = "\\mp", desc = "Menos ou mais (∓)", onCopy = { copyToClipboard("\\mp") })
                                }
                            }

                            item {
                                SectionCard(title = "Potenciação, Raízes e Índices") {
                                    CommandExample(cmd = "x^2", desc = "Potência simples", onCopy = { copyToClipboard("x^2") })
                                    CommandExample(cmd = "x^{n+1}", desc = "Potência com expoente agrupado", onCopy = { copyToClipboard("x^{n+1}") })
                                    CommandExample(cmd = "x_1, \\quad x_{i,j}", desc = "Subscritos e índices de vetores/matrizes", onCopy = { copyToClipboard("x_{i,j}") })
                                    CommandExample(cmd = "\\sqrt{x}", desc = "Raiz quadrada simples", onCopy = { copyToClipboard("\\sqrt{x}") })
                                    CommandExample(cmd = "\\sqrt[3]{x}", desc = "Raiz cúbica ou n-ésima", onCopy = { copyToClipboard("\\sqrt[3]{x}") })
                                }
                            }

                            item {
                                SectionCard(title = "Cálculo: Limites, Derivadas e Integrais") {
                                    CommandExample(cmd = "\\lim_{x \\to 0} f(x)", desc = "Limite com x tendendo a 0", onCopy = { copyToClipboard("\\lim_{x \\to 0} f(x)") })
                                    CommandExample(cmd = "\\frac{df}{dx}", desc = "Derivada simples de Leibniz", onCopy = { copyToClipboard("\\frac{df}{dx}") })
                                    CommandExample(cmd = "\\frac{\\partial f}{\\partial x}", desc = "Derivada parcial", onCopy = { copyToClipboard("\\frac{\\partial f}{\\partial x}") })
                                    CommandExample(cmd = "\\int_{a}^{b} f(x) \\, dx", desc = "Integral definida de a até b", onCopy = { copyToClipboard("\\int_{a}^{b} f(x) \\, dx") })
                                    CommandExample(cmd = "\\oint", desc = "Integral de linha fechada ou contorno", onCopy = { copyToClipboard("\\oint") })
                                    CommandExample(cmd = "\\sum_{i=1}^{n} x_i", desc = "Somatório com limites", onCopy = { copyToClipboard("\\sum_{i=1}^{n} x_i") })
                                    CommandExample(cmd = "\\prod_{i=1}^{n} x_i", desc = "Produtório com limites", onCopy = { copyToClipboard("\\prod_{i=1}^{n} x_i") })
                                }
                            }

                            item {
                                SectionCard(title = "Matrizes e Vetores") {
                                    val matEx = "\\begin{pmatrix}\na & b \\\\\nc & d\n\\end{pmatrix}"
                                    CommandExample(cmd = matEx, desc = "Matriz 2x2 com parênteses", onCopy = { copyToClipboard(matEx) })
                                    CommandExample(cmd = "\\vec{v} = \\begin{pmatrix} x \\\\ y \\end{pmatrix}", desc = "Vetor coluna", onCopy = { copyToClipboard("\\vec{v} = \\begin{pmatrix} x \\\\ y \\end{pmatrix}") })
                                    CommandExample(cmd = "\\vec{u} \\cdot \\vec{v}", desc = "Produto escalar entre vetores", onCopy = { copyToClipboard("\\vec{u} \\cdot \\vec{v}") })
                                }
                            }

                            item {
                                SectionCard(title = "Relações, Lógica e Conjuntos") {
                                    CommandExample(cmd = "\\le, \\quad \\ge", desc = "Menor ou igual (≤) e maior ou igual (≥)", onCopy = { copyToClipboard("\\le \\quad \\ge") })
                                    CommandExample(cmd = "\\neq, \\quad \\approx", desc = "Diferente (≠) e aproximadamente (≈)", onCopy = { copyToClipboard("\\neq \\quad \\approx") })
                                    CommandExample(cmd = "\\in, \\quad \\notin", desc = "Pertence (∈) e não pertence (∉)", onCopy = { copyToClipboard("\\in \\quad \\notin") })
                                    CommandExample(cmd = "\\subset, \\quad \\cup, \\quad \\cap", desc = "Subconjunto (⊂), união (∪) e interseção (∩)", onCopy = { copyToClipboard("\\subset \\quad \\cup \\quad \\cap") })
                                    CommandExample(cmd = "\\forall, \\quad \\exists", desc = "Para todo (∀) e existe (∃)", onCopy = { copyToClipboard("\\forall \\quad \\exists") })
                                    CommandExample(cmd = "\\implies, \\quad \\iff", desc = "Implica (⇒) e se, e somente se (⇔)", onCopy = { copyToClipboard("\\implies \\quad \\iff") })
                                }
                            }

                            item {
                                SectionCard(title = "Letras Gregas Essenciais") {
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
