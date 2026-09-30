package com.dodla.honestmilk.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class Product(val name:String,val variant:String,val price:Int,val volumeMl:Int)
data class Employee(val name:String,val id:String,val department:String)

class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{CounterApp()}}}

@Composable fun CounterApp(){
 var step by remember{mutableIntStateOf(0)}; var selected by remember{mutableStateOf<Product?>(null)}; var quantity by remember{mutableIntStateOf(1)}; var employee by remember{mutableStateOf<Employee?>(null)}
 val products=listOf(Product("Honest Milk","500 ML",38,500),Product("Honest Milk","1 L",75,1000))
 val employees=listOf(Employee("Ravi Kumar","EMP1025","Production",1.0,18.0),Employee("Suresh Kumar","EMP1041","HR",0.5,12.0),Employee("Anita Rao","GUEST001","Quality",1.0,9.0))
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   Text(if(step==0)"TAKE PRODUCT" else if(step==1)"WHO ARE YOU?" else if(step==2)"CONFIRM" else "SAVED",style=MaterialTheme.typography.headlineMedium)
   when(step){
    0->{products.forEach{p->OutlinedButton(onClick={selected=p},modifier=Modifier.fillMaxWidth().height(82.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(p.name+" — "+p.variant,style=MaterialTheme.typography.titleLarge);Text("₹"+p.price)}}};selected?.let{p->Text("Selected: "+p.variant);Row(verticalAlignment=Alignment.CenterVertically){Button(onClick={if(quantity>1)quantity--}){Text("−")};Text("  "+quantity+"  ",style=MaterialTheme.typography.titleLarge);Button(onClick={quantity++}){Text("+")}};Text("Total ₹"+(p.price*quantity),style=MaterialTheme.typography.titleLarge);Button(onClick={step=1},modifier=Modifier.fillMaxWidth()){Text("NEXT")}}}
    1->{Text("Select an employee to record this transaction.");employees.forEach{e->OutlinedButton(onClick={employee=e;step=2},modifier=Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth().padding(4.dp)){Text(e.name,style=MaterialTheme.typography.titleMedium);Text(e.id+" · "+e.department)}}};TextButton(onClick={step=0}){Text("BACK")}}
    2->{val p=selected!!;val e=employee!!;Text(e.name,style=MaterialTheme.typography.titleLarge);Text(e.id+" · "+e.department);Text(p.name+" — "+p.variant+" × "+quantity);Text("Transaction ₹"+(p.price*quantity));Text("Today’s total "+(e.todayLitres+(p.volumeMl*quantity/1000.0))+" L");Text("This month’s total "+(e.monthLitres+(p.volumeMl*quantity/1000.0))+" L");Button(onClick={step=3},modifier=Modifier.fillMaxWidth()){Text("CONFIRM")};TextButton(onClick={step=1}){Text("BACK")}}
    3->{Text("✓ SAVED",style=MaterialTheme.typography.headlineLarge);Text(employee!!.name);Text(selected!!.name+" "+quantity+" × "+selected!!.variant);Text("Total today: "+(employee!!.todayLitres+(selected!!.volumeMl*quantity/1000.0))+" L");Button(onClick={selected=null;employee=null;quantity=1;step=0},modifier=Modifier.fillMaxWidth()){Text("DONE")}}
   }
 }}
}
