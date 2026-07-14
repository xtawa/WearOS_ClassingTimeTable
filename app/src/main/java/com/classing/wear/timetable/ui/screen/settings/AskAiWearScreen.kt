package com.classing.wear.timetable.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.classing.wear.timetable.ui.component.screenPadding

/** Separate Wear page reserved for the phone-proxied Ask AI experience. */
@Composable
fun AskAiWearScreen(onBack: () -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Ask AI", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text("课表助手通过已配对手机处理提问与会员校验。", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall) } }
        item { Card { Text("请先在手机端登录并开通会员。手表不会保存账户凭据。", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall) } }
        item { Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(999.dp)) { Text("返回") } }
    }
}
