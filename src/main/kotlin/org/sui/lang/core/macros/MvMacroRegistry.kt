package org.sui.lang.core.macros

enum class MacroReturnKind {
    UNIT,
    UNKNOWN,
    BYTE_STRING,
    OPTION,
    RESULT,
}

data class MacroParam(
    val name: String,
    val type: String,
)

data class MacroSpec(
    val name: String,
    val tailText: String,
    val typeText: String,
    val insertText: String = "()",
    val minArgs: Int? = null,
    val maxArgs: Int? = null,
    val returnKind: MacroReturnKind = MacroReturnKind.UNKNOWN,
    val params: List<MacroParam> = emptyList(),
) {
    val completionName: String get() = "$name!"
    fun fixedArgsCountOrNull(): Int? =
        if (minArgs != null && maxArgs != null && minArgs == maxArgs) minArgs else null
}

object MvMacroRegistry {
    private val builtinSpecs: List<MacroSpec> = listOf(
        MacroSpec(
            name = "assert",
            tailText = "(_: bool, err: u64)",
            typeText = "()",
            minArgs = 2,
            maxArgs = 2,
            returnKind = MacroReturnKind.UNIT,
            params = listOf(
                MacroParam("_", "bool"),
                MacroParam("err", "u64"),
            ),
        ),
        MacroSpec(
            name = "debug",
            tailText = "(_: ...)",
            typeText = "()",
            returnKind = MacroReturnKind.UNIT,
            params = listOf(
                MacroParam("_", "..."),
            ),
        ),
        MacroSpec(
            name = "option",
            tailText = "(_)",
            typeText = "option<T>",
            minArgs = 1,
            maxArgs = 1,
            returnKind = MacroReturnKind.OPTION,
            params = listOf(
                MacroParam("_", "T"),
            ),
        ),
        MacroSpec(
            name = "result",
            tailText = "(_, _)",
            typeText = "result<T, E>",
            minArgs = 2,
            maxArgs = 2,
            returnKind = MacroReturnKind.RESULT,
            params = listOf(
                MacroParam("_", "T"),
                MacroParam("_", "E"),
            ),
        ),
        MacroSpec(
            name = "bcs",
            tailText = "(_)",
            typeText = "vector<u8>",
            minArgs = 1,
            maxArgs = 1,
            returnKind = MacroReturnKind.BYTE_STRING,
            params = listOf(
                MacroParam("_", "T"),
            ),
        ),
        MacroSpec(
            name = "object",
            tailText = "(_)",
            typeText = "object",
            params = listOf(
                MacroParam("_", "T"),
            ),
        ),
        MacroSpec(
            name = "transfer",
            tailText = "(_, _)",
            typeText = "()",
            minArgs = 2,
            maxArgs = 2,
            returnKind = MacroReturnKind.UNIT,
            params = listOf(
                MacroParam("obj", "T"),
                MacroParam("recipient", "address"),
            ),
        ),
        MacroSpec(
            name = "event",
            tailText = "(_)",
            typeText = "()",
            returnKind = MacroReturnKind.UNIT,
            params = listOf(
                MacroParam("_", "T"),
            ),
        ),
        MacroSpec(
            name = "table",
            tailText = "(_)",
            typeText = "table<K, V>",
            params = listOf(
                MacroParam("_", "T"),
            ),
        ),
        MacroSpec(
            name = "system",
            tailText = "(_)",
            typeText = "()",
            returnKind = MacroReturnKind.UNIT,
            params = listOf(
                MacroParam("_", "T"),
            ),
        ),
        MacroSpec(
            name = "vote",
            tailText = "(_)",
            typeText = "()",
            returnKind = MacroReturnKind.UNIT,
            params = listOf(
                MacroParam("_", "T"),
            ),
        ),
    )

