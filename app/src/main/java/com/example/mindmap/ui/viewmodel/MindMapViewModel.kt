package com.example.mindmap.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mindmap.data.NodeEntity
import com.example.mindmap.data.MindMapNodeSizing
import com.example.mindmap.data.NodeRepository
import com.example.mindmap.data.MindMapReminderScheduler
import com.example.mindmap.data.MindMapReminderSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MindMapViewModel(private val repository: NodeRepository) : ViewModel() {

    val allNodes: StateFlow<List<NodeEntity>> = repository.getAllNodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRootDateNode(sectionId: Long, label: String, x: Float? = null, y: Float? = null) {
        viewModelScope.launch {
            val roots = allNodes.value.filter { it.parentId == null && it.sectionId == sectionId }
            val nextIndex = (roots.maxOfOrNull { it.orderIndex } ?: -1) + 1
            val size = MindMapNodeSizing.forLabel(label, isRoot = true)
            repository.insert(
                NodeEntity(
                    sectionId = sectionId,
                    parentId = null, label = label, orderIndex = nextIndex,
                    x = x ?: 200f,
                    y = y ?: 300f + nextIndex * 260f,
                    widthScale = size.width,
                    heightScale = size.height
                )
            )
        }
    }

    fun addChildNode(parent: NodeEntity, label: String) {
        viewModelScope.launch {
            val siblingCount = allNodes.value.count { it.parentId == parent.id }
            val size = MindMapNodeSizing.forLabel(label)
            repository.insert(
                NodeEntity(
                    sectionId = parent.sectionId,
                    parentId = parent.id, label = label,
                    orderIndex = siblingCount,
                    x = parent.x + 360f, y = parent.y + siblingCount * 150f,
                    widthScale = size.width,
                    heightScale = size.height
                )
            )
            if (!parent.isExpanded) repository.update(parent.copy(isExpanded = true))
        }
    }

    fun addMediaChildNode(parent: NodeEntity, label: String, onCreated: (NodeEntity) -> Unit) {
        viewModelScope.launch {
            val siblingCount = allNodes.value.count { it.parentId == parent.id }
            val child = NodeEntity(
                sectionId = parent.sectionId,
                parentId = parent.id,
                label = label,
                orderIndex = siblingCount,
                x = parent.x + 360f,
                y = parent.y + siblingCount * 150f
            )
            val childId = repository.insert(child)
            if (!parent.isExpanded) repository.update(parent.copy(isExpanded = true))
            onCreated(child.copy(id = childId))
        }
    }

    // ---- AI (Gemini) generated node creation — mirrors addRootDateNode /
    // addChildNode positioning, but returns the freshly inserted entity (with
    // its real DB id) via callback so a generated tree can be walked and
    // inserted level-by-level as Gemini's structured response is processed.
    // Reuses the same repository/table as manual creation — no parallel model. ----
    fun addAiRootNode(sectionId: Long, label: String, x: Float, y: Float, onCreated: (NodeEntity) -> Unit) {
        viewModelScope.launch {
            val roots = allNodes.value.filter { it.parentId == null && it.sectionId == sectionId }
            val nextIndex = (roots.maxOfOrNull { it.orderIndex } ?: -1) + 1
            val size = MindMapNodeSizing.forLabel(label, isRoot = true)
            val node = NodeEntity(
                sectionId = sectionId,
                parentId = null,
                label = label,
                orderIndex = nextIndex,
                x = x,
                y = y,
                isExpanded = true,
                widthScale = size.width,
                heightScale = size.height
            )
            val id = repository.insert(node)
            onCreated(node.copy(id = id))
        }
    }

    fun addAiChildNode(parent: NodeEntity, label: String, x: Float, y: Float, onCreated: (NodeEntity) -> Unit) {
        viewModelScope.launch {
            val siblingCount = allNodes.value.count { it.parentId == parent.id }
            val size = MindMapNodeSizing.forLabel(label)
            val node = NodeEntity(
                sectionId = parent.sectionId,
                parentId = parent.id,
                label = label,
                orderIndex = siblingCount,
                x = x,
                y = y,
                isExpanded = true,
                widthScale = size.width,
                heightScale = size.height
            )
            val id = repository.insert(node)
            if (!parent.isExpanded) repository.update(parent.copy(isExpanded = true))
            onCreated(node.copy(id = id))
        }
    }

    fun pasteSubtree(
        targetParent: NodeEntity,
        sourceRootId: Long,
        sourceNodes: List<NodeEntity>,
        includeText: Boolean,
        includeSourceBox: Boolean?
    ) {
        viewModelScope.launch {
            val sourceRoot = sourceNodes.firstOrNull { it.id == sourceRootId } ?: return@launch
            val childrenByParentId = sourceNodes.groupBy { it.parentId }
            val existingChildCount = allNodes.value.count { it.parentId == targetParent.id }
            val shouldIncludeSourceBox = includeSourceBox ?: (sourceRoot.parentId != null)

            suspend fun insertCopy(source: NodeEntity, parentId: Long, topLevelOrderIndex: Int? = null) {
                val relativeX = source.x - sourceRoot.x
                val relativeY = source.y - sourceRoot.y
                val sourceChildren = childrenByParentId[source.id].orEmpty()
                val copy = source.copy(
                    id = 0,
                    sectionId = targetParent.sectionId,
                    parentId = parentId,
                    label = if (includeText) source.label else "",
                    orderIndex = topLevelOrderIndex ?: source.orderIndex,
                    x = targetParent.x + if (shouldIncludeSourceBox) 300f + relativeX else relativeX,
                    y = targetParent.y + existingChildCount * 130f + relativeY,
                    isExpanded = sourceChildren.isNotEmpty()
                )
                val insertedId = repository.insert(copy)
                sourceChildren.sortedBy { it.orderIndex }.forEach { child ->
                    insertCopy(child, insertedId)
                }
            }

            if (!shouldIncludeSourceBox) {
                childrenByParentId[sourceRoot.id].orEmpty()
                    .sortedBy { it.orderIndex }
                    .forEachIndexed { index, child ->
                        insertCopy(child, targetParent.id, existingChildCount + index)
                    }
            } else {
                insertCopy(sourceRoot, targetParent.id, existingChildCount)
            }
            repository.update(targetParent.copy(isExpanded = true))
        }
    }

    fun updateLabel(node: NodeEntity, newLabel: String) {
        viewModelScope.launch {
            val auto = MindMapNodeSizing.forLabel(newLabel, node.textSizeSp, node.parentId == null)
            repository.update(node.copy(label = newLabel, widthScale = auto.width, heightScale = auto.height))
        }
    }

    fun toggleDone(context: Context, node: NodeEntity) {
        viewModelScope.launch {
            val updatedNode = node.copy(isDone = !node.isDone)
            repository.update(updatedNode)
            reconcileReminderOwners(context, allNodes.value.map { if (it.id == node.id) updatedNode else it })
        }
    }

    fun deleteNode(context: Context, node: NodeEntity) {
        viewModelScope.launch {
            MindMapReminderScheduler.cancel(context, node.id)
            repository.delete(node)
            reconcileReminderOwners(context, repository.getNodesNow())
        }
    }

    fun toggleReminder(context: Context, owner: NodeEntity) {
        viewModelScope.launch {
            if (owner.reminderEnabled) {
                MindMapReminderScheduler.cancel(context, owner.id)
                repository.update(owner.copy(
                    reminderEnabled = false,
                    reminderQueueActive = false,
                    reminderActiveTaskId = null,
                    reminderEscalationMinutes = 0,
                    reminderDeliveryCount = 0,
                    reminderNextTriggerMillis = 0L
                ))
                return@launch
            }
            val directIncomplete = allNodes.value.asSequence()
                .filter { it.parentId == owner.id && !it.isDone }
                .sortedWith(compareBy<NodeEntity> { it.orderIndex }.thenBy { it.id })
                .toList()
            val firstTask = directIncomplete.firstOrNull()
            if (firstTask == null) {
                // No queue is persisted for a leaf or an already completed branch.
                MindMapReminderScheduler.cancel(context, owner.id)
                repository.update(owner.copy(reminderEnabled = false, reminderQueueActive = false))
                return@launch
            }
            val enabled = owner.copy(
                reminderEnabled = true,
                reminderQueueActive = true,
                reminderActiveTaskId = firstTask.id,
                reminderEscalationMinutes = 0,
                reminderDeliveryCount = 0,
                reminderIntervalMinutes = MindMapReminderSettings.intervalMinutes(context),
                reminderNextTriggerMillis = System.currentTimeMillis() + 10 * 60_000L
            )
            repository.update(enabled)
            MindMapReminderScheduler.schedule(context, enabled)
        }
    }

    /** Existing alarms are intentionally left in place; the new interval takes
     * effect when that cycle schedules its next occurrence. */
    fun updateReminderInterval(context: Context, minutes: Int) {
        viewModelScope.launch {
            val valid = minutes.coerceIn(1, 59 * 60 + 59)
            MindMapReminderSettings.setIntervalMinutes(context, valid)
            allNodes.value.filter { it.reminderEnabled && it.reminderQueueActive }
                .forEach { repository.update(it.copy(reminderIntervalMinutes = valid)) }
        }
    }

    /** Rebuilds only direct-child queues after completion/deletion, never grandchildren. */
    private suspend fun reconcileReminderOwners(context: Context, snapshot: List<NodeEntity>) {
        snapshot.filter { it.reminderEnabled }.forEach { owner ->
            val incomplete = snapshot.asSequence()
                .filter { it.parentId == owner.id && !it.isDone }
                .sortedWith(compareBy<NodeEntity> { it.orderIndex }.thenBy { it.id })
                .toList()
            val current = incomplete.firstOrNull { it.id == owner.reminderActiveTaskId }
            val next = current ?: incomplete.firstOrNull()
            val updated = when {
                next == null -> owner.copy(
                    reminderEnabled = false,
                    reminderQueueActive = false,
                    reminderActiveTaskId = null,
                    reminderNextTriggerMillis = 0L
                )
                current == null -> owner.copy(
                    reminderQueueActive = true,
                    reminderActiveTaskId = next.id,
                    reminderEscalationMinutes = 0,
                    reminderDeliveryCount = 0,
                    reminderNextTriggerMillis = System.currentTimeMillis() + 10 * 60_000L
                )
                else -> owner
            }
            if (updated != owner) repository.update(updated)
            if (!updated.reminderEnabled || !updated.reminderQueueActive) {
                MindMapReminderScheduler.cancel(context, owner.id)
            } else if (updated != owner) {
                MindMapReminderScheduler.schedule(context, updated)
            }
        }
    }

    fun updatePosition(node: NodeEntity, x: Float, y: Float) {
        viewModelScope.launch { repository.update(node.copy(x = x, y = y)) }
    }

    fun updatePositions(nodes: List<NodeEntity>) {
        viewModelScope.launch { nodes.forEach { repository.update(it) } }
    }
    fun updateColor(node: NodeEntity, colorArgb: Long?) {
        viewModelScope.launch { repository.update(node.copy(colorArgb = colorArgb)) }
    }
    fun updateBoxStyle(
        node: NodeEntity,
        colorArgb: Long?,
        textColorArgb: Long?,
        widthScale: Float,
        heightScale: Float
    ) {
        viewModelScope.launch {
            repository.update(
                node.copy(
                    colorArgb = colorArgb,
                    textColorArgb = textColorArgb,
                    widthScale = widthScale.coerceIn(MindMapNodeSizing.MIN_SCALE, MindMapNodeSizing.MAX_SCALE),
                    heightScale = heightScale.coerceIn(MindMapNodeSizing.MIN_SCALE, MindMapNodeSizing.MAX_SCALE)
                )
            )
        }
    }
    fun updateTextStyle(
        node: NodeEntity,
        label: String,
        textSizeSp: Float,
        textWeight: Int,
        textColorArgb: Long?
    ) {
        viewModelScope.launch {
            val safeTextSize = textSizeSp.coerceIn(10f, 34f)
            val auto = MindMapNodeSizing.forLabel(label, safeTextSize, node.parentId == null)
            repository.update(
                node.copy(
                    label = label,
                    widthScale = auto.width,
                    heightScale = auto.height,
                    textSizeSp = safeTextSize,
                    textWeight = textWeight.coerceIn(100, 1200),
                    textColorArgb = textColorArgb
                )
            )
        }
    }

    fun applyAiUpdate(
        node: NodeEntity,
        label: String? = null,
        colorArgb: Long? = node.colorArgb,
        textColorArgb: Long? = node.textColorArgb,
        widthScale: Float? = null,
        heightScale: Float? = null,
        textSizeSp: Float? = null,
        textWeight: Int? = null,
        x: Float? = null,
        y: Float? = null
    ) {
        viewModelScope.launch {
            val nextLabel = label?.takeIf { it.isNotBlank() } ?: node.label
            val nextTextSize = (textSizeSp ?: node.textSizeSp).coerceIn(10f, 40f)
            val auto = MindMapNodeSizing.forLabel(nextLabel, nextTextSize, node.parentId == null)
            repository.update(node.copy(
                label = nextLabel,
                colorArgb = colorArgb,
                textColorArgb = textColorArgb,
                widthScale = (widthScale ?: auto.width).coerceIn(MindMapNodeSizing.MIN_SCALE, MindMapNodeSizing.MAX_SCALE),
                heightScale = (heightScale ?: auto.height).coerceIn(MindMapNodeSizing.MIN_SCALE, MindMapNodeSizing.MAX_SCALE),
                textSizeSp = nextTextSize,
                textWeight = (textWeight ?: node.textWeight).coerceIn(100, 1200),
                x = x ?: node.x,
                y = y ?: node.y
            ))
        }
    }
    fun toggleExpand(node: NodeEntity, allowMultipleRoots: Boolean) {
        viewModelScope.launch {
            if (node.parentId == null && !allowMultipleRoots) {
                val newState = !node.isExpanded
                allNodes.value.filter { it.parentId == null && it.sectionId == node.sectionId }.forEach { root ->
                    repository.update(root.copy(isExpanded = if (root.id == node.id) newState else false))
                }
            } else {
                repository.update(node.copy(isExpanded = !node.isExpanded))
            }
        }
    }

    fun collapseAllRoots(sectionId: Long) {
        viewModelScope.launch {
            allNodes.value.filter {
                it.parentId == null && it.isExpanded && it.sectionId == sectionId
            }.forEach {
                repository.update(it.copy(isExpanded = false))
            }
        }
    }

    fun expandAncestors(nodeId: Long) {
        viewModelScope.launch {
            val nodesById = allNodes.value.associateBy { it.id }
            var parentId = nodesById[nodeId]?.parentId
            while (parentId != null) {
                val parent = nodesById[parentId] ?: break
                if (!parent.isExpanded) repository.update(parent.copy(isExpanded = true))
                parentId = parent.parentId
            }
        }
    }
    fun updateConnectorStyle(node: NodeEntity, colorArgb: Long, strokeWidth: Float) {
        viewModelScope.launch {
            repository.update(
                node.copy(
                    connectorColorArgb = colorArgb,
                    connectorStrokeWidth = strokeWidth.coerceIn(1f, 16f),
                    isConnectorHidden = false
                )
            )
        }
    }

    fun hideConnector(node: NodeEntity) {
        viewModelScope.launch { repository.update(node.copy(isConnectorHidden = true)) }
    }

    fun resetConnectorStyle(node: NodeEntity) {
        viewModelScope.launch {
            repository.update(
                node.copy(
                    connectorColorArgb = null,
                    connectorStrokeWidth = 3f,
                    isConnectorHidden = false
                )
            )
        }
    }
}

class MindMapViewModelFactory(private val repository: NodeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MindMapViewModel(repository) as T
    }
}
