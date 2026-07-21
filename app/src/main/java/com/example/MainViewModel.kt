package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.net.URL

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

data class CompetitorStats(
    val url: String,
    val wordCount: Int,
    val keywordDensity: Float,
    val keywordCount: Int
)

data class SeoAnalysisResult(
    val url: String,
    val title: String,
    val metaDescription: String,
    val h1Tags: List<String>,
    val wordCount: Int,
    val keywordDensity: Float,
    val keywordCount: Int,
    val textContent: String,
    val targetKeyword: String,
    val headerScore: Int,
    val readabilityScore: Int,
    val totalImagesCount: Int,
    val imagesWithoutAltCount: Int,
    val competitorStats: CompetitorStats? = null,
    val aiSuggestions: String? = null
)

sealed class AnalysisState {
    object Idle : AnalysisState()
    object Loading : AnalysisState()
    data class Success(val result: SeoAnalysisResult) : AnalysisState()
    data class Error(val message: String) : AnalysisState()
}

class MainViewModel : ViewModel() {

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    fun analyzeUrl(urlStr: String, targetKeyword: String, competitorUrlStr: String) {
        if (urlStr.isBlank() || targetKeyword.isBlank()) {
            _analysisState.value = AnalysisState.Error("Please enter both URL and Target Keyword")
            return
        }

        _analysisState.value = AnalysisState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure url has protocol
                val validUrl = if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
                    "https://$urlStr"
                } else {
                    urlStr
                }

