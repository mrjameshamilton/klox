package eu.jameshamilton.klox.compile.composer

import eu.jameshamilton.klox.compile.FunctionVariableAllocator
import eu.jameshamilton.klox.compile.VariableAllocator
import eu.jameshamilton.klox.parse.FunctionExpr
import proguard.classfile.editor.CompactCodeAttributeComposer.Label
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

sealed class Switch(
    private val cases: List<SwitchCase<*>>,
    private val defaultCase: (Composer.() -> Unit)?
) {
    private val labels = mutableMapOf<SwitchCase<*>, Label>()
    var SwitchCase<*>.label: Label
        get() = labels[this]!!
        set(value) { labels[this] = value }

    open fun generate(composer: Composer): Composer = with(composer) {
        val end = pushBreakLabel()
        val default = if (defaultCase == null) end else Label()

        transformSubjectValue()

        val lookupIndices = cases
            .onEach { it.label = Label() }
            .groupBy { valueToIndex(it.value) }

        val lookupLabels = lookupIndices
            .map { it.key to Label() }
            .toMap()
            // lookupswitch indices must be sorted in ascending order
            .toSortedMap()

        lookupswitch(default, lookupLabels.keys.toTypedArray().toIntArray(), lookupLabels.values.toTypedArray())

        generateEntries(lookupIndices, lookupLabels, default)

        for (case in cases) {
            label(case.label)
            case.composer(this)
        }

        defaultCase?.let {
            label(default)
            it(this)
        }

        popBreakLabel()
        label(end)
    }

    abstract fun Composer.generateEntries(
        lookupIndices: Map<Int, List<SwitchCase<*>>>,
        lookupLabels: Map<Int, Label>,
        default: Label?
    )

    open fun Composer.transformSubjectValue(): Composer { return this }
    abstract fun valueToIndex(value: Any?): Int
}

class IntegerSwitch(cases: List<SwitchCase<Int>>, defaultCase: (Composer.() -> Unit)?) :
    Switch(cases, defaultCase) {

    override fun Composer.generateEntries(
        lookupIndices: Map<Int, List<SwitchCase<*>>>,
        lookupLabels: Map<Int, Label>,
        default: Label?
    ) {
        // TODO: refactor this?
        // Replace labels, since in an integer switch, the labels are the indices
        for ((valueIndex, cases) in lookupIndices.entries) {
            for (case in cases) {
                case.label = lookupLabels[valueIndex]!!
            }
        }
    }

    override fun valueToIndex(value: Any?): Int = value as Int
}

class StringSwitch(private val variableAllocator: VariableAllocator, cases: List<SwitchCase<String>>, defaultCase: (Composer.() -> Unit)? = null) :
    Switch(cases, defaultCase) {
    private var temporary = variableAllocator.next()

    override fun Composer.generateEntries(
        lookupIndices: Map<Int, List<SwitchCase<*>>>,
        lookupLabels: Map<Int, Label>,
        default: Label?
    ) {
        for ((valueIndex, cases) in lookupIndices.entries) {
            label(lookupLabels[valueIndex])
            var nextLabel: Label? = null
            for ((caseIndex, case) in cases.withIndex()) {
                if (nextLabel != null) label(nextLabel)
                nextLabel = if (caseIndex == cases.lastIndex) default else Label()
                ldc(case.value as String)
                aload(temporary)
                invokevirtual("java/lang/Object", "equals", "(Ljava/lang/Object;)Z")
                ifeq(nextLabel)
                goto_(case.label)
            }
        }
        variableAllocator.free(temporary)
    }

    override fun Composer.transformSubjectValue(): Composer = with(this) {
        astore(temporary)
        aload(temporary)
        invokevirtual("java/lang/String", "hashCode", "()I")
    }

    override fun valueToIndex(value: Any?): Int = value.hashCode()
}

class SwitchCase<T>(val value: T, val composer: Composer.() -> Unit)
infix fun String.case(case: Composer.() -> Unit) = SwitchCase(this, case)
infix fun Int.case(case: Composer.() -> Unit) = SwitchCase(this, case)

fun Composer.switch(
    variableAllocator: VariableAllocator,
    vararg cases: SwitchCase<*>,
    default: Composer.() -> Unit = {}
): Composer = when {
    cases.all { it.value is String } -> StringSwitch(
        variableAllocator,
        cases.map { SwitchCase(it.value as String, it.composer) }.toList(),
        default
    ).generate(this)
    cases.all { it.value is Int } -> IntegerSwitch(
        cases.map { SwitchCase(it.value as Int, it.composer) }.toList(),
        default
    ).generate(this)
    else -> throw IllegalStateException("Unsupported switch type")
}

fun Composer.switch(function: FunctionExpr, vararg cases: SwitchCase<*>, default: Composer.() -> Unit = {}): Composer =
    switch(
        FunctionVariableAllocator(function),
        cases = cases,
        default = default
    )

fun Composer.switch(temporary: Int, vararg cases: SwitchCase<*>, default: Composer.() -> Unit = {}): Composer =
    switch(
        object : VariableAllocator {
            override fun next(): Int = temporary
            override fun free(index: Int) {}
        },
        cases = cases,
        default = default
    )
