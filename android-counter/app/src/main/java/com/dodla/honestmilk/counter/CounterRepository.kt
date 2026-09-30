package com.dodla.honestmilk.counter

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

@Serializable data class ProductRow(val id:String,val name:String,val active:Boolean=true)
@Serializable data class VariantRow(val id:String,val product_id:String,val variant_name:String,val unit_volume_ml:Int,val price:Double,val active:Boolean=true)
@Serializable data class EmployeeRow(val id:String,val employee_code:String?=null,val guest_code:String?=null,val id_type:String,val name:String,val department:String?=null,val phone:String?=null,val active:Boolean=true)
@Serializable data class ProfileRow(val id:String,val display_name:String,val role:String,val active:Boolean)
@Serializable data class TransactionInsert(val employee_id:String,val product_variant_id:String,val quantity:Int,val unit_price:Double,val operator_id:String)
@Serializable data class TransactionRow(val quantity:Int,val transaction_at:String)

class CounterRepository {
    private val supabase=SupabaseClientProvider.client

    suspend fun sessionExists():Boolean = supabase.auth.currentUserOrNull()!=null

    suspend fun signIn(email:String,password:String) {
        supabase.auth.signInWith(Email) { this.email=email; this.password=password }
        val userId=supabase.auth.currentUserOrNull()?.id ?: error("Login failed.")
        val profile=supabase.from("profiles").select { filter { eq("id",userId) } }.decodeSingle<ProfileRow>()
        if(!profile.active || (profile.role!="ADMIN" && profile.role!="OPERATOR")) {
            supabase.auth.signOut()
            error("Account is not active as Admin or Operator.")
        }
    }

    suspend fun signOut(){supabase.auth.signOut()}

    suspend fun products():List<ProductRow> =
        supabase.from("products").select { filter { eq("active",true) }; order(column = "name", order = Order.ASCENDING) }.decodeList()

    suspend fun variants():List<VariantRow> =
        supabase.from("product_variants").select { filter { eq("active",true) }; order(column = "variant_name", order = Order.ASCENDING) }.decodeList()

    suspend fun employees(search:String=""):List<EmployeeRow> =
        supabase.from("employees").select {
            filter {
                eq("active",true)
                if(search.isNotBlank()) {
                    or {
                        ilike("name","%$search%")
                        ilike("employee_code","%$search%")
                        ilike("guest_code","%$search%")
                    }
                }
            }
            order(column = "name", order = Order.ASCENDING)
            limit(10)
        }.decodeList()

    suspend fun totals(employeeId:String,variantId:String):Pair<Double,Double> {
        val zone=ZoneId.systemDefault()
        val now=Instant.now()
        val today=now.atZone(zone).toLocalDate()
        val monthStart=today.withDayOfMonth(1).atStartOfDay(zone).toInstant()
        val rows=supabase.from("transactions").select {
            filter {
                eq("employee_id",employeeId)
                eq("product_variant_id",variantId)
                gte("transaction_at",monthStart.toString())
                lte("transaction_at",now.toString())
            }
        }.decodeList<TransactionRow>()
        val todayQty=rows.filter {
            Instant.parse(it.transaction_at).atZone(zone).toLocalDate()==today
        }.sumOf { it.quantity }
        val monthQty=rows.sumOf { it.quantity }
        return todayQty.toDouble() to monthQty.toDouble()
    }

    suspend fun save(employeeId:String,variantId:String,quantity:Int,unitPrice:Double) {
        val operatorId=supabase.auth.currentUserOrNull()?.id ?: error("Operator session expired.")
        supabase.from("transactions").insert(
            TransactionInsert(employeeId,variantId,quantity,unitPrice,operatorId)
        )
    }
}
