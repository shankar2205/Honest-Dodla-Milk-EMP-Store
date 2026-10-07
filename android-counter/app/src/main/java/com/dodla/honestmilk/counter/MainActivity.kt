package com.dodla.honestmilk.counter

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Product(val id:String,val name:String,val variant:String,val price:Double,val volumeMl:Int)
data class Employee(val id:String,val name:String,val department:String,val identifier:String,val type:String)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{CounterApp{shareReceipt(it)}}}
 private fun shareReceipt(text:String){startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Share digital receipt"))}
}

@Composable fun CounterApp(onShare:(String)->Unit){
 val context=LocalContext.current.applicationContext
 val repo=remember{CounterRepository(context)}; val scope=rememberCoroutineScope()
 var loggedIn by remember{mutableStateOf(false)}; var checking by remember{mutableStateOf(true)}; var loginError by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(Unit){loggedIn=runCatching{repo.sessionExists()}.getOrDefault(false);checking=false}
 if(checking) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
 else if(!loggedIn) LoginScreen(loading=checking,error=loginError){email,password->
  scope.launch{
   checking=true
   loginError=null
   runCatching{repo.signIn(email,password)}
    .onSuccess{loggedIn=true}
    .onFailure{loginError=it.message?.takeIf{m->m.isNotBlank()} ?: "Login failed. Check the email, password, and account access."}
   checking=false
  }
 }
 else CounterFlow(repo,scope,onShare){scope.launch{repo.signOut();loggedIn=false}}
}

@Composable fun LoginScreen(loading:Boolean,error:String?,onLogin:(String,String)->Unit){
 var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally){
  Spacer(Modifier.height(60.dp));Text("HONEST MILK",style=MaterialTheme.typography.headlineLarge);Text("Counter Login",style=MaterialTheme.typography.titleLarge)
  OutlinedTextField(email,{email=it},label={Text("Email")},singleLine=true,enabled=!loading,modifier=Modifier.fillMaxWidth())
  OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,enabled=!loading,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
  error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.fillMaxWidth())}
  Button(onClick={onLogin(email.trim(),password)},enabled=!loading&&email.isNotBlank()&&password.isNotBlank(),modifier=Modifier.fillMaxWidth().height(56.dp)){
   if(loading) CircularProgressIndicator(modifier=Modifier.size(22.dp),strokeWidth=2.dp) else Text("LOGIN")
  }
 }
}

