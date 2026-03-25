package ca.flutra.rag

fun main() {
    println("=== RAG Assistant Ready ===")

    while (true) {
        print("\nAsk a question (or 'quit'): ")
        val question = readlnOrNull()?.trim() ?: break
        if (question.lowercase() == "quit") break

        val (answer, chunks) = AnswerService.answerQuestion(question)

        println("\n--- Answer ---")
        println(answer)
        println("\n--- Sources used ---")
        chunks.forEach { println("  • ${it.metadata["source"]}") }
    }
}
