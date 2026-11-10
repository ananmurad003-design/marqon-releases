package com.tharaa.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun txns(): Flow<List<Txn>>

    @Query("SELECT * FROM incomes ORDER BY timestamp DESC, id DESC")
    fun incomes(): Flow<List<Income>>

    @Query("SELECT * FROM debts ORDER BY createdAt, id")
    fun debts(): Flow<List<Debt>>

    @Query("SELECT * FROM goals ORDER BY priority, id")
    fun goals(): Flow<List<Goal>>

    @Query("SELECT * FROM holdings ORDER BY id")
    fun holdings(): Flow<List<Holding>>

    @Query("SELECT * FROM settings")
    fun settings(): Flow<List<Setting>>

    // لقطات فورية (للنسخ الاحتياطي والعمال بالخلفية)
    @Query("SELECT * FROM transactions") suspend fun allTxns(): List<Txn>
    @Query("SELECT * FROM incomes") suspend fun allIncomes(): List<Income>
    @Query("SELECT * FROM debts") suspend fun allDebts(): List<Debt>
    @Query("SELECT * FROM goals") suspend fun allGoals(): List<Goal>
    @Query("SELECT * FROM holdings") suspend fun allHoldings(): List<Holding>
    @Query("SELECT * FROM settings") suspend fun allSettings(): List<Setting>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE fund = :fund")
    suspend fun balance(fund: String): Double

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'EXPENSE' AND timestamp >= :from")
    suspend fun expenseCountSince(from: Long): Int

    @Insert suspend fun insertTxn(t: Txn): Long
    @Insert suspend fun insertTxns(t: List<Txn>)
    @Insert suspend fun insertIncome(i: Income): Long
    @Insert suspend fun insertIncomes(i: List<Income>)
    @Insert suspend fun insertDebt(d: Debt): Long
    @Insert suspend fun insertDebts(d: List<Debt>)
    @Update suspend fun updateDebt(d: Debt)
    @Insert suspend fun insertGoal(g: Goal): Long
    @Insert suspend fun insertGoals(g: List<Goal>)
    @Update suspend fun updateGoal(g: Goal)
    @Insert suspend fun insertHolding(h: Holding): Long
    @Insert suspend fun insertHoldings(h: List<Holding>)
    @Update suspend fun updateHolding(h: Holding)
    @Upsert suspend fun putSetting(s: Setting)
    @Insert suspend fun insertSettings(s: List<Setting>)

    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTxn(id: Long)
    @Query("DELETE FROM transactions WHERE incomeId = :incomeId") suspend fun deleteTxnsOfIncome(incomeId: Long)
    @Query("DELETE FROM incomes WHERE id = :id") suspend fun deleteIncome(id: Long)
    @Query("DELETE FROM debts WHERE id = :id") suspend fun deleteDebt(id: Long)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoal(id: Long)
    @Query("UPDATE transactions SET goalId = NULL WHERE goalId = :id") suspend fun detachGoal(id: Long)
    @Query("DELETE FROM holdings WHERE id = :id") suspend fun deleteHolding(id: Long)

    @Query("DELETE FROM transactions") suspend fun clearTxns()
    @Query("DELETE FROM incomes") suspend fun clearIncomes()
    @Query("DELETE FROM debts") suspend fun clearDebts()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM holdings") suspend fun clearHoldings()
    @Query("DELETE FROM settings") suspend fun clearSettings()
}

@Database(
    entities = [Txn::class, Income::class, Debt::class, Goal::class, Holding::class, Setting::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tharaa.db").build()
    }
}