@Composable fun CounterFlow(repo:CounterRepository,scope:CoroutineScope,onShare:(String)->Unit,onLogout:()->Unit){
 val now=remember{mutableStateOf(Instant.now())}
 LaunchedEffect(Unit){while(isActive){now.value=Instant.now();delay(30_000)}}
 val currentDateTime=formatLocalDateTime(now.value.toString())
 val greeting=greetingForHour(now.value.atZone(ZoneId.systemDefault()).hour)
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
 var pendingCount by remember{mutableIntStateOf(0)}
 var offlineMode by remember{mutableStateOf(false)}
 var addingAnotherItem by remember{mutableStateOf(false)}
 var showAddEmployee by remember{mutableStateOf(false)}
 var showNewEmpRegister by remember{mutableStateOf(false)}
 var newEmpCode by remember{mutableStateOf("")}
 var newEmpName by remember{mutableStateOf("")}
 var newEmpPhone by remember{mutableStateOf("")}
 var newEmpDepartment by remember{mutableStateOf("")}
 var registeringEmp by remember{mutableStateOf(false)}
 var newGuestName by remember{mutableStateOf("")}
 var newGuestPhone by remember{mutableStateOf("")}
 var newGuestDepartment by remember{mutableStateOf("")}
 var addingGuest by remember{mutableStateOf(false)}
 val productScrollState=rememberScrollState()

 fun loadEmployees(q:String){scope.launch{runCatching{repo.employees(q)}.onSuccess{employees=it.map{r->Employee(r.id,r.name,r.department?:"",r.employee_code?:r.guest_code?:"GUEST",if(r.employee_code!=null)"EMPLOYEE" else "GUEST")}}.onFailure{error=it.message?:"Unable to load employees."}}}
 fun addToCart(){
  val p=selectedProduct?:return
  val existing=cart.firstOrNull{it.product.id==p.id}
  cart=if(existing==null) cart+CartLine(p,quantity) else cart.map{if(it.product.id==p.id)it.copy(quantity=it.quantity+quantity)else it}
  selectedProduct=null;quantity=1;addingAnotherItem=false
 }
 fun cartTotal()=cart.sumOf{it.product.price*it.quantity}

 LaunchedEffect(Unit){
  offlineMode=!repo.isOnline()
  runCatching{repo.syncPending()}.onFailure{}
  pendingCount=repo.pendingCount()
  runCatching{
   val ps=repo.products()
   val vs=repo.variants()
   products=vs.mapNotNull{v->ps.find{it.id==v.product_id}?.let{p->Product(v.id,p.name,v.variant_name,v.price,v.unit_volume_ml)}}
   if(products.isEmpty()) error("No active products are available. Please check the Admin product setup.")
  }.onFailure{error=it.message?.takeIf{m->m.isNotBlank()} ?: "Unable to load counter data."}
  offlineMode=!repo.isOnline()
  pendingCount=repo.pendingCount()
  loading=false
 }
 LaunchedEffect(search,step){if(step==1)loadEmployees(search)}
 LaunchedEffect(Unit){
  while(isActive){
   delay(15000)
   if(repo.isOnline()){
    runCatching{repo.syncPending()}.onSuccess{
     pendingCount=repo.pendingCount()
     offlineMode=false
    }
   } else {
    offlineMode=true
   }
  }
 }

 val lifecycleOwner=LocalLifecycleOwner.current
 DisposableEffect(lifecycleOwner){
  val observer=LifecycleEventObserver{_,event->
   if(event==Lifecycle.Event.ON_START){
    scope.launch{
     if(repo.isOnline()){
      runCatching{repo.syncPending()}.onSuccess{
       pendingCount=repo.pendingCount()
       offlineMode=false
      }
     } else {
      offlineMode=true
     }
    }
   }
  }
  lifecycleOwner.lifecycle.addObserver(observer)
  onDispose{lifecycleOwner.lifecycle.removeObserver(observer)}
 }

 Column((if(step==0) Modifier.fillMaxSize().padding(20.dp).verticalScroll(productScrollState) else Modifier.fillMaxSize().padding(20.dp)),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("HONEST MILK",style=MaterialTheme.typography.titleLarge);TextButton(onClick=onLogout){Text("LOG OUT")}}
  if(step==0){
   Text(greeting+"!",style=MaterialTheme.typography.headlineSmall)
   Text(currentDateTime,style=MaterialTheme.typography.bodyMedium)
  }
  Text(when(step){0->"TAKE PRODUCTS";1->"SELECT EMPLOYEE";2->"CONFIRM";else->"DIGITAL RECEIPT"},style=MaterialTheme.typography.headlineMedium)
  if(offlineMode) Text("OFFLINE MODE — cached counter data is being used.",color=MaterialTheme.colorScheme.error)
  if(pendingCount>0) Text("Pending sync: $pendingCount transaction(s). They will sync when online.",style=MaterialTheme.typography.labelMedium)
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  when(step){
   0->{
    Text(if(addingAnotherItem)"SELECT ANOTHER ITEM" else "SELECT PRODUCTS",style=MaterialTheme.typography.titleLarge)
    if(loading)CircularProgressIndicator()
    products.forEach{p->
     OutlinedButton(onClick={selectedProduct=p;quantity=1;error=null},modifier=Modifier.fillMaxWidth().height(72.dp)){
      Column(horizontalAlignment=Alignment.CenterHorizontally){Text(p.name+" — "+p.variant,style=MaterialTheme.typography.titleMedium);Text("₹"+String.format("%.2f",p.price))}
     }
    }
    selectedProduct?.let{p->
     Text("Add: "+p.name+" — "+p.variant,style=MaterialTheme.typography.titleMedium)
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){Button(onClick={if(quantity>1)quantity--}){Text("−")};Text("  "+quantity+"  ",style=MaterialTheme.typography.titleLarge);Button(onClick={quantity++}){Text("+")}}
     Button(onClick={addToCart()},modifier=Modifier.fillMaxWidth().height(52.dp)){Text("ADD TO CART")}
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
     OutlinedButton(
      onClick={
       selectedProduct=null
       quantity=1
       error=null
       addingAnotherItem=true
       scope.launch{productScrollState.animateScrollTo(0)}
      },
      modifier=Modifier.fillMaxWidth().height(52.dp)
     ){Text("+ ADD ANOTHER ITEM")}
     Button(onClick={step=1},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("NEXT")}
    }
   }
   1->{
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
     OutlinedTextField(search,{search=it},label={Text("Employee code, name or mobile")},singleLine=true,modifier=Modifier.weight(1f))
     if(search.isNotBlank())TextButton(onClick={search=""}){Text("CLEAR")}
    }
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){
     OutlinedButton(
      onClick={showNewEmpRegister=true;error=null},
      enabled=!registeringEmp,
      modifier=Modifier.weight(1f).height(54.dp)
     ){Text("NEW EMP REGISTER")}
     OutlinedButton(
      onClick={showAddEmployee=true;error=null},
      enabled=!addingGuest,
      modifier=Modifier.weight(1f).height(54.dp)
     ){Text("ADD EMPLOYEE")}
    }
    Text("NEW EMP REGISTER creates an employee. ADD EMPLOYEE creates a guest only.",style=MaterialTheme.typography.labelMedium)
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.weight(1f,false)){
     items(employees){e->OutlinedButton(onClick={selectedEmployee=e;error=null;summary=null;step=2;scope.launch{runCatching{repo.summary(e.id)}.onSuccess{summary=it}.onFailure{if(repo.isOnline())error=it.message?:"Unable to load employee summary."}}},modifier=Modifier.fillMaxWidth()){
      Column(Modifier.fillMaxWidth().padding(4.dp)){Text(e.name,style=MaterialTheme.typography.titleMedium);Text(e.identifier+" · "+e.department);Text(e.type,style=MaterialTheme.typography.labelSmall)}
     }}
    }
    TextButton(onClick={step=0}){Text("BACK")}
    if(showNewEmpRegister){
     AlertDialog(onDismissRequest={if(!registeringEmp)showNewEmpRegister=false},title={Text("NEW EMP REGISTER")},text={
      Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
       Text("This will be registered as an employee, not a guest.",style=MaterialTheme.typography.bodyMedium)
       OutlinedTextField(newEmpCode,{newEmpCode=it},label={Text("Employee ID *")},singleLine=true,enabled=!registeringEmp,modifier=Modifier.fillMaxWidth())
       OutlinedTextField(newEmpName,{newEmpName=it},label={Text("Name *")},singleLine=true,enabled=!registeringEmp,modifier=Modifier.fillMaxWidth())
       OutlinedTextField(newEmpPhone,{newEmpPhone=it},label={Text("Mobile (optional)")},singleLine=true,enabled=!registeringEmp,modifier=Modifier.fillMaxWidth())
       OutlinedTextField(newEmpDepartment,{newEmpDepartment=it},label={Text("Department (optional)")},singleLine=true,enabled=!registeringEmp,modifier=Modifier.fillMaxWidth())
      }
     },confirmButton={
      Button(enabled=!registeringEmp&&newEmpCode.trim().isNotBlank()&&newEmpName.trim().isNotBlank()&&repo.isOnline(),onClick={
       registeringEmp=true;error=null
       scope.launch{
        runCatching{repo.createEmployee(newEmpCode,newEmpName,newEmpPhone,newEmpDepartment)}
         .onSuccess{emp->
          val e=Employee(emp.id,emp.name,emp.department.orEmpty(),emp.employee_code?:"","EMPLOYEE")
          employees=(listOf(e)+employees).distinctBy{it.id};selectedEmployee=e
          newEmpCode="";newEmpName="";newEmpPhone="";newEmpDepartment=""
          showNewEmpRegister=false;summary=null;step=2
          runCatching{repo.summary(e.id)}.onSuccess{summary=it}.onFailure{if(repo.isOnline())error=it.message?:"Unable to load employee summary."}
         }.onFailure{error=it.message?:"Unable to register employee."}
        registeringEmp=false
       }
      }){Text(if(registeringEmp)"REGISTERING..." else "REGISTER & CONTINUE")}
     },dismissButton={TextButton(onClick={showNewEmpRegister=false},enabled=!registeringEmp){Text("CANCEL")}})
    }
    if(showAddEmployee){
     AlertDialog(
      onDismissRequest={if(!addingGuest)showAddEmployee=false},
      title={Text("ADD EMPLOYEE")},
      text={
       Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("This will be added as a guest employee only.",style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(newGuestName,{newGuestName=it},label={Text("Name *")},singleLine=true,enabled=!addingGuest,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(newGuestPhone,{newGuestPhone=it},label={Text("Mobile (optional)")},singleLine=true,enabled=!addingGuest,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(newGuestDepartment,{newGuestDepartment=it},label={Text("Department (optional)")},singleLine=true,enabled=!addingGuest,modifier=Modifier.fillMaxWidth())
       }
      },
      confirmButton={
       Button(enabled=!addingGuest&&newGuestName.trim().isNotBlank()&&repo.isOnline(),onClick={
        addingGuest=true
        error=null
        scope.launch{
         runCatching{repo.createGuestEmployee(newGuestName,newGuestPhone,newGuestDepartment)}
          .onSuccess{guest->
           val e=Employee(guest.id,guest.name,guest.department.orEmpty(),guest.guest_code?:"GUEST","GUEST")
           employees=(listOf(e)+employees).distinctBy{it.id}
           selectedEmployee=e
           newGuestName=""
           newGuestPhone=""
           newGuestDepartment=""
           showAddEmployee=false
           summary=null
           step=2
           runCatching{repo.summary(e.id)}.onSuccess{summary=it}.onFailure{if(repo.isOnline())error=it.message?:"Unable to load employee summary."}
          }
          .onFailure{error=it.message?:"Unable to add guest employee."}
         addingGuest=false
        }
       }){Text(if(addingGuest)"ADDING..." else "ADD & CONTINUE")}
      },
      dismissButton={TextButton(onClick={showAddEmployee=false},enabled=!addingGuest){Text("CANCEL")}}
     )
    }
   }
   2->{
    val e=selectedEmployee!!;val s=summary
    Text("EMPLOYEE",style=MaterialTheme.typography.labelLarge);Text(e.name,style=MaterialTheme.typography.titleLarge);Text(e.identifier+" · "+e.department);Text(e.type,style=MaterialTheme.typography.labelMedium)
    HorizontalDivider();Text("CURRENT TRANSACTION",style=MaterialTheme.typography.labelLarge)
    cart.forEach{line->Text(line.product.name+" — "+line.product.variant+" × "+line.quantity);Text("₹"+String.format("%.2f",line.product.price)+" each · ₹"+String.format("%.2f",line.product.price*line.quantity))}
    Text("Transaction total ₹"+String.format("%.2f",cartTotal()),style=MaterialTheme.typography.titleLarge)
    HorizontalDivider();Text("CONSUMPTION TILL TODAY",style=MaterialTheme.typography.labelLarge)
    if(s==null && !repo.isOnline()){
     Text("Previous consumption history is unavailable offline.",style=MaterialTheme.typography.bodyMedium)
    } else {
     Text("Previous transactions: "+(s?.transactionCount?:0));Text("Previous quantity: "+(s?.quantity?:0));Text("Previous bill value: ₹"+String.format("%.2f",s?.value?:0.0))
    }
    if((s?.variants?:emptyList()).isNotEmpty()){
     Text("Variant-wise quantity:",style=MaterialTheme.typography.labelMedium)
     s?.variants?.forEach{v->Text(v.variantName+" : "+v.quantity+" till today")}
    }
    Text("After this: "+((s?.quantity?:0)+cart.sumOf{it.quantity})+" units · ₹"+String.format("%.2f",(s?.value?:0.0)+cartTotal()))
    Button(enabled=!saving,onClick={
     saving=true;error=null
     scope.launch{
      runCatching{repo.save(e.id,cart.map{TransactionLine(it.product.id,it.quantity,it.product.price)})}
       .onSuccess{saved->
        val pending=saved.any{repo.isPending(it.id)}
        receipt=SavedReceipt(saved.map{it.id},e,cart,cartTotal(),s?.quantity?:0,s?.value?:0.0,saved.firstOrNull()?.transaction_at?:"",pending)
        pendingCount=repo.pendingCount()
        offlineMode=pending||!repo.isOnline()
        step=3
       }
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
      if(r.pending)Text("Status: PENDING SYNC",color=MaterialTheme.colorScheme.error)
      Text("Day: "+formatReceiptDay(r.transactionAt))
      Text("Date: "+formatReceiptDate(r.transactionAt))
      Text("Time: "+formatReceiptTime(r.transactionAt))
      Text("Employee: "+r.employee.name);Text("Employee ID: "+r.employee.identifier)
      r.lines.forEach{Text(it.product.name+" — "+it.product.variant+" × "+it.quantity+" = ₹"+String.format("%.2f",it.product.price*it.quantity))}
      HorizontalDivider();Text("Total bill: ₹"+String.format("%.2f",r.total),style=MaterialTheme.typography.titleMedium)
      Text("Consumption till today");Text("Quantity: "+(r.previousQuantity+r.lines.sumOf{it.quantity}));Text("Bill value: ₹"+String.format("%.2f",r.previousValue+r.total))
     }
    }
    Button(onClick={onShare(buildReceiptText(r))},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("SHARE DIGITAL RECEIPT")}
    Button(onClick={cart=emptyList();selectedProduct=null;selectedEmployee=null;quantity=1;search="";summary=null;receipt=null;error=null;addingAnotherItem=false;step=0},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("NEW TRANSACTION")}
   }
  }
 }
}

