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
data class TransactionLine(val variantId:String,val quantity:Int,val unitPrice:Double)
data class SavedTransactionBatch(val rows:List<SavedTransactionRow>)

class CounterRepository{
 private val supabase=SupabaseClientProvider.client
 suspend fun sessionExists():Boolean=supabase.auth.currentUserOrNull()!=null
 suspend fun signIn(email:String,password:String){supabase.auth.signInWith(Email){this.email=email;this.password=password};val userId=supabase.auth.currentUserOrNull()?.id?:error("Login failed.");val profile=supabase.from("profiles").select{filter{eq("id",userId)}}.decodeSingle<ProfileRow>();if(!profile.active||(profile.role!="ADMIN"&&profile.role!="OPERATOR")){supabase.auth.signOut();error("Account is not active as Admin or Operator.")}}
 suspend fun signOut(){supabase.auth.signOut()}
 suspend fun products(): List<ProductRow> =supabase.from("products").select{filter{eq("active",true)};order(column="name",order=Order.ASCENDING)}.decodeList()
 suspend fun variants(): List<VariantRow> =supabase.from("product_variants").select{filter{eq("active",true)};order(column="variant_name",order=Order.ASCENDING)}.decodeList()
 suspend fun employees(search:String=""):List<EmployeeRow>{
  val rows=supabase.from("employees").select{filter{eq("active",true)};order(column="name",order=Order.ASCENDING)}.decodeList<EmployeeRow>()
  if(search.isBlank()) return rows.take(15)
  val q=search.trim().lowercase()
  fun score(r:EmployeeRow):Int{
   val code=r.employee_code?.lowercase().orEmpty()
   val guest=r.guest_code?.lowercase().orEmpty()
   val name=r.name.lowercase()
   val phone=r.phone?.lowercase().orEmpty()
   return when{
    code==q||guest==q||phone==q->0
    code.startsWith(q)||guest.startsWith(q)||phone.startsWith(q)->1
    name.startsWith(q)->2
    name.split(" ").any{it.startsWith(q)}->3
    name.contains(q)||code.contains(q)||guest.contains(q)||phone.contains(q)->4
    else->99
   }
  }
  return rows.map{it to score(it)}.filter{it.second<99}.sortedWith(compareBy<Pair<EmployeeRow,Int>>{it.second}.thenBy{it.first.name}).take(15).map{it.first}
 }
 suspend fun summary(employeeId:String):EmployeeSummary{val rows=supabase.from("transactions").select{filter{eq("employee_id",employeeId)}}.decodeList<TransactionHistoryRow>();return EmployeeSummary(rows.size,rows.sumOf{it.quantity},rows.sumOf{it.quantity*it.unit_price})}
 suspend fun save(employeeId:String,lines:List<TransactionLine>):List<SavedTransactionRow>{
  require(lines.isNotEmpty()){"Add at least one product."}
  val operatorId=supabase.auth.currentUserOrNull()?.id?:error("Operator session expired.")
  val inserts=lines.map{TransactionInsert(employeeId,it.variantId,it.quantity,it.unitPrice,operatorId)}
  return supabase.from("transactions").insert(inserts){select()}.decodeList()
 }
}
