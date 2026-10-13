package org.androidlabs.filewall.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.androidlabs.filewall.MainActivityViewModel
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SharedViewModel
import org.androidlabs.filewall.rules.edit.RuleEditDialog
import org.androidlabs.filewall.ui.DeleteRuleAlert
import org.androidlabs.filewall.ui.theme.FilewallTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.Locale

class RulesFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private val TAG = "SFRuleFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                FilewallTheme {
                    RulesScreen(
                        sharedViewModel,
                        onDeleteRuleById = ::onDeleteRuleById
                    )
                }
            }
        }
    }

    private fun onDeleteRuleById(id: Long) {
        sharedViewModel.deleteRuleById(id)
    }
}

@Composable
fun RulesScreen(
    viewModel: SharedViewModel,
    onDeleteRuleById: (id: Long) -> Unit
) {
    val view = LocalView.current
    var deleteIdDialog by remember { mutableLongStateOf(-1) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedRule by remember { mutableStateOf<ListRuleItem?>(null) }
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.reorderRules(from.index, to.index)

        ViewCompat.performHapticFeedback(
            view,
            HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK
        )
    }

    val list = viewModel.rules.observeAsState(initial = listOf())

    LaunchedEffect(Unit) {
        viewModel.checkExpiredRules()
    }
    LaunchedEffect(key1 = true) {
        viewModel.refreshRules()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshRules()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (list.value.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(26.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.no_rules),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
        ) {
            itemsIndexed(list.value, key = { _, item -> item.rule.id!! }) { index, rule ->
                val id = rule.rule.id!!
                ReorderableItem(reorderableLazyListState, key = id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                    Surface(shadowElevation = elevation) {
                        RuleRow(
                            rule = rule,
                            timeFormatter = formatter,
                            isSelected = id == deleteIdDialog,
                            onSelect = {
                                deleteIdDialog = id
                            },
                            onClick = {
                                selectedRule = it
                                showEditDialog = true
                            },
                            reorderModifier = Modifier.draggableHandle(
                                onDragStarted = {
                                    ViewCompat.performHapticFeedback(
                                        view,
                                        HapticFeedbackConstantsCompat.GESTURE_START
                                    )
                                },
                                onDragStopped = {
                                    ViewCompat.performHapticFeedback(
                                        view,
                                        HapticFeedbackConstantsCompat.GESTURE_END
                                    )
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    if (deleteIdDialog > 0) {
        DeleteRuleAlert(
            onCancel = { deleteIdDialog = -1 },
            onDelete = {
                onDeleteRuleById(deleteIdDialog)
                deleteIdDialog = -1
            }
        )
    }

    if (showEditDialog) {
        RuleEditDialog(
            sharedViewModel = viewModel,
            onSave = { ruleItem->
               viewModel.updateRule(ruleItem.rule)
               selectedRule = null
               showEditDialog = false
            },
            onDismiss = {
                selectedRule = null
                showEditDialog = false
            },
            rule = selectedRule!!,
        )
    }
}
