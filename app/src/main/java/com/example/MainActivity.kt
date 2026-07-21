package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
          SeoScreen(
              viewModel = viewModel,
              modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}

@Composable
fun SeoScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.analysisState.collectAsState()
    var url by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    var competitorUrl by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    var currentResultToExport by remember { mutableStateOf<SeoAnalysisResult?>(null) }
    
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            uri?.let {
                currentResultToExport?.let { result ->
                    viewModel.exportReport(it, contentResolver, result)
                }
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Website URL") },
            leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("url_input"),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text("Target Keyword") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("keyword_input"),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        
        OutlinedTextField(
            value = competitorUrl,
            onValueChange = { competitorUrl = it },
            label = { Text("Competitor URL (Optional)") },
            leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("competitor_url_input"),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        
        Button(
            onClick = { viewModel.analyzeUrl(url, keyword, competitorUrl) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("analyze_button"),
            enabled = state !is AnalysisState.Loading,
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Analyze Page")
        }
        
        when (val s = state) {
            is AnalysisState.Idle -> {
                Text(
                    text = "Enter a URL and target keyword to begin analysis.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp)
                )
            }
            is AnalysisState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                }
            }
            is AnalysisState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is AnalysisState.Success -> {
                AnalysisResults(
                    result = s.result,
                    onGetSuggestions = { viewModel.generateSuggestions(s.result) },
                    onExport = { 
                        currentResultToExport = s.result
                        createDocumentLauncher.launch("seo_report.txt")
                    }
                )
            }
        }
    }
}

@Composable
fun AnalysisResults(result: SeoAnalysisResult, onGetSuggestions: () -> Unit, onExport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("On-Page SEO Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            
            Text("Title:", fontWeight = FontWeight.Bold)
            Text(result.title)
            
            Text("Meta Description:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(result.metaDescription)
            
            Text("H1 Tags (${result.h1Tags.size}):", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            result.h1Tags.forEach { h1 ->
                Text("• $h1")
            }
            if (result.h1Tags.isEmpty()) {
                Text("No H1 tags found", color = MaterialTheme.colorScheme.error)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Text("Optimization Metrics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    title = "Readability",
                    value = "${result.readabilityScore}/100",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Header Score",
                    value = "${result.headerScore}/100",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    title = "Key Density",
                    value = "${String.format("%.2f", result.keywordDensity)}%",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Word Count",
                    value = "${result.wordCount}",
                    modifier = Modifier.weight(1f)
                )
            }
            
            if (result.competitorStats != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Competitor Analysis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("URL: ${result.competitorStats.url}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(
                        title = "Comp. Density",
                        value = "${String.format("%.2f", result.competitorStats.keywordDensity)}%",
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    MetricCard(
                        title = "Comp. Words",
                        value = "${result.competitorStats.wordCount}",
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
    
    SeoChecklist(result = result)
    
    if (result.aiSuggestions != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoGraph, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Optimization Suggestions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text(result.aiSuggestions, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    } else {
        Button(
            onClick = onGetSuggestions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag("suggestions_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(Icons.Default.AutoGraph, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Get AI Suggestions")
        }
    }
    
    Button(
        onClick = onExport,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("export_button"),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(24.dp)
    ) {
        Icon(Icons.Default.Download, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Download Report")
    }
}

@Composable
fun SeoChecklist(result: SeoAnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SEO Best Practices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            
            val metaLengthOk = result.metaDescription.length in 50..160
            ChecklistItem("Meta description length (50-160 chars)", metaLengthOk, "${result.metaDescription.length} chars")
            
            val h1Present = result.h1Tags.isNotEmpty()
            ChecklistItem("H1 tag present", h1Present, if (h1Present) "Found ${result.h1Tags.size}" else "Missing")
            
            val keywordInH1 = result.h1Tags.any { it.contains(result.targetKeyword, ignoreCase = true) }
            ChecklistItem("Target keyword in H1", keywordInH1, if (keywordInH1) "Found" else "Not found")
            
            val imagesOk = result.imagesWithoutAltCount == 0
            ChecklistItem("Images have alt tags", imagesOk, if (result.totalImagesCount == 0) "No images" else "${result.imagesWithoutAltCount} missing alt")
            
            val wordCountOk = result.wordCount >= 300
            ChecklistItem("Word count > 300", wordCountOk, "${result.wordCount} words")
            
            val densityOk = result.keywordDensity in 1f..3f
            ChecklistItem("Keyword density (1-3%)", densityOk, "${String.format("%.2f", result.keywordDensity)}%")
        }
    }
}

@Composable
fun ChecklistItem(label: String, isPassed: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = if (isPassed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isPassed) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = contentColor)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
        }
    }
}
