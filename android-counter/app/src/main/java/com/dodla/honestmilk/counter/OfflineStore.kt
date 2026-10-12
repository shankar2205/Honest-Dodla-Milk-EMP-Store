package com.dodla.honestmilk.counter

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PendingTransaction(
    val id: String,
    val employeeId: String,
    val productVariantId: String,
    val quantity: Int,
    val unitPrice: Double,
    val operatorId: String,
    val transactionAt: String
)

class OfflineStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("honest_milk_offline", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val pendingLock = Any()

    fun saveProducts(rows: List<ProductRow>, variants: List<VariantRow>) {
        prefs.edit()
            .putString("products", json.encodeToString(rows))
            .putString("variants", json.encodeToString(variants))
            .apply()
    }

    fun loadProducts(): List<ProductRow> =
        prefs.getString("products", null)?.let { runCatching { json.decodeFromString<List<ProductRow>>(it) }.getOrNull() } ?: emptyList()

    fun loadVariants(): List<VariantRow> =
        prefs.getString("variants", null)?.let { runCatching { json.decodeFromString<List<VariantRow>>(it) }.getOrNull() } ?: emptyList()

    fun saveEmployees(rows: List<EmployeeRow>) {
        prefs.edit().putString("employees", json.encodeToString(rows)).apply()
    }

    fun loadEmployees(): List<EmployeeRow> =
        prefs.getString("employees", null)?.let { runCatching { json.decodeFromString<List<EmployeeRow>>(it) }.getOrNull() } ?: emptyList()

    fun saveSummary(employeeId: String, summary: EmployeeSummary) {
        prefs.edit().putString("summary_$employeeId", json.encodeToString(summary)).apply()
    }

    fun loadSummary(employeeId: String): EmployeeSummary? =
        prefs.getString("summary_$employeeId", null)?.let { runCatching { json.decodeFromString<EmployeeSummary>(it) }.getOrNull() }

    fun pendingTransactions(): List<PendingTransaction> = synchronized(pendingLock) {
        prefs.getString("pending_transactions", null)?.let {
            runCatching { json.decodeFromString<List<PendingTransaction>>(it) }.getOrNull()
        } ?: emptyList()
    }

    fun addPending(transactions: List<PendingTransaction>) = synchronized(pendingLock) {
        val merged = (pendingTransactions() + transactions).distinctBy { it.id }
        check(prefs.edit().putString("pending_transactions", json.encodeToString(merged)).commit()) {
            "Could not persist pending transactions locally."
        }
    }

    fun removePending(ids: Set<String>) = synchronized(pendingLock) {
        val remaining = pendingTransactions().filterNot { ids.contains(it.id) }
        check(prefs.edit().putString("pending_transactions", json.encodeToString(remaining)).commit()) {
            "Could not update pending transactions locally."
        }
    }

    fun hasPending(id: String): Boolean = synchronized(pendingLock) {
        pendingTransactions().any { it.id == id }
    }

    fun pendingCount(): Int = synchronized(pendingLock) {
        pendingTransactions().size
    }
}
