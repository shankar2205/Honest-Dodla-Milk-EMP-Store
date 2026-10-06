package com.dodla.honestmilk.counter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class Product(val id:String,val name:String,val variant:String,val price:Double,val volumeMl:Int)
data class Employee(val id:String,val name:String,val department:String,val identifier:String,val type:String)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{CounterApp{shareReceipt(it)}}}
 private fun shareReceipt(text:String){startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Share digital receipt"))}
}

@Composable fun CounterApp(onShare:(String)->Unit){
 val repo=remember{CounterRepository()}; val scope=rememberCoroutineScope()
 var loggedIn by remember{mutableStateOf(false)}; var checking by remember{mutableStateOf(true)}; var loginError by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(Unit){loggedIn=runCatching{repo.sessionExists()}.getOrDefault(false);checking=false}
 if(checking) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
 else if(!loggedIn) Column(Modifier.fillMaxSize()){LoginScreen{email,password->scope.launch{checking=true;loginError=null;runCatching{repo.signIn(email,password)}.onSuccess{loggedIn=true}.onFailure{loginError=it.message?:"Login failed."};checking=false}};loginError?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(24.dp))}}
 else CounterFlow(repo,scope,onShare){scope.launch{repo.signOut();loggedIn=false}}
}

@Composable fun LoginScreen(onLogin:(String,String)->Unit){
 var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally){
  Spacer(Modifier.height(60.dp));Text("HONEST MILK",style=MaterialTheme.typography.headlineLarge);Text("Counter Login",style=MaterialTheme.typography.titleLarge)
  OutlinedTextField(email,{email=it},label={Text("Email")},singleLine=true,modifier=Modifier.fillMaxWidth())
  OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
  Button(onClick={onLogin(email.trim(),password)},enabled=email.isNotBlank()&&password.isNotBlank(),modifier=Modifier.fillMaxWidth().height(56.dp)){Text("LOGIN")}
 }
}

