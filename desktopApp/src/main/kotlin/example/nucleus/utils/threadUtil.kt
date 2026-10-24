package example.nucleus.utils

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

fun <T> runOnUiThread(block: () -> T): T = runBlocking {
    withContext(Dispatchers.Main) { block() }
}