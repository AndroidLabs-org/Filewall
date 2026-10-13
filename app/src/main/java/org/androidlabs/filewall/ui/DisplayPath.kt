package org.androidlabs.filewall.ui

/**
 * Wraps a path for display so right-to-left layout cannot reorder it.
 *
 * A path is a neutral run as far as the bidirectional algorithm is concerned, so under an
 * RTL locale its leading "./" is moved to the end: the rule on ./Documents renders as
 * "Documents/." and ./alpha.txt as "alpha.txt/.". That is the field which says what a rule
 * covers, so it has to read the same in every locale.
 *
 * The isolate characters are display-only. Nothing stored, exported or compared goes
 * through this.
 */
fun String.asDisplayPath(): String = LTR_ISOLATE + this + POP_DIRECTIONAL_ISOLATE

// Built from code points rather than written out: a literal puts invisible
// direction-changing characters into this file, which is what BidiSpoofing exists to
// catch and which nobody reading the source can see.
private val LTR_ISOLATE = Char(0x2066).toString()
private val POP_DIRECTIONAL_ISOLATE = Char(0x2069).toString()