@Composable fun CounterFlow(repo:CounterRepository,scope:CoroutineScope,onShare:(String)->Unit,onLogout:()->Unit){
 var step by remember{mutableIntStateOf(0)}
 var products by remember{mutableStateOf<List<Product>>(emptyList())}
 var employees by remember{mutableStateOf<List<Employee>>(emptyList())}
 var cart by remember{mutableStateOf<List<CartLine>>(emptyList())}
 var selectedProduct by remember{mutableStateOf<Product?>(null)}
 var selectedEmployee by remember{mutableStateOf<Employee?>(null)}
 var quantity by remember{mutableIntStateOf(1)}
 var search by remember{mutableStateOf("")}
 var error by remember{mutableStateOf<String?>(null)}
 var loading by remember{mutableStateOf(true)}
 var saving by remember{mutableStateOf(false)}
 var summary by remember{mutableStateOf<EmployeeSummary?>(null)}
 var receipt by remember{mutableStateOf<SavedReceipt?>(null)}

 fun loadEmployees(q:String){scope.launch{runCatching{repo.employees(q)}.onSuccess{employees=it.map{r->Employee(r.id,r.name,r.department?:"",r.employee_code?:r.guest_code?:"GUEST",if(r.employee_code!=null)"EMPLOYEE" else "GUEST")}}.onFailure{error=it.message?:"Unable to load employees."}}}
 fun addToCart(){
  val p=selectedProduct?:return
  val existing=cart.firstOrNull{it.product.id==p.id}
  cart=if(existing==null) cart+CartLine(p,quantity) else cart.map{if(it.product.id==p.id)it.copy(quantity=it.quantity+quantity)else it}
  selectedProduct=null;quantity=1
 }
 fun cartTotal()=cart.sumOf{it.product.price*it.quantity}

 LaunchedEffect(Unit){runCatching{val ps=repo.products();val vs=repo.variants();products=vs.mapNotNull{v->ps.find{it.id==v.product_id}?.let{p->Product(v.id,p.name,v.variant_name,p.price,v.unit_volume_ml)}}}.onFailure{error=it.message?:"Unable to load counter data."};loading=false}
 LaunchedEffect(search,step){if(step==1)loadEmployees(search)}

 Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("HONEST MILK",style=MaterialTheme.typography.titleLarge);TextButton(onClick=onLogout){Text("LOG OUT")}}
  Text(when(step){0->"TAKE PRODUCTS";1->"SELECT EMPLOYEE";2->"CONFIRM";else->"DIGITAL RECEIPT"},style=MaterialTheme.typography.headlineMedium)
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  when(step){
   0->{
    if(loading)CircularProgressIndicator()
    products.forEach{p->
     OutlinedButton(onClick={selectedProduct=p;quantity=1;error=null},modifier=Modifier.fillMaxWidth().height(72.dp)){
      Column(horizontalAlignment=Alignment.CenterHorizontally){Text(p.name+" — "+p.variant,style=MaterialTheme.typography.titleMedium);Text("₹"+String.format("%.2f",p.price))}
     }
    }
    selectedProduct?.let{p->
     Text("Add: "+p.name+" — "+p.variant,style=MaterialTheme.typography.titleMedium)
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){Button(onClick={if(quantity>1)quantity--}){Text("−")};Text("  "+quantity+"  ",style=MaterialTheme.typography.titleLarge);Button(onClick={quantity++}){Text("+")}}
     Button(onClick={addToCart},modifier=Modifier.fillMaxWidth().height(52.dp)){Text("ADD TO CART")}
    }
    if(cart.isNotEmpty()){
     HorizontalDivider()
     Text("CURRENT CART",style=MaterialTheme.typography.labelLarge)
     cart.forEachIndexed{index,line->
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
       Column(Modifier.weight(1f)){Text(line.product.name+" — "+line.product.variant);Text("₹"+String.format("%.2f",line.product.price)+" × "+line.quantity)}
       Text("₹"+String.format("%.2f",line.product.price*line.quantity))
       TextButton(onClick={cart=cart.filterIndexed{i,_->i!=index}}){Text("REMOVE")}
      }
     }
     Text("Cart total ₹"+String.format("%.2f",cartTotal()),style=MaterialTheme.typography.titleLarge)
     Button(onClick={step=1},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("NEXT")}
    }
   }
   1->{
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(search,{search=it},label={Text("Employee code, name or mobile")},singleLine=true,modifier=Modifier.weight(1f));if(search.isNotBlank())TextButton(onClick={search=""}){Text("CLEAR")}}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.weight(1f,false)){
     items(employees){e->OutlinedButton(onClick={selectedEmployee=e;error=null;step=2;scope.launch{runCatching{repo.summary(e.id)}.onSuccess{summary=it}.onFailure{error=it.message?:"Unable to load employee summary."}}},modifier=Modifier.fillMaxWidth()){
      Column(Modifier.fillMaxWidth().padding(4.dp)){Text(e.name,style=MaterialTheme.typography.titleMedium);Text(e.identifier+" · "+e.department);Text(e.type,style=MaterialTheme.typography.labelSmall)}
     }}
    }
    TextButton(onClick={step=0}){Text("BACK")}
   }
   2->{
    val e=selectedEmployee!!;val s=summary
    Text("EMPLOYEE",style=MaterialTheme.typography.labelLarge);Text(e.name,style=MaterialTheme.typography.titleLarge);Text(e.identifier+" · "+e.department);Text(e.type,style=MaterialTheme.typography.labelMedium)
    HorizontalDivider();Text("CURRENT TRANSACTION",style=MaterialTheme.typography.labelLarge)
    cart.forEach{line->Text(line.product.name+" — "+line.product.variant+" × "+line.quantity);Text("₹"+String.format("%.2f",line.product.price)+" each · ₹"+String.format("%.2f",line.product.price*line.quantity))}
    Text("Transaction total ₹"+String.format("%.2f",cartTotal()),style=MaterialTheme.typography.titleLarge)
    HorizontalDivider();Text("CONSUMPTION TILL TODAY",style=MaterialTheme.typography.labelLarge)
    Text("Previous transactions: "+(s?.transactionCount?:0));Text("Previous quantity: "+(s?.quantity?:0));Text("Previous bill value: ₹"+String.format("%.2f",s?.value?:0.0))
    Text("After this: "+((s?.quantity?:0)+cart.sumOf{it.quantity})+" units · ₹"+String.format("%.2f",(s?.value?:0.0)+cartTotal()))
    Button(enabled=!saving,onClick={
     saving=true;error=null
     scope.launch{
      runCatching{repo.save(e.id,cart.map{TransactionLine(it.product.id,it.quantity,it.product.price)})}
       .onSuccess{saved->receipt=SavedReceipt(saved.map{it.id},e,cart,cartTotal(),s?.quantity?:0,s?.value?:0.0,saved.firstOrNull()?.transaction_at?:"");step=3}
       .onFailure{error=it.message?:"Unable to save transaction."}
      saving=false
     }
    },modifier=Modifier.fillMaxWidth().height(60.dp)){Text(if(saving)"SAVING..." else "CONFIRM & FINISH")}
    TextButton(onClick={step=0},enabled=!saving){Text("BACK")}
   }
   else->{
    val r=receipt!!
    Card(Modifier.fillMaxWidth()){
     Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
      Text("✓ TRANSACTION COMPLETE",style=MaterialTheme.typography.headlineSmall);Text("HONEST MILK",style=MaterialTheme.typography.titleLarge)
      Text("Receipt: "+r.ids.first().take(8).uppercase());Text("Employee: "+r.employee.name);Text("Employee ID: "+r.employee.identifier)
      r.lines.forEach{Text(it.product.name+" — "+it.product.variant+" × "+it.quantity+" = ₹"+String.format("%.2f",it.product.price*it.quantity))}
      HorizontalDivider();Text("Total bill: ₹"+String.format("%.2f",r.total),style=MaterialTheme.typography.titleMedium)
      Text("Consumption till today");Text("Quantity: "+(r.previousQuantity+r.lines.sumOf{it.quantity}));Text("Bill value: ₹"+String.format("%.2f",r.previousValue+r.total))
     }
    }
    Button(onClick={onShare(buildReceiptText(r))},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("SHARE DIGITAL RECEIPT")}
    Button(onClick={cart=emptyList();selectedProduct=null;selectedEmployee=null;quantity=1;search="";summary=null;receipt=null;error=null;step=0},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("NEW TRANSACTION")}
   }
  }
 }
}