                val document = Jsoup.connect(validUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(10000)
                    .get()
                
                val title = document.title()
                val metaDescElement = document.select("meta[name=description]").first()
                val metaDescription = metaDescElement?.attr("content") ?: "No meta description found"
                
                val h1Elements = document.select("h1")
                val h1Tags = h1Elements.map { it.text() }
                
                val imgElements = document.select("img")
                val totalImagesCount = imgElements.size
                val imagesWithoutAltCount = imgElements.count { it.attr("alt").isNullOrBlank() }
                
                val textContent = document.body().text()
                val words = textContent.split("\\s+".toRegex()).filter { it.isNotBlank() }
                val wordCount = words.size
                
                val keywordLower = targetKeyword.lowercase()
                var keywordCount = 0
                val textLower = textContent.lowercase()
                
                // Simple keyword counting (occurrences)
                var index = textLower.indexOf(keywordLower)
                while (index >= 0) {
                    keywordCount++
                    index = textLower.indexOf(keywordLower, index + keywordLower.length)
                }
                
                val density = if (wordCount > 0) {
                    (keywordCount.toFloat() / wordCount.toFloat()) * 100f
                } else {
                    0f
                }
                
                var headerScore = 0
                if (h1Tags.isNotEmpty()) headerScore += 50
                if (h1Tags.any { it.contains(targetKeyword, ignoreCase = true) }) headerScore += 50
                
                val sentences = textContent.split(Regex("[.!?]+")).filter { it.isNotBlank() }
                val sentenceCount = sentences.size
                val readabilityScore = if (sentenceCount > 0 && wordCount > 0) {
                    val wordsPerSentence = wordCount.toFloat() / sentenceCount.toFloat()
                    val score = 100f - ((wordsPerSentence - 10f).coerceAtLeast(0f) * 5f)
                    score.coerceIn(0f, 100f).toInt()
                } else {
                    0
                }
                
                var compStats: CompetitorStats? = null
                if (competitorUrlStr.isNotBlank()) {
                    val validCompUrl = if (!competitorUrlStr.startsWith("http://") && !competitorUrlStr.startsWith("https://")) {
                        "https://$competitorUrlStr"
                    } else {
                        competitorUrlStr
                    }
                    
                    try {
                        val compDoc = Jsoup.connect(validCompUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                            .timeout(10000)
                            .get()
                        val compText = compDoc.body().text()
                        val compWords = compText.split("\\s+".toRegex()).filter { it.isNotBlank() }
                        val compWordCount = compWords.size
                        
                        var compKeywordCount = 0
                        val compTextLower = compText.lowercase()
                        var compIndex = compTextLower.indexOf(keywordLower)
                        while (compIndex >= 0) {
                            compKeywordCount++
                            compIndex = compTextLower.indexOf(keywordLower, compIndex + keywordLower.length)
                        }
                        val compDensity = if (compWordCount > 0) {
                            (compKeywordCount.toFloat() / compWordCount.toFloat()) * 100f
                        } else 0f
                        
                        compStats = CompetitorStats(
                            url = validCompUrl,
                            wordCount = compWordCount,
                            keywordDensity = compDensity,
                            keywordCount = compKeywordCount
                        )
                    } catch (e: Exception) {
                        // Ignore competitor fetch errors
                    }
                }
                
                val result = SeoAnalysisResult(
                    url = validUrl,
                    title = title,
                    metaDescription = metaDescription,
                    h1Tags = h1Tags,
                    wordCount = wordCount,
                    keywordDensity = density,
                    keywordCount = keywordCount,
                    textContent = textContent,
                    targetKeyword = targetKeyword,
                    headerScore = headerScore,
                    readabilityScore = readabilityScore,
                    totalImagesCount = totalImagesCount,
                    imagesWithoutAltCount = imagesWithoutAltCount,
                    competitorStats = compStats
                )
                
                _analysisState.value = AnalysisState.Success(result)
            } catch (e: Exception) {
                _analysisState.value = AnalysisState.Error("Failed to analyze: ${e.message}")
            }
        }
    }

    fun generateSuggestions(result: SeoAnalysisResult) {
        viewModelScope.launch(Dispatchers.IO) {
            _analysisState.value = AnalysisState.Loading
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _analysisState.value = AnalysisState.Success(result.copy(aiSuggestions = "Error: Gemini API Key not configured. Please add it in AI Studio Secrets."))
                    return@launch
                }
                
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey
                )

                val prompt = """
                    You are an expert SEO analyst. Review the following on-page SEO metrics and provide actionable suggestions to improve the page ranking for the target keyword.
                    
                    Target Keyword: ${result.targetKeyword}
                    URL: ${result.url}
                    Title: ${result.title}
                    Meta Description: ${result.metaDescription}
                    H1 Tags: ${result.h1Tags.joinToString()}
                    Word Count: ${result.wordCount}
                    Keyword Density: ${String.format("%.2f", result.keywordDensity)}%
                    
                    Provide brief, bulleted suggestions on how to improve the content, tags, and structure to rank better on Google Search.
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                _analysisState.value = AnalysisState.Success(result.copy(aiSuggestions = response.text))
            } catch (e: Exception) {
                _analysisState.value = AnalysisState.Success(result.copy(aiSuggestions = "Failed to generate AI suggestions: ${e.message}"))
            }
        }
    }

    fun exportReport(uri: android.net.Uri, contentResolver: android.content.ContentResolver, result: SeoAnalysisResult) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val report = buildString {
                        appendLine("SEO Analysis Report")
                        appendLine("===================")
                        appendLine("URL: ${result.url}")
                        appendLine("Target Keyword: ${result.targetKeyword}")
                        appendLine()
                        appendLine("On-Page SEO Results:")
                        appendLine("--------------------")
                        appendLine("Title: ${result.title}")
                        appendLine("Meta Description: ${result.metaDescription}")
                        appendLine("H1 Tags: ${if (result.h1Tags.isEmpty()) "None" else result.h1Tags.joinToString()}")
                        appendLine()
                        appendLine("Optimization Metrics:")
                        appendLine("---------------------")
                        appendLine("Word Count: ${result.wordCount}")
                        appendLine("Keyword Density: ${String.format("%.2f", result.keywordDensity)}%")
                        appendLine("Header Score: ${result.headerScore}/100")
                        appendLine("Readability Score: ${result.readabilityScore}/100")
                        
                        if (result.aiSuggestions != null) {
                            appendLine()
                            appendLine("AI Optimization Suggestions:")
                            appendLine("----------------------------")
                            appendLine(result.aiSuggestions)
                        }
                    }
                    outputStream.write(report.toByteArray())
                }
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }
}
