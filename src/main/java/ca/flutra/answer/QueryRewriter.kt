package ca.flutra.answer

import dev.langchain4j.model.ollama.OllamaChatModel

object QueryRewriter {

    private val chatModel: OllamaChatModel by lazy {
        OllamaChatModel.OllamaChatModelBuilder()
            .baseUrl("http://localhost:11434/")
            .modelName("llama3.2")
            .build()
            ?: throw IllegalStateException("Failed to provide the model!")
    }

    fun rewriteQuery(question: String): String {
        val prompt = """
            ""Rewrite the user's question to be a more specific question that is more likely to surface relevant content in the Knowledge Base.""${'"'}
            You are about to look up information in a Knowledge Base to answer the user's question.

            And this is the user's current question:
            $question

            Respond only with a short, refined question that we will use to search the Knowledge Base.
        """.trimIndent()
        return chatModel.chat(prompt)
    }
}