package com.dodla.honestmilk.counter

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable data class ProductRow(val id:String,val name:String,val active:Boolean=true)
@Serializable data class VariantRow(val id:String,val product_id:String,val variant_name:String,val unit_volume_ml:Int,val price:Double,val active:Boolean=true)
@Serializable data class EmployeeRow(val id:String,val employee_code:String?=null,val guest_code:String?=null,val id_type:String,val name:String,val department:String?=null,val phone:String?=null,val active:Boolean=true)
@Serializable data class ProfileRow(val id:String,val display_name:String,val role:String,val active:Boolean)
@Serializable data class TransactionInsert(val id:String,val employee_id:String,val product_variant_id:String,val quantity:Int,val unit_price:Double,val operator_id:String,val transaction_at:String?=null)
@Serializable data class TransactionHistoryRow(val quantity:Int,val unit_price:Double)
@Serializable data class SavedTransactionRow(val id:String,val transaction_at:String)
@Serializable data class TransactionLine(val variantId:String,val quantity:Int,val unitPrice:Double)
@Serializable data class SavedTransactionBatch(val rows:List<SavedTransactionRow>)
@Serializable data class EmployeeSummary(val transactionCount:Int,val quantity:Int,val value:Double)

class CounterRepository(context: Context){
 private val supabase=SupabaseClientProvider.client
 private val offline=OfflineStore(context)
 private val appContext=context.applicationContext
 fun isOnline():Boolean {
  val cm=appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  val network=cm.activeNetwork ?: return false
  val caps=cm.getNetworkCapabilities(network) ?: return false
  return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
 }

 suspend fun sessionExists():Boolean=supabase.auth.currentUserOrNull()!=null
 suspend fun signIn(email:String,password:String){
  supabase.auth.signInWith(Email){this.email=email;this.password=password}
  val userId=supabase.auth.currentUserOrNull()?.id?:error("Login failed.")
  val profile=supabase.from("profiles").select{filter{eq("id",userId)}}.decodeSingle<ProfileRow>()
  if(!profile.active||(profile.role!="ADMIN"&&profile.role!="OPERATOR")){supabase.auth.signOut();error("Account is not active as Admin or Operator.")}
 }
 suspend fun signOut(){supabase.auth.signOut()}

 suspend fun products(): List<ProductRow> {
  return runCatching{
   val rows=supabase.from("products").select{filter{eq("active",true)};order(column="name",order=Order.ASCENDING)}.decodeList<ProductRow>()
   val variants=supabase.from("product_variants").select{filter{eq("active",true)};order(column="variant_name",order=Order.ASCENDING)}.decodeList<VariantRow>()
   offline.saveProducts(rows,variants)
   rows
  }.getOrElse{
   val cached=offline.loadProducts()
   if(cached.isEmpty()) throw it
   cached
  }
 }

 suspend fun variants(): List<VariantRow> {
  return runCatching{
   val rows=supabase.from("product_variants").select{filter{eq("active",true)};order(column="variant_name",order=Order.ASCENDING)}.decodeList<VariantRow>()
   val products=supabase.from("products").select{filter{eq("active",true)}}.decodeList<ProductRow>()
   offline.saveProducts(products,rows)
   rows
  }.getOrElse{
   val cached=offline.loadVariants()
   if(cached.isEmpty()) throw it
   cached
  }
 }

 suspend fun employees(search:String=""):List<EmployeeRow>{
  val rows=runCatching{
   val fresh=supabase.from("employees").select{filter{eq("active",true)};order(column="name",order=Order.ASCENDING)}.decodeList<EmployeeRow>()
   offline.saveEmployees(fresh)
   fresh
  }.getOrElse{
   val cached=offline.loadEmployees()
   if(cached.isEmpty()) throw it
   cached
  }
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

 suspend fun summary(employeeId:String):EmployeeSummary{
  return runCatching{
   val rows=supabase.from("transactions").select{filter{eq("employee_id",employeeId)}}.decodeList<TransactionHistoryRow>()
   EmployeeSummary(rows.size,rows.sumOf{it.quantity},rows.sumOf{it.quantity*it.unit_price}).also{offline.saveSummary(employeeId,it)}
  }.getOrElse{
   offline.loadSummary(employeeId) ?: throw it
  }
 }

 suspend fun save(employeeId:String,lines:List<TransactionLine>):List<SavedTransactionRow>{
  require(lines.isNotEmpty()){"Add at least one product."}
  val operatorId=supabase.auth.currentUserOrNull()?.id?:error("Operator session expired.")
  val now=java.time.Instant.now().toString()
  val inserts=lines.map{
   TransactionInsert(UUID.randomUUID().toString(),employeeId,it.variantId,it.quantity,it.unitPrice,operatorId,now)
  }
  if(isOnline()){
   return supabase.from("transactions").insert(inserts){select()}.decodeList()
  }
  offline.addPending(inserts.map{PendingTransaction(it.id,it.employee_id,it.product_variant_id,it.quantity,it.unit_price,it.operator_id,it.transaction_at?:now)})
  return inserts.map{SavedTransactionRow(it.id,now)}

 }

 suspend fun syncPending():Int{
  val pending=offline.pendingTransactions()
  if(pending.isEmpty()||supabase.auth.currentUserOrNull()==null) return 0
  val successful=mutableSetOf<String>()
  pending.groupBy{it.operatorId}.values.flatten().forEach{p->
   try{
    supabase.from("transactions").insert(
     TransactionInsert(p.id,p.employeeId,p.productVariantId,p.quantity,p.unitPrice,p.operatorId,p.transactionAt)
    )
    successful.add(p.id)
   }catch(_:Exception){}
  }
  offline.removePending(successful)
  return successful.size
 }

 fun pendingCount():Int=offline.pendingCount()
}
