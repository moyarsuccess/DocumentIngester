package ca.flutra.new

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaEmbeddingModel
import java.time.Duration

object ModelProvider {

    val chatModel: ChatModel by lazy {
        OllamaChatModel.builder()
            .baseUrl(Config.OLLAMA_BASE_URL)
            .modelName(Config.CHAT_MODEL_NAME)
            .temperature(0.0)
            .timeout(Duration.ofMinutes(5))
            .build()
    }

    val embeddingModel: EmbeddingModel by lazy {
        OllamaEmbeddingModel.builder()
            .baseUrl(Config.OLLAMA_BASE_URL)
            .modelName(Config.EMBEDDING_MODEL_NAME)
            .timeout(Duration.ofMinutes(2))
            .build()
    }
}
