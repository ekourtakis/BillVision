package com.example.billvision

enum class BillLabel(val label: String) {
    HUNDRED_DOLLAR("Hundred Dollar"),
    TEN_DOLLAR("Ten Dollar"),
    ONE_DOLLAR("One Dollar"),
    TWENTY_DOLLAR("Twenty Dollar"),
    TWO_DOLLAR("Two Dollar"),
    FIFTY_DOLLAR("Fifty Dollar"),
    FIVE_DOLLAR("Five Dollar"),
    UNKNOWN("Unknown");

    companion object {
        fun fromIndex(index: Int): BillLabel {
            return entries.getOrNull(index) ?: UNKNOWN
        }
    }
}