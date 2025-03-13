package com.example.billvision

enum class BillLabel(val label: String) {
    ONE_DOLLAR("One Dollar"),
    FIFTY_DOLLAR("Fifty Dollar"),
    TEN_DOLLAR("Ten Dollar"),
    TWO_DOLLAR("Two Dollar"),
    TWENTY_DOLLAR("Twenty Dollar"),
    FIVE_DOLLAR("Five Dollar"),
    HUNDRED_DOLLAR("Hundred Dollar"),
    UNKNOWN("Unknown");

    companion object {
        fun fromIndex(index: Int): BillLabel {
            return entries.getOrNull(index) ?: UNKNOWN
        }
    }
}