data class CartLine(val product:Product,val quantity:Int)
data class EmployeeSummary(val transactionCount:Int,val quantity:Int,val value:Double)
data class SavedReceipt(val ids:List<String>,val employee:Employee,val lines:List<CartLine>,val total:Double,val previousQuantity:Int,val previousValue:Double,val transactionAt:String)

fun buildReceiptText(r:SavedReceipt):String{
 return buildString{
  appendLine("HONEST MILK - DODLA EMPLOYEE STORE")
  appendLine()
  appendLine("Digital Receipt")
  appendLine("Receipt: "+r.ids.joinToString(", "){it.take(8).uppercase()})
  appendLine("Date: "+r.transactionAt)
  appendLine()
  appendLine("Employee: "+r.employee.name)
  appendLine("Employee ID: "+r.employee.identifier)
  appendLine()
  r.lines.forEach{
   appendLine(it.product.name+" - "+it.product.variant+" x "+it.quantity)
   appendLine("Unit price: ₹"+String.format("%.2f",it.product.price))
   appendLine("Line total: ₹"+String.format("%.2f",it.product.price*it.quantity))
  }
  appendLine()
  appendLine("Total bill: ₹"+String.format("%.2f",r.total))
  appendLine()
  appendLine("Consumption till today:")
  appendLine("Quantity: "+(r.previousQuantity+r.lines.sumOf{it.quantity}))
  appendLine("Bill value: ₹"+String.format("%.2f",r.previousValue+r.total))
 }
}