data class CartLine(val product:Product,val quantity:Int)
data class SavedReceipt(val ids:List<String>,val employee:Employee,val lines:List<CartLine>,val total:Double,val previousQuantity:Int,val previousValue:Double,val transactionAt:String,val pending:Boolean)

fun buildReceiptText(r:SavedReceipt):String{
 return buildString{
  appendLine("HONEST MILK - DODLA EMPLOYEE STORE")
  appendLine()
  appendLine("Digital Receipt")
  appendLine("Day: "+formatReceiptDay(r.transactionAt))
  appendLine("Date: "+formatReceiptDate(r.transactionAt))
  appendLine("Time: "+formatReceiptTime(r.transactionAt))
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


fun formatLocalDateTime(instantText:String):String = runCatching {
 val z=Instant.parse(instantText).atZone(ZoneId.systemDefault())
 z.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy · hh:mm a",Locale.getDefault()))
}.getOrDefault(instantText)
fun formatReceiptDay(instantText:String):String = runCatching { Instant.parse(instantText).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE",Locale.getDefault())) }.getOrDefault("—")
fun formatReceiptDate(instantText:String):String = runCatching { Instant.parse(instantText).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMMM yyyy",Locale.getDefault())) }.getOrDefault("—")
fun formatReceiptTime(instantText:String):String = runCatching { Instant.parse(instantText).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("hh:mm:ss a",Locale.getDefault())) }.getOrDefault("—")
fun greetingForHour(hour:Int):String = when(hour){in 5..11->"Good Morning";in 12..16->"Good Afternoon";in 17..20->"Good Evening";else->"Good Night"}
