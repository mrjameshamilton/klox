package eu.jameshamilton.klox.compile.composer

import proguard.classfile.editor.CompactCodeAttributeComposer.Label
import java.util.Stack
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

fun Composer.Label() = createLabel()
fun Composer.labels(n: Int): List<Label> =
    (1..n).map { this.createLabel() }

private var breakLabels: Stack<Label> = Stack()
private var continueLabels: Stack<Label> = Stack()

fun Composer.pushBreakLabel(): Label = breakLabels.push(createLabel())
fun Composer.popBreakLabel() = breakLabels.pop()

fun Composer.break_(): Composer {
    if (breakLabels.isNotEmpty()) goto_(breakLabels.peek())
    return this
}

fun Composer.pushContinueLabel(): Label = continueLabels.push(createLabel())
fun Composer.popContinueLabel() = continueLabels.pop()

fun Composer.continue_(): Composer {
    if (continueLabels.isNotEmpty()) goto_(continueLabels.peek())
    return this
}

data class LoopLabelSet(val condition: Label, val body: Label, val end: Label)

/**
 * Provides a set of labels for a loop.
 *
 * The end label is automatically applied, but the condition and body labels must be applied manually.
 *
 * Continue and Break labels are pushed onto their respective stacks, so that
 * `break_()` and `continue_()` can be used to jump to the end of the loop, or continue respectively.
 */
fun Composer.loop(block: Composer.(Label, Label, Label) -> Unit) {
    with(LoopLabelSet(pushContinueLabel(), createLabel(), pushBreakLabel())) {
        block(this@loop, condition, body, end)
        popContinueLabel()
        label(popBreakLabel())
    }
}
