package ru.genesiscorporation.workspace.beta.modules.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import ru.genesiscorporation.workspace.beta.BuildConfig
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.util.Locale

@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val libraries by produceLibraries(R.raw.aboutlibraries)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedLibraryId by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleLibraries = remember(libraries, searchQuery) {
        libraries?.let { loaded ->
            Libs(
                libraries = loaded.libraries.filter { library ->
                    matchesLibrarySearch(
                        query = searchQuery,
                        searchableValues = buildList {
                            add(library.name)
                            add(library.uniqueId)
                            add(library.artifactVersion)
                            add(library.organization?.name)
                            addAll(library.licenses.map { it.name })
                        },
                    )
                },
                licenses = loaded.licenses,
            )
        }
    }
    val buildDetails = remember {
        AppBuildDetails(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
        )
    }
    val appName = stringResource(R.string.app_name)

    LibrariesContainer(
        libraries = visibleLibraries,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 28.dp),
        showAuthor = true,
        showDescription = false,
        showVersion = true,
        showLicenseBadges = true,
        onLibraryClick = { library ->
            selectedLibraryId = library.uniqueId
        },
        header = {
            item(key = "about-header") {
                AboutHeader(
                    appName = appName,
                    buildDetails = buildDetails,
                    searchQuery = searchQuery,
                    totalLibraryCount = libraries?.libraries?.size,
                    visibleLibraryCount = visibleLibraries?.libraries?.size,
                    onSearchQueryChange = { searchQuery = it },
                    onBack = onBack,
                )
            }
        },
    )

    val selectedLibrary = libraries
        ?.libraries
        ?.firstOrNull { it.uniqueId == selectedLibraryId }
    if (selectedLibrary != null) {
        LibraryDetailsDialog(
            library = selectedLibrary,
            onDismiss = { selectedLibraryId = null },
        )
    }
}

@Composable
private fun AboutHeader(
    appName: String,
    buildDetails: AppBuildDetails,
    searchQuery: String,
    totalLibraryCount: Int?,
    visibleLibraryCount: Int?,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = "Назад",
                    tint = colors.textHeaders,
                )
            }
            Text(
                text = "О приложении",
                color = colors.textHeaders,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = appName,
                color = colors.textHeaders,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatVersionLabel(buildDetails),
                color = colors.textHeaders,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = formatBuildTypeLabel(buildDetails.buildType),
                color = colors.textAdditional50,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }

        HorizontalDivider(color = colors.indicatorGrey.copy(alpha = 0.45f))

        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Лицензии открытого ПО",
                color = colors.textHeaders,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = libraryCountLabel(
                    totalCount = totalLibraryCount,
                    visibleCount = visibleLibraryCount,
                    hasQuery = searchQuery.isNotBlank(),
                ),
                color = colors.textAdditional50,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Поиск библиотеки или лицензии") },
                singleLine = true,
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close_small),
                                contentDescription = "Очистить поиск",
                            )
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (totalLibraryCount != null && visibleLibraryCount == 0) {
                Text(
                    text = "По запросу ничего не найдено",
                    color = colors.textHeaders,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryDetailsDialog(
    library: Library,
    onDismiss: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = library.name,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                library.artifactVersion?.takeIf { it.isNotBlank() }?.let { version ->
                    Text(
                        text = "Версия $version",
                        color = colors.textAdditional50,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                Text(
                    text = library.uniqueId,
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                if (library.licenses.isEmpty()) {
                    Text(
                        text = "Для этого компонента в метаданных сборки лицензия не указана.",
                        color = colors.textHeaders,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                } else {
                    library.licenses.forEach { license ->
                        Text(
                            text = license.name,
                            color = colors.textHeaders,
                            fontSize = 16.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        val content = license.licenseContent
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                        Text(
                            text = content ?: missingLicenseTextMessage(license.url),
                            color = colors.textHeaders,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

internal data class AppBuildDetails(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
)

internal fun formatVersionLabel(details: AppBuildDetails): String {
    val versionName = details.versionName.trim().ifEmpty { "не указана" }
    return "Версия $versionName (${details.versionCode})"
}

internal fun formatBuildTypeLabel(buildType: String): String {
    val normalized = buildType.trim().lowercase(Locale.ROOT)
    val label = when (normalized) {
        "debug" -> "отладочная"
        "release" -> "релизная"
        "" -> "не указана"
        else -> normalized
    }
    return "Тип сборки: $label"
}

internal fun libraryCountLabel(
    totalCount: Int?,
    visibleCount: Int?,
    hasQuery: Boolean,
): String = when {
    totalCount == null -> "Формируем список из текущей сборки…"
    hasQuery -> "Найдено ${visibleCount ?: 0} из $totalCount"
    else -> "Компонентов в текущей сборке: $totalCount"
}

internal fun matchesLibrarySearch(
    query: String,
    searchableValues: Iterable<String?>,
): Boolean {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isEmpty()) return true
    return searchableValues.any { value ->
        value?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true
    }
}

internal fun missingLicenseTextMessage(url: String?): String {
    val source = url
        ?.take(MAX_LICENSE_SOURCE_LENGTH)
        ?.map { character ->
            if (character.isISOControl()) ' ' else character
        }
        ?.joinToString(separator = "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { "\nИсточник: $it" }
        .orEmpty()
    return "Полный текст лицензии не включён в метаданные сборки.$source"
}

private const val MAX_LICENSE_SOURCE_LENGTH = 512
