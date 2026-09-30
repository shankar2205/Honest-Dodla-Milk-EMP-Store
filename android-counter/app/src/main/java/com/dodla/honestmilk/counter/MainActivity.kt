package com.dodla.honestmilk.counter

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
data class Employee(val id:String,val name:String,val department:String,val identifier:String)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContent { CounterApp() }
    }
}

@Composable
fun CounterApp(){
    val repo=remember{CounterRepository()}
    val scope=rememberCoroutineScope()
    var loggedIn by remember{mutableStateOf(false)}
    var checking by remember{mutableStateOf(true)}
    var loginError by remember{mutableStateOf<String?>(null)}

    LaunchedEffect(Unit){
        loggedIn=runCatching{repo.sessionExists()}.getOrDefault(false)
        checking=false
    }

    if(checking){
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
    } else if(!loggedIn){
        LoginScreen { email,password ->
            scope.launch {
                checking=true
                loginError=null
                runCatching{repo.signIn(email,password)}
                    .onSuccess{loggedIn=true}
                    .onFailure{loginError=it.message ?: "Login failed."}
                checking=false
            }
        }
        loginError?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(16.dp))}
    } else {
        CounterFlow(repo,scope){
            scope.launch{repo.signOut();loggedIn=false}
        }
    }
}

@Composable
fun LoginScreen(onLogin:(String,String)->Unit){
    var email by remember{mutableStateOf("")}
    var password by remember{mutableStateOf("")}
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp),
        horizontalAlignment=Alignment.CenterHorizontally
    ){
        Spacer(Modifier.height(60.dp))
        Text("HONEST MILK",style=MaterialTheme.typography.headlineLarge)
        Text("Counter Login",style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(email,{email=it},label={Text("Email")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Button(
            onClick={onLogin(email.trim(),password)},
            enabled=email.isNotBlank()&&password.isNotBlank(),
            modifier=Modifier.fillMaxWidth()
        ){Text("LOGIN")}
    }
}

@Composable
fun CounterFlow(repo:CounterRepository,scope:CoroutineScope,onLogout:()->Unit){
    var step by remember{mutableIntStateOf(0)}
    var products by remember{mutableStateOf<List<Product>>(emptyList())}
    var employees by remember{mutableStateOf<List<Employee>>(emptyList())}
    var selected by remember{mutableStateOf<Product?>(null)}
    var employee by remember{mutableStateOf<Employee?>(null)}
    var quantity by remember{mutableIntStateOf(1)}
    var search by remember{mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    var loading by remember{mutableStateOf(true)}
    var saving by remember{mutableStateOf(false)}
    var todayQty by remember{mutableDoubleStateOf(0.0)}
    var monthQty by remember{mutableDoubleStateOf(0.0)}

    fun loadEmployees(q:String){
        scope.launch{
            runCatching{repo.employees(q)}
                .onSuccess{employees=it.map { row -> Employee(row.id,row.name,row.department ?: "",row.employee_code ?: row.guest_code ?: "") }}
                .onFailure{error=it.message ?: "Unable to load employees."}
        }
    }

    LaunchedEffect(Unit){
        loading=true
        runCatching{
            val ps=repo.products()
            val vs=repo.variants()
            products=vs.mapNotNull{v->
                ps.find{it.id==v.product_id}?.let{p->
                    Product(v.id,p.name,v.variant_name,v.price,v.unit_volume_ml)
                }
            }
            .also { rows -> employees=rows.map { row -> Employee(row.id,row.name,"",row.identifier) } }
        }.onFailure{error=it.message ?: "Unable to load counter data."}
        loading=false
    }

    LaunchedEffect(search,step){
        if(step==1) loadEmployees(search)
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ){
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically
        ){
            Text("HONEST MILK",style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onLogout){Text("LOG OUT")}
        }

        Text(
            when(step){0->"TAKE PRODUCT";1->"SELECT EMPLOYEE";2->"CONFIRM";else->"SAVED"},
            style=MaterialTheme.typography.headlineMedium
        )

        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}

        when(step){
            0->{
                if(loading) CircularProgressIndicator()
                products.forEach{p->
                    OutlinedButton(
                        onClick={selected=p;quantity=1},
                        modifier=Modifier.fillMaxWidth().height(82.dp)
                    ){
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            Text("${p.name} — ${p.variant}",style=MaterialTheme.typography.titleLarge)
                            Text("₹${p.price}")
                        }
                    }
                }
                selected?.let{p->
                    Text("Selected: ${p.variant}")
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Button(onClick={if(quantity>1)quantity--}){Text("−")}
                        Text("  $quantity  ",style=MaterialTheme.typography.titleLarge)
                        Button(onClick={quantity++}){Text("+")}
                    }
                    Text("Total ₹${p.price*quantity}",style=MaterialTheme.typography.titleLarge)
                    Button(onClick={step=1},modifier=Modifier.fillMaxWidth()){Text("NEXT")}
                }
            }

            1->{
                OutlinedTextField(
                    search,{search=it},
                    label={Text("Search name or ID")},
                    singleLine=true,
                    modifier=Modifier.fillMaxWidth()
                )
                LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    items(employees){e->
                        OutlinedButton(
                            onClick={
                                employee=e
                                error=null
                                step=2
                                scope.launch{
                                    runCatching{repo.totals(e.id,selected!!.id)}
                                        .onSuccess{todayQty=it.first;monthQty=it.second}
                                        .onFailure{error=it.message ?: "Unable to load totals."}
                                }
                            },
                            modifier=Modifier.fillMaxWidth()
                        ){
                            Column(Modifier.fillMaxWidth().padding(4.dp)){
                                Text(e.name,style=MaterialTheme.typography.titleMedium)
                                Text("${e.identifier} · ${e.department}")
                            }
                        }
                    }
                }
                TextButton(onClick={step=0}){Text("BACK")}
            }

            2->{
                val p=selected!!
                val e=employee!!
                Text(e.name,style=MaterialTheme.typography.titleLarge)
                Text("${e.identifier} · ${e.department}")
                Text("${p.name} — ${p.variant} × $quantity")
                Text("Transaction ₹${p.price*quantity}")
                Text("Today's total ${todayQty+(p.volumeMl*quantity/1000.0)} L")
                Text("This month's total ${monthQty+(p.volumeMl*quantity/1000.0)} L")
                Button(
                    enabled=!saving,
                    onClick={
                        saving=true
                        error=null
                        scope.launch{
                            runCatching{repo.save(e.id,p.id,quantity,p.price)}
                                .onSuccess{step=3}
                                .onFailure{error=it.message ?: "Unable to save transaction."}
                            saving=false
                        }
                    },
                    modifier=Modifier.fillMaxWidth()
                ){Text(if(saving)"SAVING..." else "CONFIRM")}
                TextButton(onClick={step=1}){Text("BACK")}
            }

            else->{
                Text("✓ SAVED",style=MaterialTheme.typography.headlineLarge)
                Text(employee!!.name)
                Text("${selected!!.name} $quantity × ${selected!!.variant}")
                Text("Total today: ${todayQty+(selected!!.volumeMl*quantity/1000.0)} L")
                Button(
                    onClick={
                        selected=null
                        employee=null
                        quantity=1
                        search=""
                        todayQty=0.0
                        monthQty=0.0
                        error=null
                        step=0
                    },
                    modifier=Modifier.fillMaxWidth()
                ){Text("DONE")}
            }
        }
    }
}
