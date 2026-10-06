package com.dodla.honestmilk.counter

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

@Serializable data class ProductRow(val id:String,val name:String,val active:Boolean=true)
@Serializable data class VariantRow(val id:String,val product_id:String,val variant_name:String,val unit_volume_ml:Int,val price:Double,val active:Boolean=true)
@Serializable data class EmployeeRow(val id:String,val employee_code:String?=null,val guest_code:String?=null,val id_type:String,val name:String,val department:String?=null,val phone:String?=null,val active:Boolean=true)
@Serializable data class ProfileRow(val id:String,val display_name:String,val role:String,val active:Boolean)
@Serializable data class TransactionInsert(val employee_id:String,val product_variant_id:String,val quantity:Int,val unit_price:Double,val operator_id:String)
@Serializable data class TransactionHistoryRow(val quantity:Int,val unit_price:Double)
@Serializable data class SavedTransactionRow(val id:String,val transaction_at:String)

class CounterRepository{
 private val supabase=SupabaseClientProvider.client
 suspend fun sessionExists():Boolean=supabase.auth.currentUserOrNull()!=null
 suspend fun signIn(email:String,password:String){supabase.auth.signInWith(Email){this.email=email;this.password=password};val userId=supabase.auth.currentUserOrNull()?.id?:error("Login failed.");val profile=supabase.from("profiles").select{filter{eq("id",userId)}}.decodeSingle<ProfileRow>();if(!profile.active||(profile.role!="ADMIN"&&profile.role!="OPERATOR")){supabase.auth.signOut();error("Account is not active as Admin or Operator.")}}
 suspend fun signOut(){supabase.auth.signOut()}
 suspend fun products():List<ProductRow>=supabase.from("products").select{filter{eq("active",true)};order(column="name",order=Order.ASCENDING)}.decodeList()
 suspend fun variants():List<VariantRow>=supabase.from("product_variants").select{filter{eq("active",true)};order(column="variant_name",order=Order.ASCENDING)}.decodeList()
 suspend fun employees(search:String=""):List<EmployeeRow>=supabase.from("employees").select{filter{eq("active",true);if(search.isNotBlank()){or{ilike("name","%$search%");ilike("employee_code","%$search%");ilike("guest_code","%$search%");ilike("phone","%$search%")}}};order(column="name",order=Order.ASCENDING);limit(15)}.decodeList()
 suspend fun summary(employeeId:String):EmployeeSummary{val rows=supabase.from("transactions").select{filter{eq("employee_id",employeeId)}}.decodeList<TransactionHistoryRow>();return EmployeeSummary(rows.size,rows.sumOf{it.quantity},rows.sumOf{it.quantity*it.unit_price})}
 suspend fun save(employeeId:String,variantId:String,quantity:Int,unitPrice:Double):SavedTransactionRow{val operatorId=supabase.auth.currentUserOrNull()?.id?:error("Operator session expired.");return supabase.from("transactions").insert(TransactionInsert(employeeId,variantId,quantity,unitPrice,operatorId)){select()}.decodeSingle()}
}
