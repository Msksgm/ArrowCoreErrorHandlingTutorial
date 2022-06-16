import arrow.core.*
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.computations.either
import arrow.core.computations.nullable
import arrow.typeclasses.Semigroup

fun main(args: Array<String>) {
    println("Hello World!")

    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    println("Program arguments: ${args.joinToString()}")

    val fields = listOf(
        FormField("Invalid Email Domain Lable", "nowhere.com"),
        FormField("Too Long Email Label", "nowheretoolong${(0..251).map { "0" }}"),
        FormField("Valid Email Lablel", "getlost@nowhere.com")
    )
    Rules(Strategy.FailFast, fields)
    Rules(Strategy.ErrorAccumulation, fields)
}

object Lettuce
object Knife
object Salad


sealed class CookingException {
    object NastyLettuce : CookingException()
    object KnifeIsDull : CookingException()
    data class InsufficientAmountOfLettuce(val quantityInGrams: Int) : CookingException()
}

typealias NastyLettuce = CookingException.NastyLettuce
typealias KnifeIsDull = CookingException.KnifeIsDull
typealias InsufficientAmountOfLettuce = CookingException.InsufficientAmountOfLettuce

fun takeFoodFromRefrigerator(): Either<NastyLettuce, Lettuce> = Right(Lettuce)
fun getKnife(): Either<KnifeIsDull, Knife> = Right(Knife)
fun lunch(knife: Knife, food: Lettuce): Either<InsufficientAmountOfLettuce, Salad> =
    Left(InsufficientAmountOfLettuce(5))

suspend fun prepareEither(): Either<CookingException, Salad> =
    either {
        val lettuce = takeFoodFromRefrigerator().bind()
        val knife = getKnife().bind()
        val salad = lunch(knife, lettuce).bind()
        salad
    }

sealed class ValidationError(val msg: String) {
    data class DoesNotContain(val value: String) : ValidationError("Did not contain $value")
    data class MaxLength(val value: Int) : ValidationError("Exceeded length of $value")
    data class NotAnEmail(val reasons: Nel<ValidationError>) : ValidationError("Not a valid email")
}

data class FormField(val label: String, val value: String)
data class Email(val value: String)

/** strategies **/
sealed class Strategy {
    object FailFast : Strategy()
    object ErrorAccumulation : Strategy()
}

/** Abstracts away invoke strategy **/
object Rules {

    private fun FormField.contains(needle: String): ValidatedNel<ValidationError, FormField> =
        if (value.contains(needle, false)) validNel()
        else ValidationError.DoesNotContain(needle).invalidNel()

    private fun FormField.maxLength(maxLength: Int): ValidatedNel<ValidationError, FormField> =
        if (value.length <= maxLength) validNel()
        else ValidationError.MaxLength(maxLength).invalidNel()

    private fun FormField.validateErrorAccumulate(): ValidatedNel<ValidationError, Email> =
        contains("@").zip(
            Semigroup.nonEmptyList(), // accumulates errors in a non empty list, can be omited for NonEmptyList
            maxLength(250)
        ) { _, _ -> Email(value) }.handleErrorWith { ValidationError.NotAnEmail(it).invalidNel() }

    /** either blocks support binding over Validated values with no additional cost or need to convert first to Either **/
    private fun FormField.validateFailFast(): Either<Nel<ValidationError>, Email> =
        either.eager {
            contains("@").bind() // fails fast on first error found
            maxLength(250).bind()
            Email(value)
        }

    operator fun invoke(strategy: Strategy, fields: List<FormField>): Either<Nel<ValidationError>, List<Email>> =
        when (strategy) {
            Strategy.FailFast ->
                fields.traverseEither { it.validateFailFast() }
            Strategy.ErrorAccumulation ->
                fields.traverseValidated(Semigroup.nonEmptyList()) {
                    it.validateErrorAccumulate()
                }.toEither()
        }
}