package ca.flutra.rag

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage

object AnswerService {

    /**
     * Builds the full message list for a RAG query.
     * Equivalent of Python's make_rag_messages().
     */
    fun makeRagMessages(
        question: String,
        history: List<ChatMessage>,
        chunks: List<Result>,
    ): List<ChatMessage> {
        val context = chunks.joinToString("\n\n") { chunk ->
            "Extract from ${chunk.metadata["source"]}:\n${chunk.pageContent}"
        }
        val systemPrompt = Config.SYSTEM_PROMPT.replace("{context}", context)
        return buildList {
            add(SystemMessage.from(systemPrompt))
            addAll(history)
            add(UserMessage.from(question))
        }
    }

    /**
     * Full RAG pipeline: retrieve → rerank → answer.
     * Equivalent of Python's answer_question().
     *
     * @return Pair of (answer string, retrieved chunks used for context)
     */
    fun answerQuestion(
        question: String,
        history: List<ChatMessage> = emptyList(),
    ): Pair<String, List<Result>> {
        // 1. Retrieve and merge chunks (original + rewritten query).
        // Include both user and AI turns so the query rewriter has full conversation context.
        val historyAsMaps = history.mapNotNull { message ->
            when (message) {
                is UserMessage -> mapOf("role" to "user", "content" to message.singleText())
                is AiMessage  -> mapOf("role" to "assistant", "content" to message.text())
                else          -> null
            }
        }

        val candidates = Retriever.fetchContext(question, historyAsMaps)

        // 2. Rerank and take top-K
        val reranked = Reranker.rerank(question, candidates)
        val topChunks = reranked.take(Config.FINAL_K)

        // 3. Build messages and call LLM
        val messages = makeRagMessages(question, history, topChunks)
        val answer = ModelProvider.chatModel.chat(messages).aiMessage().text()

        return answer to topChunks
    }
}
