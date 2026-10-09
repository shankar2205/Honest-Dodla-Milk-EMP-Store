package com.dodla.honestmilk.counter

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DAIRY_BG="https://images.unsplash.com/photo-1573731399281-6540bc50ed91?auto=format&fit=crop&fm=jpg&q=100&w=3200"
private val Ink=Color(0xFF0E1B2A)
private val Cream=Color(0xFFF8F4E8)
private val WhiteCard=Color(0xFFFFFFFF)
private val Teal=Color(0xFF096B73)
private val TealPale=Color(0xFFE5F2F0)
private val Gold=Color(0xFFC27A12)
private val GoldPale=Color(0xFFFFF2D5)
private val Border=Color(0xFFB7C5C7)

@Composable
private fun DairyBackground(tint:Color=Color(0xFF12344A)){
 Box(Modifier.fillMaxSize()){
  AsyncImage(model=DAIRY_BG,contentDescription="Dairy farm and cow background",modifier=Modifier.fillMaxSize().graphicsLayer{alpha=0.70f},contentScale=ContentScale.Crop)
  Box(Modifier.fillMaxSize().background(tint.copy(alpha=0.12f)))
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha=0.03f),Color.Black.copy(alpha=0.01f)))))
 }
}

data class Product(val id:String,val name:String,val variant:String,val price:Double,val volumeMl:Int)
data class Employee(val id:String,val name:String,val department:String,val identifier:String,val type:String)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{CounterApp{shareReceipt(it)}}}
 private fun shareReceipt(text:String){startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Share digital receipt"))}
}

@Composable fun CounterApp(onShare:(String)->Unit){
 val darkColors=darkColorScheme(
  primary=Color(0xFF096B73),
  onPrimary=Color.White,
  primaryContainer=Color(0xFF5A3A00),
  onPrimaryContainer=Color(0xFFFFE7A0),
  secondary=Color(0xFFC27A12),
  onSecondary=Color.White,
  secondaryContainer=Color(0xFF073E43),
  onSecondaryContainer=Color(0xFFB8F4FB),
  background=Color(0xFFF8F4E8),
  onBackground=Color(0xFF0E1B2A),
  surface=Color(0xFFF8F4E8),
  onSurface=Color(0xFF0E1B2A),
  surfaceVariant=Color(0xFFE8EEEE),
  onSurfaceVariant=Color(0xFF42535D),
  error=Color(0xFFFF6B6B),
  errorContainer=Color(0xFF4A1F26),
  onErrorContainer=Color(0xFFFFDAD6)
 )
 val boldTypography=Typography(
  displayLarge=MaterialTheme.typography.displayLarge.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.ExtraBold,letterSpacing=(-0.4).sp),
  displayMedium=MaterialTheme.typography.displayMedium.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.ExtraBold),
  displaySmall=MaterialTheme.typography.displaySmall.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.ExtraBold),
  headlineLarge=MaterialTheme.typography.headlineLarge.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.ExtraBold,letterSpacing=0.2.sp),
  headlineMedium=MaterialTheme.typography.headlineMedium.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.ExtraBold,letterSpacing=0.3.sp),
  headlineSmall=MaterialTheme.typography.headlineSmall.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,letterSpacing=0.2.sp),
  titleLarge=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.Bold),
  titleMedium=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Bold),
  labelLarge=MaterialTheme.typography.labelLarge.copy(fontWeight=FontWeight.Bold),
  labelMedium=MaterialTheme.typography.labelMedium.copy(fontWeight=FontWeight.Bold)
 )
 MaterialTheme(colorScheme=darkColors,typography=boldTypography){
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
}

