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

data class Product(val name: String, val variant: String, val price: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { CounterApp() } }
}

@Composable
fun CounterApp() {
    var selected by remember { mutableStateOf<Product?>(null) }
    var quantity by remember { mutableIntStateOf(1) }
    var step by remember { mutableIntStateOf(0) }
    val products = listOf(Product("Honest Milk", "500 ML", 38), Product("Honest Milk", "1 L", 75))
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if (step == 0) "TAKE PRODUCT" else "WHO ARE YOU", style = MaterialTheme.typography.headlineMedium)
            if (step == 0) {
                products.forEach { product ->
                    OutlinedButton(onClick = { selected = product }, modifier = Modifier.fillMaxWidth().height(90.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(product.name + " — " + product.variant, style = MaterialTheme.typography.titleLarge)
                            Text("₹" + product.price, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                selected?.let { product ->
                    Text("Selected: " + product.name + " " + product.variant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { if (quantity > 1) quantity-- }) { Text("−") }
                        Text("  " + quantity + "  ", style = MaterialTheme.typography.titleLarge)
                        Button(onClick = { quantity++ }) { Text("+") }
                    }
                    Text("Total ₹" + (product.price * quantity), style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("NEXT") }
                }
            } else {
                Text("Employee identification will be connected to Supabase next.")
                Button(onClick = { step = 0 }, modifier = Modifier.fillMaxWidth()) { Text("BACK") }
            }
        }
    }
}