    private val stdlibSpecs: List<MacroSpec> = listOf(
        MacroSpec(
            name = "all",
            tailText = "(v: &vector<T>, f: |&T| -> bool)",
            typeText = "bool",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("v", "&vector<T>"),
                MacroParam("f", "|&T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "and",
            tailText = "(o: Option<T>, f: |T| -> Option<U>)",
            typeText = "Option<U>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("o", "Option<T>"),
                MacroParam("f", "|T| -> Option<U>"),
            ),
        ),
        MacroSpec(
            name = "and_ref",
            tailText = "(o: &Option<T>, f: |&T| -> Option<U>)",
            typeText = "Option<U>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("o", "&Option<T>"),
                MacroParam("f", "|&T| -> Option<U>"),
            ),
        ),
        MacroSpec(
            name = "any",
            tailText = "(v: &vector<T>, f: |&T| -> bool)",
            typeText = "bool",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("v", "&vector<T>"),
                MacroParam("f", "|&T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "assert_eq",
            tailText = "(t1: T, t2: T)",
            typeText = "()",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("t1", "T"),
                MacroParam("t2", "T"),
            ),
        ),
        MacroSpec(
            name = "assert_ref_eq",
            tailText = "(t1: &T, t2: &T)",
            typeText = "()",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("t1", "&T"),
                MacroParam("t2", "&T"),
            ),
        ),
        MacroSpec(
            name = "count",
            tailText = "(v: &vector<T>, f: |&T| -> bool)",
            typeText = "u64",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("v", "&vector<T>"),
                MacroParam("f", "|&T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "destroy",
            tailText = "(o: Option<T>, f: |T| -> R)",
            typeText = "()",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("o", "Option<T>"),
                MacroParam("f", "|T| -> R"),
            ),
        ),
        MacroSpec(
            name = "destroy_or",
            tailText = "(o: Option<T>, default: T)",
            typeText = "T",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("o", "Option<T>"),
                MacroParam("default", "T"),
            ),
        ),
        MacroSpec(
            name = "do",
            tailText = "(stop: T, f: |T| -> R)",
            typeText = "()",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("stop", "T"),
                MacroParam("f", "|T| -> R"),
            ),
        ),
        MacroSpec(
            name = "do_eq",
            tailText = "(stop: T, f: |T| -> R)",
            typeText = "()",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("stop", "T"),
                MacroParam("f", "|T| -> R"),
            ),
        ),
        MacroSpec(
            name = "do_mut",
            tailText = "(o: &mut Option<T>, f: |&mut T| -> R)",
            typeText = "()",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("o", "&mut Option<T>"),
                MacroParam("f", "|&mut T| -> R"),
            ),
        ),
        MacroSpec(
            name = "do_ref",
            tailText = "(o: &Option<T>, f: |&T| -> R)",
            typeText = "()",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("o", "&Option<T>"),
                MacroParam("f", "|&T| -> R"),
            ),
        ),
        MacroSpec(
            name = "extract_or",
            tailText = "(o: &mut Option<T>, default: T)",
            typeText = "T",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("o", "&mut Option<T>"),
                MacroParam("default", "T"),
            ),
        ),
        MacroSpec(
            name = "filter",
            tailText = "(o: Option<T>, f: |&T| -> bool)",
            typeText = "Option<T>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("o", "Option<T>"),
                MacroParam("f", "|&T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "find_index",
            tailText = "(v: &vector<T>, f: |&T| -> bool)",
            typeText = "Option<u64>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("v", "&vector<T>"),
                MacroParam("f", "|&T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "find_indices",
            tailText = "(v: &vector<T>, f: |&T| -> bool)",
            typeText = "vector<u64>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("v", "&vector<T>"),
                MacroParam("f", "|&T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "fold",
            tailText = "(v: vector<T>, init: Acc, f: |Acc, T| -> Acc)",
            typeText = "Acc",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("v", "vector<T>"),
                MacroParam("init", "Acc"),
                MacroParam("f", "|Acc, T| -> Acc"),
            ),
        ),
        MacroSpec(
            name = "insertion_sort_by",
            tailText = "(v: &mut vector<T>, le: |&T, &T| -> bool)",
            typeText = "()",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("v", "&mut vector<T>"),
                MacroParam("le", "|&T, &T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "is_some_and",
            tailText = "(o: &Option<T>, f: |&T| -> bool)",
            typeText = "bool",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("o", "&Option<T>"),
                MacroParam("f", "|&T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "is_sorted_by",
            tailText = "(v: &vector<T>, le: |&T, &T| -> bool)",
            typeText = "bool",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("v", "&vector<T>"),
                MacroParam("le", "|&T, &T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "map",
            tailText = "(o: Option<T>, f: |T| -> U)",
            typeText = "Option<U>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("o", "Option<T>"),
                MacroParam("f", "|T| -> U"),
            ),
        ),
        MacroSpec(
            name = "map_ref",
            tailText = "(o: &Option<T>, f: |&T| -> U)",
            typeText = "Option<U>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("o", "&Option<T>"),
                MacroParam("f", "|&T| -> U"),
            ),
        ),
        MacroSpec(
            name = "max_value",
            tailText = "()",
            typeText = "u256",
            minArgs = 0,
            maxArgs = 0,
            params = emptyList(),
        ),
        MacroSpec(
            name = "merge_sort_by",
            tailText = "(v: &mut vector<T>, le: |&T, &T| -> bool)",
            typeText = "()",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("v", "&mut vector<T>"),
                MacroParam("le", "|&T, &T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "num_checked_add",
            tailText = "(x: T, y: T, max_t: T)",
            typeText = "Option<T>",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("y", "T"),
                MacroParam("max_t", "T"),
            ),
        ),
        MacroSpec(
            name = "num_checked_div",
            tailText = "(x: T, y: T)",
            typeText = "Option<T>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("y", "T"),
            ),
        ),
        MacroSpec(
            name = "num_checked_mul",
            tailText = "(x: T, y: T, max_t: T)",
            typeText = "Option<T>",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("y", "T"),
                MacroParam("max_t", "T"),
            ),
        ),
        MacroSpec(
            name = "num_checked_shl",
            tailText = "(x: T, shift: u8, bit_size: u8)",
            typeText = "Option<T>",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("shift", "u8"),
                MacroParam("bit_size", "u8"),
            ),
        ),
        MacroSpec(
            name = "num_checked_shr",
            tailText = "(x: T, shift: u8, bit_size: u8)",
            typeText = "Option<T>",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("shift", "u8"),
                MacroParam("bit_size", "u8"),
            ),
        ),
        MacroSpec(
            name = "num_checked_sub",
            tailText = "(x: T, y: T)",
            typeText = "Option<T>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("y", "T"),
            ),
        ),
        MacroSpec(
            name = "num_diff",
            tailText = "(x: T, y: T)",
            typeText = "T",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("y", "T"),
            ),
        ),
        MacroSpec(
            name = "num_divide_and_round_up",
            tailText = "(x: T, y: T)",
            typeText = "T",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("y", "T"),
            ),
        ),
        MacroSpec(
            name = "num_lossless_div",
            tailText = "(x: T, y: T)",
            typeText = "Option<T>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("y", "T"),
            ),
        ),
        MacroSpec(
            name = "num_lossless_shl",
            tailText = "(x: T, shift: u8, bit_size: u8)",
            typeText = "Option<T>",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("shift", "u8"),
                MacroParam("bit_size", "u8"),
            ),
        ),
        MacroSpec(
            name = "num_lossless_shr",
            tailText = "(x: T, shift: u8, bit_size: u8)",
            typeText = "Option<T>",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("shift", "u8"),
                MacroParam("bit_size", "u8"),
            ),
        ),
        MacroSpec(
            name = "num_max",
            tailText = "(x: T, y: T)",
            typeText = "T",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("y", "T"),
            ),
        ),
        MacroSpec(
            name = "num_min",
            tailText = "(x: T, y: T)",
            typeText = "T",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("y", "T"),
            ),
        ),
        MacroSpec(
            name = "num_pow",
            tailText = "(base: _, exponent: u8)",
            typeText = "_",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("base", "_"),
                MacroParam("exponent", "u8"),
            ),
        ),
        MacroSpec(
            name = "num_saturating_add",
            tailText = "(x: T, y: T, max_t: T)",
            typeText = "T",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("y", "T"),
                MacroParam("max_t", "T"),
            ),
        ),
        MacroSpec(
            name = "num_saturating_mul",
            tailText = "(x: T, y: T, max_t: T)",
            typeText = "T",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("y", "T"),
                MacroParam("max_t", "T"),
            ),
        ),
        MacroSpec(
            name = "num_saturating_sub",
            tailText = "(x: T, y: T)",
            typeText = "T",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("y", "T"),
            ),
        ),
        MacroSpec(
            name = "num_sqrt",
            tailText = "(x: T, bitsize: u8)",
            typeText = "T",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("x", "T"),
                MacroParam("bitsize", "u8"),
            ),
        ),
        MacroSpec(
            name = "num_to_string",
            tailText = "(x: _)",
            typeText = "String",
            minArgs = 1,
            maxArgs = 1,
            params = listOf(
                MacroParam("x", "_"),
            ),
        ),
        MacroSpec(
            name = "or",
            tailText = "(o: Option<T>, default: Option<T>)",
            typeText = "Option<T>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("o", "Option<T>"),
                MacroParam("default", "Option<T>"),
            ),
        ),
        MacroSpec(
            name = "partition",
            tailText = "(v: vector<T>, f: |&T| -> bool)",
            typeText = "(vector<T>, vector<T>)",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("v", "vector<T>"),
                MacroParam("f", "|&T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "range_do",
            tailText = "(start: T, stop: T, f: |T| -> R)",
            typeText = "()",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("start", "T"),
                MacroParam("stop", "T"),
                MacroParam("f", "|T| -> R"),
            ),
        ),
        MacroSpec(
            name = "range_do_eq",
            tailText = "(start: T, stop: T, f: |T| -> R)",
            typeText = "()",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("start", "T"),
                MacroParam("stop", "T"),
                MacroParam("f", "|T| -> R"),
            ),
        ),
        MacroSpec(
            name = "skip_while",
            tailText = "(v: vector<T>, p: |&T| -> bool)",
            typeText = "vector<T>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("v", "vector<T>"),
                MacroParam("p", "|&T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "tabulate",
            tailText = "(n: u64, f: |u64| -> T)",
            typeText = "vector<T>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("n", "u64"),
                MacroParam("f", "|u64| -> T"),
            ),
        ),
        MacroSpec(
            name = "take_while",
            tailText = "(v: vector<T>, p: |&T| -> bool)",
            typeText = "vector<T>",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("v", "vector<T>"),
                MacroParam("p", "|&T| -> bool"),
            ),
        ),
        MacroSpec(
            name = "try_as_u128",
            tailText = "(x: _)",
            typeText = "Option<u128>",
            minArgs = 1,
            maxArgs = 1,
            params = listOf(
                MacroParam("x", "_"),
            ),
        ),
        MacroSpec(
            name = "try_as_u16",
            tailText = "(x: _)",
            typeText = "Option<u16>",
            minArgs = 1,
            maxArgs = 1,
            params = listOf(
                MacroParam("x", "_"),
            ),
        ),
        MacroSpec(
            name = "try_as_u32",
            tailText = "(x: _)",
            typeText = "Option<u32>",
            minArgs = 1,
            maxArgs = 1,
            params = listOf(
                MacroParam("x", "_"),
            ),
        ),
        MacroSpec(
            name = "try_as_u64",
            tailText = "(x: _)",
            typeText = "Option<u64>",
            minArgs = 1,
            maxArgs = 1,
            params = listOf(
                MacroParam("x", "_"),
            ),
        ),
        MacroSpec(
            name = "try_as_u8",
            tailText = "(x: _)",
            typeText = "Option<u8>",
            minArgs = 1,
            maxArgs = 1,
            params = listOf(
                MacroParam("x", "_"),
            ),
        ),
        MacroSpec(
            name = "uq_add",
            tailText = "(a: T, b: T, max_t: T, abort_overflow: _)",
            typeText = "T",
            minArgs = 4,
            maxArgs = 4,
            params = listOf(
                MacroParam("a", "T"),
                MacroParam("b", "T"),
                MacroParam("max_t", "T"),
                MacroParam("abort_overflow", "_"),
            ),
        ),
        MacroSpec(
            name = "uq_from_int",
            tailText = "(integer: T, fractional_bits: u8)",
            typeText = "U",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("integer", "T"),
                MacroParam("fractional_bits", "u8"),
            ),
        ),
        MacroSpec(
            name = "uq_from_quotient",
            tailText = "(numerator: T, denominator: T, max_t: T, t_bits: u8, fractional_bits: u8, abort_denominator: _, abort_quotient_too_small: _, abort_quotient_too_large: _)",
            typeText = "T",
            minArgs = 8,
            maxArgs = 8,
            params = listOf(
                MacroParam("numerator", "T"),
                MacroParam("denominator", "T"),
                MacroParam("max_t", "T"),
                MacroParam("t_bits", "u8"),
                MacroParam("fractional_bits", "u8"),
                MacroParam("abort_denominator", "_"),
                MacroParam("abort_quotient_too_small", "_"),
                MacroParam("abort_quotient_too_large", "_"),
            ),
        ),
        MacroSpec(
            name = "uq_int_div",
            tailText = "(val: T, divisor: T, max_t: T, fractional_bits: u8, abort_division_by_zero: _, abort_overflow: _)",
            typeText = "T",
            minArgs = 6,
            maxArgs = 6,
            params = listOf(
                MacroParam("val", "T"),
                MacroParam("divisor", "T"),
                MacroParam("max_t", "T"),
                MacroParam("fractional_bits", "u8"),
                MacroParam("abort_division_by_zero", "_"),
                MacroParam("abort_overflow", "_"),
            ),
        ),
        MacroSpec(
            name = "uq_int_mul",
            tailText = "(val: T, multiplier: T, max_t: T, fractional_bits: u8, abort_overflow: _)",
            typeText = "T",
            minArgs = 5,
            maxArgs = 5,
            params = listOf(
                MacroParam("val", "T"),
                MacroParam("multiplier", "T"),
                MacroParam("max_t", "T"),
                MacroParam("fractional_bits", "u8"),
                MacroParam("abort_overflow", "_"),
            ),
        ),
        MacroSpec(
            name = "uq_sub",
            tailText = "(a: T, b: T, abort_overflow: _)",
            typeText = "T",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("a", "T"),
                MacroParam("b", "T"),
                MacroParam("abort_overflow", "_"),
            ),
        ),
        MacroSpec(
            name = "uq_to_int",
            tailText = "(a: U, fractional_bits: u8)",
            typeText = "T",
            minArgs = 2,
            maxArgs = 2,
            params = listOf(
                MacroParam("a", "U"),
                MacroParam("fractional_bits", "u8"),
            ),
        ),
        MacroSpec(
            name = "zip_do",
            tailText = "(v1: vector<T1>, v2: vector<T2>, f: |T1, T2| -> R)",
            typeText = "()",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("v1", "vector<T1>"),
                MacroParam("v2", "vector<T2>"),
                MacroParam("f", "|T1, T2| -> R"),
            ),
        ),
        MacroSpec(
            name = "zip_do_mut",
            tailText = "(v1: &mut vector<T1>, v2: &mut vector<T2>, f: |&mut T1, &mut T2| -> R)",
            typeText = "()",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("v1", "&mut vector<T1>"),
                MacroParam("v2", "&mut vector<T2>"),
                MacroParam("f", "|&mut T1, &mut T2| -> R"),
            ),
        ),
        MacroSpec(
            name = "zip_do_ref",
            tailText = "(v1: &vector<T1>, v2: &vector<T2>, f: |&T1, &T2| -> R)",
            typeText = "()",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("v1", "&vector<T1>"),
                MacroParam("v2", "&vector<T2>"),
                MacroParam("f", "|&T1, &T2| -> R"),
            ),
        ),
        MacroSpec(
            name = "zip_do_reverse",
            tailText = "(v1: vector<T1>, v2: vector<T2>, f: |T1, T2| -> R)",
            typeText = "()",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("v1", "vector<T1>"),
                MacroParam("v2", "vector<T2>"),
                MacroParam("f", "|T1, T2| -> R"),
            ),
        ),
        MacroSpec(
            name = "zip_map",
            tailText = "(v1: vector<T1>, v2: vector<T2>, f: |T1, T2| -> U)",
            typeText = "vector<U>",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("v1", "vector<T1>"),
                MacroParam("v2", "vector<T2>"),
                MacroParam("f", "|T1, T2| -> U"),
            ),
        ),
        MacroSpec(
            name = "zip_map_ref",
            tailText = "(v1: &vector<T1>, v2: &vector<T2>, f: |&T1, &T2| -> U)",
            typeText = "vector<U>",
            minArgs = 3,
            maxArgs = 3,
            params = listOf(
                MacroParam("v1", "&vector<T1>"),
                MacroParam("v2", "&vector<T2>"),
                MacroParam("f", "|&T1, &T2| -> U"),
            ),
        ),
    )

    fun builtinSpecOf(name: String): MacroSpec? = builtinSpecs.firstOrNull { it.name == name }

    fun stdlibSpecOf(name: String): MacroSpec? = stdlibSpecs.firstOrNull { it.name == name }

    fun customSpecs(): List<MacroSpec> =
        MvMacroSpecProvider.EP_NAME.extensionList
            .flatMap { it.macroSpecs() }

    fun customSpecOf(name: String): MacroSpec? = customSpecs().firstOrNull { it.name == name }

    fun isBuiltin(name: String): Boolean = builtinSpecOf(name) != null

    fun isCustom(name: String): Boolean = customSpecOf(name) != null

    fun specOf(name: String): MacroSpec? =
        customSpecOf(name) ?: builtinSpecOf(name) ?: stdlibSpecOf(name)

    fun completionSpecs(): List<MacroSpec> {
        val merged = LinkedHashMap<String, MacroSpec>()
        for (spec in builtinSpecs) {
            merged.putIfAbsent(spec.name, spec)
        }
        for (spec in stdlibSpecs) {
            merged.putIfAbsent(spec.name, spec)
        }
        for (spec in customSpecs()) {
            merged[spec.name] = spec
        }
        return merged.values.toList()
    }
}
