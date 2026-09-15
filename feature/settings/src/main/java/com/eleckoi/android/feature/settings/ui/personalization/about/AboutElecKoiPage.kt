package com.eleckoi.android.feature.settings.ui.personalization.about

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.settings.ui.personalization.components.CompactSettingsScaffold
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsDestinationRow
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsDivider
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsRowTextStart
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsSection
import com.eleckoi.android.foundation.design.AppearanceTheme

@Composable
fun AboutElecKoiPage(
    appearance: AppearanceTheme,
    appIconResId: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val versionName = remember(context) { installedVersionName(context) }

    CompactSettingsScaffold(
        title = "关于电子爱",
        appearance = appearance,
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(112.dp),
                shape = RoundedCornerShape(28.dp),
                color = appearance.mobileSurface,
                shadowElevation = 6.dp,
            ) {
                Image(
                    painter = painterResource(appIconResId),
                    contentDescription = "电子爱 App 图标",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp)),
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "电子爱",
                color = appearance.mobileText,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "ElecKoi",
                color = appearance.mobileMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(
                text = "版本 $versionName",
                color = appearance.mobileSoft,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SettingsSection(label = "项目", appearance = appearance) {
            SettingsDestinationRow(
                iconPath = GitHubMarkPath,
                iconViewportSize = 24f,
                title = "GitHub",
                subtitle = "github.com/eleckoi/ElecKoi-app",
                appearance = appearance,
                onClick = {
                    openExternalPage(
                        context = context,
                        url = ElecKoiGithubUrl,
                        failureMessage = "没有可打开 GitHub 的应用",
                    )
                },
            )
            SettingsDivider(appearance, startIndent = SettingsRowTextStart)
            SettingsDestinationRow(
                icon = Icons.Rounded.Description,
                title = "开源许可证",
                subtitle = "GNU AGPL v3 或更高版本",
                appearance = appearance,
                onClick = {
                    openExternalPage(
                        context = context,
                        url = ElecKoiLicenseUrl,
                        failureMessage = "没有可打开许可证页面的应用",
                    )
                },
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun installedVersionName(context: android.content.Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "未知" }

private fun openExternalPage(
    context: android.content.Context,
    url: String,
    failureMessage: String,
) {
    val opened = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!opened) {
        Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

private const val ElecKoiGithubUrl = "https://github.com/eleckoi/ElecKoi-app"
private const val ElecKoiLicenseUrl = "https://github.com/eleckoi/ElecKoi-app/blob/main/LICENSE"

// GitHub's official Primer Octicons `mark-github-24` path, used without shape changes.
// Source: https://github.com/primer/octicons/blob/main/icons/mark-github-24.svg
private const val GitHubMarkPath =
    "M10.226 17.284c-2.965-.36-5.054-2.493-5.054-5.256 0-1.123.404-2.336 " +
        "1.078-3.144-.292-.741-.247-2.314.09-2.965.898-.112 2.111.36 2.83 1.01.853-.269 " +
        "1.752-.404 2.853-.404 1.1 0 1.999.135 2.807.382.696-.629 1.932-1.1 2.83-.988.315.606 " +
        ".36 2.179.067 2.942.72.854 1.101 2 1.101 3.167 0 2.763-2.089 4.852-5.098 5.234.763.494 " +
        "1.28 1.572 1.28 2.807v2.336c0 .674.561 1.056 1.235.786 4.066-1.55 7.255-5.615 " +
        "7.255-10.646C23.5 6.188 18.334 1 11.978 1 5.62 1 .5 6.188.5 12.545c0 4.986 3.167 " +
        "9.12 7.435 10.669.606.225 1.19-.18 1.19-.786V20.63a2.9 2.9 0 0 1-1.078.224c-1.483 " +
        "0-2.359-.808-2.987-2.313-.247-.607-.517-.966-1.034-1.033-.27-.023-.359-.135-.359-.27 " +
        "0-.27.45-.471.898-.471.652 0 1.213.404 1.797 1.235.45.651.921.943 1.483.943.561 " +
        "0 .92-.202 1.437-.719.382-.381.674-.718.944-.943"
