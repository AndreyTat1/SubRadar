package com.subradar.app.domain

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface RadarRepository {
    val snapshots: Flow<Snapshot>
    suspend fun snapshot(): Snapshot
    suspend fun save(subscription: Subscription, today: LocalDate)
    suspend fun delete(id: String)
    suspend fun archive(id: String)
    suspend fun addCategory(name: String): Category
    suspend fun markUsed(id: String, date: LocalDate)
    suspend fun confirm(subscriptionId: String, date: LocalDate, today: LocalDate)
    suspend fun removePayment(id: String)
    suspend fun replace(snapshot: Snapshot)
    suspend fun clear()
}
