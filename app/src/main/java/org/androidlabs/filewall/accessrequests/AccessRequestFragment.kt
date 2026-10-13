package org.androidlabs.filewall.accessrequests

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.androidlabs.filewall.MainActivityViewModel
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.SharedViewModel
import org.androidlabs.filewall.confirm.DocumentConfirmationActivity
import org.androidlabs.filewall.ui.DeleteRequestAlert
import org.androidlabs.filewall.ui.theme.FilewallTheme
import org.androidlabs.filewall.utils.Utils
import kotlin.getValue

class AccessRequestFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                FilewallTheme {
                    RequestScreen(
                        sharedViewModel,
                        onDeleteRequests = ::onDeleteRequests,
                        onItemClick = { item ->
                            Utils.startActivity(
                                context = context,
                                activityClass = DocumentConfirmationActivity::class.java,
                            ) {
                                putExtra("request", item.requestToDecide())
                                if (item.isBulk) {
                                    // The decision answers every request on the card.
                                    putExtra("group_ids", item.memberIds)
                                    putExtra("group_has_folders", item.includesFolders)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun onDeleteRequests(ids: List<Long>) {
        sharedViewModel.deleteRequestsByIds(ids)
    }
}

@Composable
fun RequestScreen(
    viewModel: SharedViewModel,
    onDeleteRequests: (ids: List<Long>) -> Unit,
    onItemClick: (ListAccessRequestItem) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ListAccessRequestItem?>(null) }
    val lazyListState = rememberLazyListState()
    val list = viewModel.requests.observeAsState(initial = listOf())

    LaunchedEffect(key1 = true) {
        viewModel.refreshRequests()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshRequests()
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
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.no_requests),
            )
        }
    } else {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
        ) {
            itemsIndexed(list.value, key = { _, item -> item.request.id }) { _, request ->
                AccessRequestRow(
                    request = request,
                    isSelected = pendingDelete?.request?.id == request.request.id,
                    onSelect = {
                        pendingDelete = request
                    },
                    onClick = {
                        onItemClick(request)
                    }
                )
            }
        }
    }

    pendingDelete?.let { item ->
        DeleteRequestAlert(
            onCancel = { pendingDelete = null },
            onDelete = {
                onDeleteRequests(item.members.map { member -> member.id })
                pendingDelete = null
            }
        )
    }
}
