package id.math315.soal150

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SoalKotlinActivity : AppCompatActivity() {

    data class Question(
        val text: String,
        val options: List<String>,
        val answer: Int
    )

    private val questions = listOf(
        Question(
            "Hasil dari 2x + 5 = 15 adalah ...",
            listOf("3", "5", "7", "10"),
            1
        ),
        Question(
            "Hasil dari 3² + 4² adalah ...",
            listOf("12", "25", "49", "16"),
            1
        ),
        Question(
            "Akar kuadrat dari 144 adalah ...",
            listOf("10", "11", "12", "14"),
            2
        ),
        Question(
            "Gradien garis y = 3x + 2 adalah ...",
            listOf("2", "3", "5", "-3"),
            1
        ),
        Question(
            "Hasil dari 25% × 200 adalah ...",
            listOf("25", "40", "50", "75"),
            2
        )
    )

    private val answers = mutableMapOf<Int, Int>()
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(241, 245, 251))
            setPadding(20, 20, 20, 20)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "MATH315\nSOAL KELAS 9 SMP"
            textSize = 24f
            setTextColor(Color.rgb(16, 43, 80))
            gravity = Gravity.CENTER
            setPadding(10, 20, 10, 25)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        content.addView(title)

        val subtitle = TextView(this).apply {
            text = "Latihan Matematika Native Kotlin"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        }
        content.addView(subtitle)

        questions.forEachIndexed { index, question ->

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(Color.WHITE)
            }

            val questionText = TextView(this).apply {
                text = "${index + 1}. ${question.text}"
                textSize = 17f
                setTextColor(Color.rgb(24, 49, 83))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            card.addView(questionText)

            val group = RadioGroup(this).apply {
                orientation = RadioGroup.VERTICAL
                setPadding(0, 10, 0, 10)
            }

            question.options.forEachIndexed { optionIndex, option ->
                val radio = RadioButton(this).apply {
                    id = android.view.View.generateViewId()
                    text = "${'A' + optionIndex}. $option"
                    textSize = 15f
                    tag = optionIndex
                }
                group.addView(radio)

                if (answers[index] == optionIndex) {
                    group.check(radio.id)
                }
            }

            group.setOnCheckedChangeListener { _, checkedId ->
                val selected = group.findViewById<RadioButton>(checkedId)
                if (selected != null) {
                    answers[index] = selected.tag as Int
                }
            }

            card.addView(group)

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 18
            }

            content.addView(card, params)
        }

        val checkButton = Button(this).apply {
            text = "CEK NILAI"
            setOnClickListener { checkAnswers() }
        }
        content.addView(checkButton)

        resultText = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(16, 43, 80))
            setPadding(10, 20, 10, 20)
        }
        content.addView(resultText)

        val backButton = Button(this).apply {
            text = "KEMBALI KE MENU"
            setOnClickListener { finish() }
        }
        content.addView(backButton)

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun checkAnswers() {
        val correct = questions.indices.count {
            answers[it] == questions[it].answer
        }

        val unanswered = questions.size - answers.size
        val score = correct * 100 / questions.size

        resultText.text =
            "HASIL LATIHAN\n\n" +
            "Benar: $correct\n" +
            "Salah: ${answers.size - correct}\n" +
            "Belum dijawab: $unanswered\n\n" +
            "NILAI: $score"
    }
}
