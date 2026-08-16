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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.classing.wear.timetable.R
import com.classing.wear.timetable.ui.component.screenPadding
import com.classing.wear.timetable.ui.component.ClassingIsland
import com.classing.wear.timetable.ui.component.ClassingWearBackground
import com.classing.wear.timetable.ui.component.WearPageHeader
import com.classing.wear.timetable.ui.theme.ClassingWearRadii
import com.classing.wear.timetable.ui.theme.ClassingWearSpacing

@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val versionName = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    ClassingWearBackground {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(ClassingWearSpacing.md),
    ) {
        item {
            WearPageHeader(
                title = stringResource(R.string.settings_about),
                eyebrow = stringResource(R.string.home_brand_wordmark),
            )
        }
        item {
            ClassingIsland(emphasized = true) {
                Text(
                text = stringResource(R.string.settings_about_summary, context.getString(R.string.app_name), versionName),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(ClassingWearRadii.pill)) {
                Text(text = context.getString(R.string.detail_back))
            }
        }
    }
    }
}