@Composable fun LoginScreen(loading:Boolean,error:String?,onLogin:(String,String)->Unit){
 var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")}
 val accent by animateColorAsState(
  targetValue=if(loading) Color(0xFFB9A0FF) else Color(0xFF79E0CF),
  animationSpec=tween(durationMillis=450),
  label="loginAccent"
 )
 Box(Modifier.fillMaxSize()){
  // Keep the existing full-screen dairy photograph and tint unchanged.
  DairyBackground(Color(0xFF0D4050))
  Box(Modifier.fillMaxSize().background(Color.White.copy(alpha=0.01f)))
  Box(Modifier.fillMaxSize().padding(20.dp),contentAlignment=Alignment.Center){
   AnimatedVisibility(
    visible=true,
    enter=fadeIn(animationSpec=tween(durationMillis=650))+
     slideInVertically(initialOffsetY={height->height/12},animationSpec=tween(durationMillis=650))
   ){
    Card(
     Modifier.fillMaxWidth(),
     colors=CardDefaults.cardColors(containerColor=Color(0xFF102D35)),
     elevation=CardDefaults.cardElevation(defaultElevation=18.dp),
     shape=MaterialTheme.shapes.extraLarge
    ){
     Column(
      Modifier.fillMaxWidth()
       .background(Brush.linearGradient(listOf(Color(0xFF173F47),Color(0xFF102D35),Color(0xFF142B3D))))
       .padding(24.dp),
      verticalArrangement=Arrangement.spacedBy(16.dp),
      horizontalAlignment=Alignment.CenterHorizontally
     ){
      Surface(
       shape=MaterialTheme.shapes.large,
       color=Color(0xFF79E0CF).copy(alpha=0.13f),
       contentColor=accent
      ){
       Text(
        "✦  DODLA DAIRY",
        modifier=Modifier.padding(horizontal=14.dp,vertical=8.dp),
        style=MaterialTheme.typography.labelMedium.copy(
         fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,letterSpacing=2.2.sp
        )
       )
      }
      Text(
       "Honest Milk",
       color=Color(0xFFF5FAF8),
       style=MaterialTheme.typography.headlineLarge.copy(
        fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.ExtraBold,
        letterSpacing=(-0.8).sp
       )
      )
      Text(
       "EMPLOYEE STORE",
       color=Color(0xFFF0C879),
       style=MaterialTheme.typography.labelLarge.copy(
        fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,letterSpacing=3.sp
       )
      )
      HorizontalDivider(color=Color.White.copy(alpha=0.16f),thickness=1.dp)
      Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(5.dp)){
       Text(
        "Welcome back",
        color=Color(0xFFF5FAF8),
        style=MaterialTheme.typography.headlineSmall.copy(
         fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,letterSpacing=(-0.3).sp
        )
       )
       Text(
        "Sign in to continue to your counter",
        color=Color(0xFFB8CFD0),
        style=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.SansSerif)
       )
      }
      val fieldColors=OutlinedTextFieldDefaults.colors(
       focusedTextColor=Color(0xFFF5FAF8),
       unfocusedTextColor=Color(0xFFF5FAF8),
       focusedLabelColor=Color(0xFF79E0CF),
       unfocusedLabelColor=Color(0xFFB8CFD0),
       focusedPlaceholderColor=Color(0xFF91A9AD),
       unfocusedPlaceholderColor=Color(0xFF91A9AD),
       focusedBorderColor=Color(0xFF79E0CF),
       unfocusedBorderColor=Color(0xFF55767D),
       cursorColor=Color(0xFF79E0CF),
       focusedContainerColor=Color.White.copy(alpha=0.045f),
       unfocusedContainerColor=Color.White.copy(alpha=0.025f),
       disabledContainerColor=Color.White.copy(alpha=0.02f)
      )
      OutlinedTextField(
       value=email,
       onValueChange={email=it},
       label={Text("Email")},
       placeholder={Text("Enter your email")},
       singleLine=true,
       enabled=!loading,
       modifier=Modifier.fillMaxWidth(),
       shape=MaterialTheme.shapes.large,
       colors=fieldColors
      )
      OutlinedTextField(
       value=password,
       onValueChange={password=it},
       label={Text("Password")},
       placeholder={Text("Enter your password")},
       singleLine=true,
       enabled=!loading,
       visualTransformation=PasswordVisualTransformation(),
       modifier=Modifier.fillMaxWidth(),
       shape=MaterialTheme.shapes.large,
       colors=fieldColors
      )
      AnimatedVisibility(
       visible=error!=null,
       enter=fadeIn(animationSpec=tween(220))+slideInVertically(initialOffsetY={it/5},animationSpec=tween(220))
      ){
       error?.let{
        Surface(
         Modifier.fillMaxWidth(),
         shape=MaterialTheme.shapes.medium,
         color=Color(0xFF5B2930),
         contentColor=Color(0xFFFFDAD6)
        ){
         Text(it,modifier=Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)
        }
       }
      }
      Button(
       onClick={onLogin(email.trim(),password)},
       enabled=!loading&&email.isNotBlank()&&password.isNotBlank(),
       modifier=Modifier.fillMaxWidth().height(54.dp),
       shape=MaterialTheme.shapes.large,
       colors=ButtonDefaults.buttonColors(
        containerColor=Color(0xFF79E0CF),
        contentColor=Color(0xFF102D35),
        disabledContainerColor=Color(0xFF79E0CF).copy(alpha=0.35f),
        disabledContentColor=Color(0xFF102D35).copy(alpha=0.65f)
       )
      ){
       Crossfade(targetState=loading,animationSpec=tween(220),label="loginButtonState"){busy->
        if(busy) CircularProgressIndicator(modifier=Modifier.size(22.dp),strokeWidth=2.dp,color=Color(0xFF102D35))
        else Text(
         "SIGN IN  →",
         style=MaterialTheme.typography.titleMedium.copy(
          fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.ExtraBold,letterSpacing=1.1.sp
         )
        )
       }
      }
      Text(
       "SECURE EMPLOYEE ACCESS",
       color=Color(0xFF86A5A8),
       style=MaterialTheme.typography.labelSmall.copy(
        fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,letterSpacing=1.8.sp
       )
      )
     }
    }
   }
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
 var screenEntering by remember{mutableStateOf(true)}
 var receiptEntering by remember{mutableStateOf(false)}
 var offlineMode by remember{mutableStateOf(false)}
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
 var productQuantities by remember{mutableStateOf<Map<String,Int>>(emptyMap())}

 fun loadEmployees(q:String){scope.launch{runCatching{repo.employees(q)}.onSuccess{employees=it.map{r->Employee(r.id,r.name,r.department?:"",r.employee_code?:r.guest_code?:"GUEST",if(r.employee_code!=null)"EMPLOYEE" else "GUEST")}}.onFailure{error=it.message?:"Unable to load employees."}}}
 fun addSelectedProductsToCart(){
  val selected=products.mapNotNull{p->
   val q=productQuantities[p.id]?:0
   if(q>0) CartLine(p,q) else null
  }
  if(selected.isEmpty()) return
  selected.forEach{line->
   val existing=cart.firstOrNull{it.product.id==line.product.id}
   cart=if(existing==null) cart+line else cart.map{if(it.product.id==line.product.id)it.copy(quantity=it.quantity+line.quantity)else it}
  }
  productQuantities=emptyMap()
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

 Box(Modifier.fillMaxSize()){
  DairyBackground(when(step){
   0->Color(0xFF0A5260)
   1->Color(0xFF4C3A75)
   2->Color(0xFF70402B)
   else->Color(0xFF17604C)
  })
  Box(Modifier.fillMaxSize().background(Color.White.copy(alpha=0.015f)))
 Column(Modifier.fillMaxSize().padding(horizontal=14.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
  Card(
   Modifier.fillMaxWidth(),
   colors=CardDefaults.cardColors(containerColor=Color(0xFFFFFBF2).copy(alpha=0.96f)),
   elevation=CardDefaults.cardElevation(defaultElevation=8.dp),
   shape=MaterialTheme.shapes.large
  ){
   Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)){
     Text("HONEST MILK",style=MaterialTheme.typography.headlineSmall.copy(fontFamily=FontFamily.Serif,fontWeight=FontWeight.ExtraBold,letterSpacing=2.sp))
     Text("DODLA EMPLOYEE STORE",style=MaterialTheme.typography.labelMedium)
    }
    TextButton(onClick=onLogout){Text("LOG OUT")}
   }
  }
  if(step==0){
   Card(
    Modifier.fillMaxWidth(),
    colors=CardDefaults.cardColors(containerColor=Cream),
    border=androidx.compose.foundation.BorderStroke(1.dp,Border),
    elevation=CardDefaults.cardElevation(defaultElevation=5.dp)
   ){
    Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
     Text(greeting+"!",color=Ink,style=MaterialTheme.typography.headlineSmall.copy(fontWeight=FontWeight.ExtraBold))
     Text(currentDateTime,color=Ink.copy(alpha=0.82f),style=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold))
    }
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
   listOf("PRODUCTS","PERSON","REVIEW","RECEIPT").forEachIndexed{i,label->
    val active=i==step
    val tabColor by animateColorAsState(if(active) Teal else Cream,animationSpec=tween(180),label="tab_color")
    Surface(modifier=Modifier.weight(1f).height(32.dp),shape=MaterialTheme.shapes.medium,color=tabColor,border=androidx.compose.foundation.BorderStroke(1.dp,if(active) Teal else Border)){
     Text("${i+1}  $label",style=MaterialTheme.typography.labelMedium.copy(color=if(active) Color.White else Ink,fontWeight=if(active) FontWeight.ExtraBold else FontWeight.Bold,letterSpacing=0.4.sp),modifier=Modifier.padding(vertical=4.dp,horizontal=1.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
   }
  }
  if(step==1||step==3) Text(if(step==1) "Select Employee/Guest" else "Receipt",style=MaterialTheme.typography.titleLarge.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,letterSpacing=0.2.sp))
  if(offlineMode){
   Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.errorContainer){
    Text("OFFLINE MODE — cached counter data is being used.",modifier=Modifier.padding(10.dp),color=MaterialTheme.colorScheme.onErrorContainer)
   }
  }
  if(pendingCount>0){
   Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.secondaryContainer){
    Text("Pending sync: $pendingCount transaction(s). They will sync when online.",modifier=Modifier.padding(10.dp),style=MaterialTheme.typography.labelMedium)
   }
  }
  error?.let{
   Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.errorContainer){
    Text(it,modifier=Modifier.padding(10.dp),color=MaterialTheme.colorScheme.onErrorContainer)
   }
  }
  LaunchedEffect(step){screenEntering=true;delay(18);screenEntering=false;if(step==3){receiptEntering=true;delay(18);receiptEntering=false}}
  val screenOffset by animateFloatAsState(if(screenEntering) 18f else 0f,animationSpec=tween(220),label="screen_offset")
  val screenAlpha by animateFloatAsState(if(screenEntering) 0.82f else 1f,animationSpec=tween(220),label="screen_alpha")
  val receiptScale by animateFloatAsState(if(receiptEntering) 0.94f else 1f,animationSpec=tween(280),label="receipt_scale")
  Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).graphicsLayer{alpha=screenAlpha;translationY=screenOffset},verticalArrangement=Arrangement.spacedBy(8.dp)){
  when(step){   0->{
    Text("Choose Products",style=MaterialTheme.typography.titleLarge.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,letterSpacing=0.1.sp))
    Text("Select the products being taken",style=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,letterSpacing=0.1.sp))
    if(loading)CircularProgressIndicator()
    BoxWithConstraints(Modifier.fillMaxWidth()){
     val columns=if(maxWidth<600.dp)2 else 4
     val rows=products.chunked(columns)
     Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
      rows.forEach{row->
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        row.forEach{p->
         val q=productQuantities[p.id]?:0
         val animatedQ by animateIntAsState(q,animationSpec=tween(140),label="quantity")
         ElevatedCard(
          colors=CardDefaults.elevatedCardColors(containerColor=WhiteCard),
          modifier=Modifier.border(2.dp,Teal,MaterialTheme.shapes.medium).weight(1f).aspectRatio(1.18f),
          elevation=CardDefaults.elevatedCardElevation(defaultElevation=6.dp)
         ){
          Column(
           Modifier.fillMaxSize().padding(8.dp),
           verticalArrangement=Arrangement.SpaceBetween,
           horizontalAlignment=Alignment.CenterHorizontally
          ){
           Column(horizontalAlignment=Alignment.CenterHorizontally){
            Text(p.name,style=MaterialTheme.typography.titleLarge,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Text(p.variant,style=MaterialTheme.typography.titleMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
           }
           Text("₹"+String.format("%.2f",p.price),style=MaterialTheme.typography.headlineSmall)
           Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            FilledTonalButton(onClick={
             productQuantities=productQuantities.toMutableMap().apply{put(p.id,maxOf(0,(this[p.id]?:0)-1))}
            },enabled=q>0,colors=ButtonDefaults.filledTonalButtonColors(containerColor=TealPale,contentColor=Teal,disabledContainerColor=TealPale.copy(alpha=0.55f),disabledContentColor=Teal.copy(alpha=0.45f)),border=androidx.compose.foundation.BorderStroke(1.5.dp,Teal),contentPadding=PaddingValues(horizontal=8.dp,vertical=3.dp),modifier=Modifier.height(36.dp)){Text("−",style=MaterialTheme.typography.titleMedium)}
            Text(animatedQ.toString(),style=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.ExtraBold))
            FilledTonalButton(onClick={
             productQuantities=productQuantities.toMutableMap().apply{put(p.id,(this[p.id]?:0)+1)}
            },colors=ButtonDefaults.filledTonalButtonColors(containerColor=Teal,contentColor=Color.White),border=androidx.compose.foundation.BorderStroke(1.5.dp,Teal),contentPadding=PaddingValues(horizontal=10.dp,vertical=3.dp),modifier=Modifier.height(38.dp)){Text("+",style=MaterialTheme.typography.titleMedium)}
           }
          }
         }
        }
        repeat(columns-row.size){
         Spacer(Modifier.weight(1f).aspectRatio(1f))
        }
       }
      }
     }
    }
    val selectedCount=productQuantities.values.sum()
    if(selectedCount>0){
     Card(
      Modifier.fillMaxWidth(),
      colors=CardDefaults.cardColors(containerColor=GoldPale),
      border=androidx.compose.foundation.BorderStroke(1.dp,Gold.copy(alpha=0.55f)),
      elevation=CardDefaults.cardElevation(defaultElevation=3.dp)
     ){
      Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
        Column{Text("SELECTED ITEMS",style=MaterialTheme.typography.labelLarge);Text("$selectedCount units",style=MaterialTheme.typography.titleLarge)}
        Text("Ready to add",style=MaterialTheme.typography.labelMedium)
       }
       Button(onClick={addSelectedProductsToCart()},colors=ButtonDefaults.buttonColors(containerColor=Teal,contentColor=Color.White),border=androidx.compose.foundation.BorderStroke(2.dp,Teal),modifier=Modifier.fillMaxWidth().height(50.dp)){
        Text("ADD TO CART",style=MaterialTheme.typography.titleMedium)
       }
      }
     }
    }
    if(cart.isNotEmpty()){
     Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Cream),border=androidx.compose.foundation.BorderStroke(1.dp,Border),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
      Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
        Text("CURRENT CART",style=MaterialTheme.typography.titleLarge)
        Text(cart.sumOf{it.quantity}.toString()+" units",style=MaterialTheme.typography.labelLarge)
       }
       cart.forEachIndexed{index,line->
        Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.small,color=WhiteCard){
         Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
          Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
           Text(line.product.name,style=MaterialTheme.typography.titleMedium)
           Text(line.product.variant+" · ₹"+String.format("%.2f",line.product.price)+" × "+line.quantity,style=MaterialTheme.typography.bodyMedium)
          }
          Column(horizontalAlignment=Alignment.End){
           Text("₹"+String.format("%.2f",line.product.price*line.quantity),style=MaterialTheme.typography.titleMedium)
           TextButton(onClick={cart=cart.filterIndexed{i,_->i!=index}}){Text("REMOVE")}
          }
         }
        }
       }
       HorizontalDivider()
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
        Text("CART TOTAL",style=MaterialTheme.typography.titleMedium)
        Text("₹"+String.format("%.2f",cartTotal()),style=MaterialTheme.typography.headlineSmall)
       }
      }
     }
    }
   }
   1->{
        
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Cream),border=androidx.compose.foundation.BorderStroke(1.dp,Border),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
     Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
      OutlinedTextField(
       search,
       {search=it},
       label={Text("Search employee ID, name or mobile")},
       placeholder={Text("Start typing to find someone")},
       singleLine=true,
       colors=OutlinedTextFieldDefaults.colors(
        focusedTextColor=Ink,
        unfocusedTextColor=Ink,
        focusedLabelColor=Teal,
        unfocusedLabelColor=Ink,
        focusedPlaceholderColor=Ink.copy(alpha=0.62f),
        unfocusedPlaceholderColor=Ink.copy(alpha=0.62f),
        focusedBorderColor=Teal,
        unfocusedBorderColor=Border,
        cursorColor=Teal
       ),
       modifier=Modifier.fillMaxWidth()
      )
      if(search.isNotBlank()){
       TextButton(onClick={search=""},modifier=Modifier.align(Alignment.End)){Text("CLEAR SEARCH")}
      }
     }
    }

    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
     ElevatedCard(
      colors=CardDefaults.elevatedCardColors(containerColor=WhiteCard),
      modifier=Modifier.border(1.dp,Teal.copy(alpha=0.45f),MaterialTheme.shapes.medium).weight(1f).height(64.dp).clickable{showNewEmpRegister=true;error=null},
      elevation=CardDefaults.elevatedCardElevation(defaultElevation=4.dp)
     ){
      Column(Modifier.fillMaxSize().padding(6.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
       Text("NEW EMP",style=MaterialTheme.typography.labelSmall)
       Text("REGISTER",style=MaterialTheme.typography.titleSmall)
      }
     }
     ElevatedCard(
      colors=CardDefaults.elevatedCardColors(containerColor=WhiteCard),
      modifier=Modifier.border(1.dp,Teal.copy(alpha=0.45f),MaterialTheme.shapes.medium).weight(1f).height(64.dp).clickable{showAddEmployee=true;error=null},
      elevation=CardDefaults.elevatedCardElevation(defaultElevation=4.dp)
     ){
      Column(Modifier.fillMaxSize().padding(6.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterVertically){
       Text("REGISTER AS",style=MaterialTheme.typography.labelSmall)
       Text("GUEST",style=MaterialTheme.typography.titleSmall)
      }
     }
    }

    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
     employees.forEach{e->
      ElevatedCard(
       colors=CardDefaults.elevatedCardColors(containerColor=Cream),
       modifier=Modifier.border(1.dp,Border,MaterialTheme.shapes.medium).fillMaxWidth().clickable{
        selectedEmployee=e
        error=null
        summary=null
        step=2
        scope.launch{
         runCatching{repo.summary(e.id)}
          .onSuccess{summary=it}
          .onFailure{if(repo.isOnline())error=it.message?:"Unable to load employee summary."}
        }
       },
       elevation=CardDefaults.elevatedCardElevation(defaultElevation=3.dp)
      ){
       Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)){
         Text(e.name,style=MaterialTheme.typography.titleSmall)
         Text(e.identifier,style=MaterialTheme.typography.bodyMedium)
         if(e.department.isNotBlank())Text(e.department,style=MaterialTheme.typography.bodySmall)
        }
        Surface(shape=MaterialTheme.shapes.small,color=if(e.type=="GUEST") GoldPale else TealPale){
         Text(e.type,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=6.dp,vertical=3.dp))
        }
       }
      }
     }
    }
    if(showNewEmpRegister){
     AlertDialog(onDismissRequest={if(!registeringEmp)showNewEmpRegister=false},containerColor=Cream,titleContentColor=Ink,textContentColor=Ink,title={Text("NEW EMP REGISTER",color=Ink)},text={
      Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
       Text("This will be registered as an employee, not a guest.",color=Ink,style=MaterialTheme.typography.bodyMedium)
       OutlinedTextField(value=newEmpCode,onValueChange={newEmpCode=it.filter{ch->ch in '0'..'9'}.take(6)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),supportingText={Text("Enter exactly 6 digits (0–9 only)",color=Ink.copy(alpha=0.72f))},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Ink,unfocusedTextColor=Ink,focusedLabelColor=Teal,unfocusedLabelColor=Ink,focusedBorderColor=Teal,unfocusedBorderColor=Border),label={Text("Employee ID *",color=Ink)},singleLine=true,enabled=!registeringEmp,modifier=Modifier.fillMaxWidth())
       OutlinedTextField(newEmpName,{newEmpName=it},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Ink,unfocusedTextColor=Ink,focusedLabelColor=Teal,unfocusedLabelColor=Ink,focusedBorderColor=Teal,unfocusedBorderColor=Border),label={Text("Name *",color=Ink)},singleLine=true,enabled=!registeringEmp,modifier=Modifier.fillMaxWidth())
       OutlinedTextField(newEmpPhone,{newEmpPhone=it},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Ink,unfocusedTextColor=Ink,focusedLabelColor=Teal,unfocusedLabelColor=Ink,focusedBorderColor=Teal,unfocusedBorderColor=Border),label={Text("Mobile (optional)",color=Ink)},singleLine=true,enabled=!registeringEmp,modifier=Modifier.fillMaxWidth())
       OutlinedTextField(newEmpDepartment,{newEmpDepartment=it},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Ink,unfocusedTextColor=Ink,focusedLabelColor=Teal,unfocusedLabelColor=Ink,focusedBorderColor=Teal,unfocusedBorderColor=Border),label={Text("Department (optional)",color=Ink)},singleLine=true,enabled=!registeringEmp,modifier=Modifier.fillMaxWidth())
      }
     },confirmButton={
      Button(modifier=Modifier.height(46.dp),enabled=!registeringEmp&&newEmpCode.matches(Regex("\\d{6}"))&&newEmpName.trim().isNotBlank()&&repo.isOnline(),onClick={
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
      containerColor=Cream,
      titleContentColor=Ink,
      textContentColor=Ink,
      title={Text("REGISTER AS GUEST",color=Ink)},
      text={
       Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("This will be added as a guest employee only.",color=Ink,style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(newGuestName,{newGuestName=it},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Ink,unfocusedTextColor=Ink,focusedLabelColor=Teal,unfocusedLabelColor=Ink,focusedBorderColor=Teal,unfocusedBorderColor=Border),label={Text("Name *",color=Ink)},singleLine=true,enabled=!addingGuest,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(newGuestPhone,{newGuestPhone=it},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Ink,unfocusedTextColor=Ink,focusedLabelColor=Teal,unfocusedLabelColor=Ink,focusedBorderColor=Teal,unfocusedBorderColor=Border),label={Text("Mobile (optional)",color=Ink)},singleLine=true,enabled=!addingGuest,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(newGuestDepartment,{newGuestDepartment=it},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Ink,unfocusedTextColor=Ink,focusedLabelColor=Teal,unfocusedLabelColor=Ink,focusedBorderColor=Teal,unfocusedBorderColor=Border),label={Text("Department (optional)",color=Ink)},singleLine=true,enabled=!addingGuest,modifier=Modifier.fillMaxWidth())
       }
      },
      confirmButton={
       Button(modifier=Modifier.height(46.dp),enabled=!addingGuest&&newGuestName.trim().isNotBlank()&&repo.isOnline(),onClick={
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
    Text("Review Your Order",style=MaterialTheme.typography.titleLarge.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,letterSpacing=0.1.sp))
    Text("Check the details before saving",style=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,letterSpacing=0.1.sp))

    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Cream),border=androidx.compose.foundation.BorderStroke(1.dp,Border),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
     Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
      Text("TAKING PRODUCTS",style=MaterialTheme.typography.labelLarge)
      Text(e.name,style=MaterialTheme.typography.headlineSmall)
      Text(e.identifier,style=MaterialTheme.typography.titleMedium)
      if(e.department.isNotBlank())Text(e.department,style=MaterialTheme.typography.bodyMedium)
      Text(e.type,style=MaterialTheme.typography.labelMedium)
     }
    }

    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Cream),border=androidx.compose.foundation.BorderStroke(1.dp,Border),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
     Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
      Text("CURRENT TRANSACTION",style=MaterialTheme.typography.labelLarge)
      cart.forEach{line->
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){
         Text(line.product.name,style=MaterialTheme.typography.titleMedium)
         Text(line.product.variant+" × "+line.quantity,style=MaterialTheme.typography.bodyMedium)
        }
        Text("₹"+String.format("%.2f",line.product.price*line.quantity),style=MaterialTheme.typography.titleMedium)
       }
      }
      HorizontalDivider()
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
       Text("TOTAL",style=MaterialTheme.typography.titleMedium)
       Text("₹"+String.format("%.2f",cartTotal()),style=MaterialTheme.typography.headlineSmall)
      }
     }
    }

    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Cream),border=androidx.compose.foundation.BorderStroke(1.dp,Border),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
     Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
      Text("CONSUMPTION TILL TODAY",style=MaterialTheme.typography.labelLarge)
      if(s==null && !repo.isOnline()){
       Text("Previous consumption history is unavailable offline.",style=MaterialTheme.typography.bodyMedium)
      } else {
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Previous transactions");Text((s?.transactionCount?:0).toString())}
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Previous quantity");Text((s?.quantity?:0).toString())}
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Previous bill value");Text("₹"+String.format("%.2f",s?.value?:0.0))}
      }
      if((s?.variants?:emptyList()).isNotEmpty()){
       HorizontalDivider()
       Text("Variant-wise quantity",style=MaterialTheme.typography.labelMedium)
       s?.variants?.forEach{v->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
         Text(v.variantName)
         Text(v.quantity.toString()+" till today")
        }
       }
      }
      HorizontalDivider()
      Text("After this transaction",style=MaterialTheme.typography.labelMedium)
      Text(((s?.quantity?:0)+cart.sumOf{it.quantity}).toString()+" units · ₹"+String.format("%.2f",(s?.value?:0.0)+cartTotal()),style=MaterialTheme.typography.titleMedium)
     }
    }

       }
   else->{
    val r=receipt!!
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
     Card(
      Modifier.fillMaxWidth(),
      colors=CardDefaults.cardColors(containerColor=TealPale),
      border=androidx.compose.foundation.BorderStroke(1.dp,Teal.copy(alpha=0.5f)),
      elevation=CardDefaults.cardElevation(defaultElevation=4.dp)
     ){
      Column(
       Modifier.fillMaxWidth().padding(22.dp),
       horizontalAlignment=Alignment.CenterHorizontally,
       verticalArrangement=Arrangement.spacedBy(6.dp)
      ){
       Text("✓",style=MaterialTheme.typography.displaySmall)
       Text("TRANSACTION COMPLETE",style=MaterialTheme.typography.headlineSmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
       Text("HONEST MILK",style=MaterialTheme.typography.titleMedium)
       if(r.pending){
        Text("PENDING SYNC",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.error)
       } else {
        Text("Saved successfully",style=MaterialTheme.typography.bodyMedium)
       }
      }
     }

     Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Cream),border=androidx.compose.foundation.BorderStroke(1.dp,Border),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
      Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
       Text("Transaction Receipt",style=MaterialTheme.typography.titleMedium.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold))
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        Column{Text(formatReceiptDay(r.transactionAt),style=MaterialTheme.typography.titleMedium);Text("Day",style=MaterialTheme.typography.labelMedium)}
        Column(horizontalAlignment=Alignment.End){Text(formatReceiptTime(r.transactionAt),style=MaterialTheme.typography.titleMedium);Text(formatReceiptDate(r.transactionAt),style=MaterialTheme.typography.labelMedium)}
       }
       HorizontalDivider()
       Text(r.employee.name,style=MaterialTheme.typography.headlineSmall)
       Text(r.employee.identifier,style=MaterialTheme.typography.titleMedium)
       if(r.employee.department.isNotBlank())Text(r.employee.department,style=MaterialTheme.typography.bodyMedium)
       Text(r.employee.type,style=MaterialTheme.typography.labelMedium)
      }
     }

     Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Cream),border=androidx.compose.foundation.BorderStroke(1.dp,Border),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
      Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
       Text("ITEMS",style=MaterialTheme.typography.labelLarge)
       r.lines.forEach{line->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){
         Column(Modifier.weight(1f)){
          Text(line.product.name,style=MaterialTheme.typography.titleMedium)
          Text(line.product.variant+" × "+line.quantity,style=MaterialTheme.typography.bodyMedium)
          Text("₹"+String.format("%.2f",line.product.price)+" each",style=MaterialTheme.typography.labelMedium)
         }
         Text("₹"+String.format("%.2f",line.product.price*line.quantity),style=MaterialTheme.typography.titleMedium)
        }
       }
       HorizontalDivider()
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
        Text("TOTAL",style=MaterialTheme.typography.titleMedium)
        Text("₹"+String.format("%.2f",r.total),style=MaterialTheme.typography.headlineMedium)
       }
      }
     }

     Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Cream),border=androidx.compose.foundation.BorderStroke(1.dp,Border),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
      Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
       Text("CONSUMPTION TILL TODAY",style=MaterialTheme.typography.labelLarge)
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        Text("Total quantity")
        Text((r.previousQuantity+r.lines.sumOf{it.quantity}).toString()+" units",style=MaterialTheme.typography.titleMedium)
       }
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        Text("Total bill value")
        Text("₹"+String.format("%.2f",r.previousValue+r.total),style=MaterialTheme.typography.titleMedium)
       }
      }
     }

         }
   }
  }

  Column(
   Modifier.fillMaxWidth().padding(top=3.dp),
   verticalArrangement=Arrangement.spacedBy(6.dp)
  ){
   when(step){
    0->Button(
     colors=ButtonDefaults.buttonColors(containerColor=Teal,contentColor=Color.White,disabledContainerColor=Color(0xFFD9E1E4),disabledContentColor=Ink.copy(alpha=0.72f)),
     border=androidx.compose.foundation.BorderStroke(2.dp,if(cart.isNotEmpty()) Teal else Border),
     onClick={step=1},
     enabled=cart.isNotEmpty(),
     modifier=Modifier.fillMaxWidth().height(50.dp)
    ){Text("NEXT — SELECT PERSON",style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.ExtraBold,letterSpacing=0.5.sp))}
    1->OutlinedButton(
     colors=ButtonDefaults.outlinedButtonColors(containerColor=WhiteCard,contentColor=Teal),
     border=androidx.compose.foundation.BorderStroke(2.dp,Teal),
     onClick={step=0},
     modifier=Modifier.fillMaxWidth().height(48.dp)
    ){Text("BACK TO PRODUCTS",style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.ExtraBold))}
    2->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     OutlinedButton(
      colors=ButtonDefaults.outlinedButtonColors(containerColor=WhiteCard,contentColor=Teal),
      border=androidx.compose.foundation.BorderStroke(2.dp,Teal),
      onClick={step=0},
      enabled=!saving,
      modifier=Modifier.weight(0.75f).height(44.dp)
     ){Text("BACK",style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.ExtraBold))}
     Button(
      colors=ButtonDefaults.buttonColors(containerColor=Teal,contentColor=Color.White,disabledContainerColor=Color(0xFFD9E1E4),disabledContentColor=Ink.copy(alpha=0.72f)),
      border=androidx.compose.foundation.BorderStroke(2.dp,Teal),
      enabled=!saving,
      onClick={
       saving=true;error=null
       scope.launch{
        runCatching{repo.save(selectedEmployee!!.id,cart.map{TransactionLine(it.product.id,it.quantity,it.product.price)})}
         .onSuccess{saved->
          val pending=saved.any{repo.isPending(it.id)}
          receipt=SavedReceipt(saved.map{it.id},selectedEmployee!!,cart,cartTotal(),summary?.quantity?:0,summary?.value?:0.0,saved.firstOrNull()?.transaction_at?:"",pending)
          pendingCount=repo.pendingCount()
          offlineMode=pending||!repo.isOnline()
          step=3
         }
         .onFailure{error=it.message?:"Unable to save transaction."}
        saving=false
       }
      },
      modifier=Modifier.weight(1.35f).height(44.dp)
     ){Text(if(saving)"SAVING..." else "CONFIRM & FINISH",style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.ExtraBold))}
    }
    else->Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
     Button(
      colors=ButtonDefaults.buttonColors(containerColor=Teal,contentColor=Color.White),
      border=androidx.compose.foundation.BorderStroke(2.dp,Teal),
      onClick={onShare(buildReceiptText(receipt!!))},
      modifier=Modifier.fillMaxWidth().height(48.dp)
     ){Text("SHARE DIGITAL RECEIPT",style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.ExtraBold))}
     Button(
      colors=ButtonDefaults.buttonColors(containerColor=Teal,contentColor=Color.White),
      border=androidx.compose.foundation.BorderStroke(2.dp,Teal),
      onClick={cart=emptyList();selectedProduct=null;selectedEmployee=null;quantity=1;productQuantities=emptyMap();search="";summary=null;receipt=null;error=null;step=0},
      modifier=Modifier.fillMaxWidth().height(48.dp)
     ){Text("NEW TRANSACTION",style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.ExtraBold))}
    }
   }
  }
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
